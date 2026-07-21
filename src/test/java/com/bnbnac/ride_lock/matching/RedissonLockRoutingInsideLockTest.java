package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractRedisIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.matching.lock.RedissonLockStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

// Redisson의 tryLock(0, SECONDS)은 대기 없이 즉시 실패하는 방식이라(RedissonLockStrategy 참고),
// 두 번째 호출자가 "기다리다 느려지는" pessimistic과 달리 실패 자체는 항상 즉시 일어난다. 그래서
// 두 호출자 간 소요시간 차이로는 배치 효과를 볼 수 없고, 대신 "이 락이 실제로 얼마나 오래 잠겨
// 있었는가(다른 요청이 거부당하는 창)"를 별도 스레드로 직접 폴링해 측정한다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=redis",
		"matching.routing-delay-ms=300",
		"matching.routing-delay-inside-lock=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RedissonLockRoutingInsideLockTest extends AbstractRedisIntegrationTest {

	private static final long DRIVER_ID = 890L;
	private static final long DELAY_MS = 300;

	@Autowired
	private RedissonLockStrategy strategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Autowired
	private RedissonClient redissonClient;

	@AfterEach
	void cleanUp() {
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void lockStaysHeldForRoughlyTheConfiguredDelay() throws Exception {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));
		RLock lock = redissonClient.getLock("driver:lock:" + DRIVER_ID);

		long heldMillis = measureLockHeldDuration(lock, () -> strategy.tryAssign(DRIVER_ID));

		assertThat(heldMillis)
				.as("inside-lock이면 routing 지연 동안 락을 쥐고 있어야 하므로 보유시간이 지연시간에 근접해야 한다")
				.isGreaterThanOrEqualTo((long) (DELAY_MS * 0.8));
	}

	private static long measureLockHeldDuration(RLock lock, Runnable call) throws InterruptedException {
		AtomicLong heldNanos = new AtomicLong();
		AtomicBoolean watching = new AtomicBoolean(true);
		Thread watcher = new Thread(() -> {
			long lockedSince = -1;
			while (watching.get()) {
				if (lock.isLocked()) {
					if (lockedSince < 0) {
						lockedSince = System.nanoTime();
					}
				} else if (lockedSince >= 0) {
					heldNanos.addAndGet(System.nanoTime() - lockedSince);
					lockedSince = -1;
				}
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			if (lockedSince >= 0) {
				heldNanos.addAndGet(System.nanoTime() - lockedSince);
			}
		});
		watcher.start();

		call.run();

		watching.set(false);
		watcher.join(TimeUnit.SECONDS.toMillis(5));
		return TimeUnit.NANOSECONDS.toMillis(heldNanos.get());
	}

}
