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

// before-lock(기본값)이면 routing 지연이 락 획득 전에 끝나므로, 락은 그 뒤의 빠른 DB 갱신
// 시간만큼만 잠깐 잠긴다 - RedissonLockRoutingInsideLockTest(반대 배치)와 대조된다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=redis",
		"matching.routing-delay-ms=300",
		"matching.routing-delay-inside-lock=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RedissonLockRoutingBeforeLockTest extends AbstractRedisIntegrationTest {

	private static final long DRIVER_ID = 891L;
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
	void lockStaysHeldForFarLessThanTheConfiguredDelay() throws Exception {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));
		RLock lock = redissonClient.getLock("driver:lock:" + DRIVER_ID);

		long heldMillis = measureLockHeldDuration(lock, () -> strategy.tryAssign(DRIVER_ID));

		assertThat(heldMillis)
				.as("before-lock이면 지연이 락 밖에서 끝나므로 보유시간이 지연시간에 크게 못 미쳐야 한다")
				.isLessThan((long) (DELAY_MS * 0.5));
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
