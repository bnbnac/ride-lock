package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.AbstractRedisIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "matching.lock-strategy=redis")
class RedissonLockStrategyTest extends AbstractRedisIntegrationTest {

	private static final long DRIVER_ID = 1L;

	@Autowired
	private RedissonLockStrategy redissonLockStrategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void cleanUp() {
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void tryAssignSucceedsOnceThenFailsOnSecondCall() {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));

		assertThat(redissonLockStrategy.tryAssign(DRIVER_ID)).isTrue();
		assertThat(redissonLockStrategy.tryAssign(DRIVER_ID)).isFalse();
	}

	// MatchingService처럼 tryAssign()을 바깥 트랜잭션 안에서 호출하다가 안쪽에서 예외가 나는
	// 상황을 재현한다 - driver_status row를 저장하지 않아 findById().orElseThrow()가 던지게
	// 만든다. 이 예외가 나도 unlock()이 즉시 실행되지 않고, 바깥 트랜잭션이 실제로 끝난 뒤에만
	// 실행돼야 한다 - 그렇지 않으면 트랜잭션은 살아있는데 락만 먼저 풀리는 창이 생긴다.
	@Test
	void unlockIsDeferredUntilOuterTransactionCompletesEvenWhenInnerCallThrows() {
		TransactionTemplate outerTransactionTemplate = new TransactionTemplate(transactionManager);
		AtomicBoolean lockedDuringOuterTransaction = new AtomicBoolean();

		assertThatThrownBy(() -> outerTransactionTemplate.execute(status -> {
			assertThatThrownBy(() -> redissonLockStrategy.tryAssign(DRIVER_ID))
					.isInstanceOf(NoSuchElementException.class);
			lockedDuringOuterTransaction.set(redissonClient.getLock("driver:lock:" + DRIVER_ID).isLocked());
			return null;
		})).isInstanceOf(UnexpectedRollbackException.class);

		assertThat(lockedDuringOuterTransaction.get())
				.as("바깥 트랜잭션이 아직 안 끝난 시점엔 락을 계속 들고 있어야 한다")
				.isTrue();
		assertThat(redissonClient.getLock("driver:lock:" + DRIVER_ID).isLocked())
				.as("바깥 트랜잭션이 롤백된 뒤엔 락이 풀려야 한다")
				.isFalse();
	}

}
