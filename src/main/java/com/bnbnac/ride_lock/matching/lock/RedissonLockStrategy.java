package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

// Redis 락 획득/해제는 Spring 트랜잭션 밖에서 일어난다. 락을 잡은 뒤 DB 작업은
// TransactionTemplate으로 인라인 처리한다 - 이 클래스의 @Transactional 메서드를 자기 자신이
// 호출하면 Spring AOP self-invocation 문제로 트랜잭션이 안 걸리기 때문에, 별도 협력 빈을
// 만드는 대신 이 방법을 쓴다.
// unlock()은 finally에서 바로 하지 않는다 - MatchingService가 tryAssign()+createTrip()을
// 하나의 트랜잭션으로 묶어서 호출하므로(DriverLockStrategy 인터페이스 주석 참고), 이 메서드의
// transactionTemplate.execute()는 그 바깥 트랜잭션에 REQUIRED로 합류할 뿐 물리적으로 커밋되지
// 않는다. finally에서 바로 unlock()하면 DB 변경이 실제로 커밋되기도 전에 Redis 락이 풀려서,
// 그 틈에 다른 스레드가 같은 driver를 findById()로 읽어 아직 안 보이는 옛 상태(IDLE)를 보고
// 중복 배정할 수 있다. 그래서 실제 트랜잭션이 끝난 뒤(afterCompletion)에 풀리도록 등록한다.
// 이 "미룰지" 판단은 안쪽 transactionTemplate.execute() 호출 "전에" 끝내둔다 - REQUIRED는
// 이미 열려있는 바깥 트랜잭션에 합류만 할 뿐이라 isSynchronizationActive()는 호출 전/후로
// 값이 같다. 판단을 호출 뒤로 미루면 그 호출이 예외를 던졌을 때 판단 자체가 건너뛰어져
// unlockDeferred가 항상 false로 남고, 바깥 트랜잭션이 살아있는데도 즉시 unlock되는
// 비대칭이 생긴다.
@Component
@ConditionalOnProperty(name = "matching.lock-strategy", havingValue = "redis")
public class RedissonLockStrategy implements DriverLockStrategy {

	private static final Logger log = LoggerFactory.getLogger(RedissonLockStrategy.class);

	private static final long WAIT_SECONDS = 0;

	private final RedissonClient redissonClient;
	private final TransactionTemplate transactionTemplate;
	private final DriverStatusRepository driverStatusRepository;

	public RedissonLockStrategy(RedissonClient redissonClient, PlatformTransactionManager transactionManager,
			DriverStatusRepository driverStatusRepository) {
		this.redissonClient = redissonClient;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.driverStatusRepository = driverStatusRepository;
	}

	@Override
	public boolean tryAssign(Long driverId) {
		RLock lock = redissonClient.getLock("driver:lock:" + driverId);
		boolean acquired;
		try {
			// leaseTime을 명시하지 않는다 - 명시하면 Redisson watchdog 자동 연장이 꺼져서 락이
			// 고정 시간 뒤 무조건 만료된다. tryAssign()+createTrip() 트랜잭션이 커넥션 풀 경합,
			// GC 등으로 그 고정 시간을 넘기면 커밋 전에 락이 만료돼 이 클래스가 막으려는 레이스가
			// 그대로 재발한다. watchdog(기본 30초, 보유 중 자동 갱신)에 맡기고 크래시 시에만
			// 만료되도록 하며, 정상 경로에서는 아래에서 항상 명시적으로 unlock()한다.
			acquired = lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
		if (!acquired) {
			return false;
		}
		boolean unlockDeferred = false;
		try {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCompletion(int status) {
						safeUnlock(lock, driverId);
					}
				});
				unlockDeferred = true;
			}
			return Boolean.TRUE.equals(transactionTemplate.execute(txStatus -> {
				DriverStatus status = driverStatusRepository.findById(driverId).orElseThrow();
				if (!status.assign(OffsetDateTime.now())) {
					return false;
				}
				driverStatusRepository.save(status);
				return true;
			}));
		} finally {
			if (!unlockDeferred) {
				safeUnlock(lock, driverId);
			}
		}
	}

	private void safeUnlock(RLock lock, Long driverId) {
		try {
			lock.unlock();
		} catch (RuntimeException e) {
			log.warn("driver {} 배정 락 해제 실패 - 이미 만료됐을 수 있음", driverId, e);
		}
	}

}
