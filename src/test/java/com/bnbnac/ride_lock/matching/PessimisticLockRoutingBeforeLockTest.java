package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.matching.lock.PessimisticLockStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// routing 지연을 FOR UPDATE 락 획득 전(before-lock, 기본값)에 두면, 두 호출의 지연이 락 없이
// 병렬로 겹친다 - 실제로 직렬화되는 구간은 그 뒤의 빠른 DB 갱신뿐이라 총 소요시간이 지연시간의
// 2배에 크게 못 미쳐야 한다. PessimisticLockRoutingInsideLockTest(반대 배치)와 대조된다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=pessimistic",
		"matching.routing-delay-ms=300",
		"matching.routing-delay-inside-lock=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PessimisticLockRoutingBeforeLockTest extends AbstractIntegrationTest {

	private static final long DRIVER_ID = 889L;
	private static final long DELAY_MS = 300;

	@Autowired
	private PessimisticLockStrategy strategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@AfterEach
	void cleanUp() {
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void concurrentCallsOverlapWithinRoughlyOneDelay() throws Exception {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch startLatch = new CountDownLatch(1);

		Future<?> a = executor.submit(() -> awaitAndAssign(startLatch));
		Future<?> b = executor.submit(() -> awaitAndAssign(startLatch));

		long start = System.nanoTime();
		startLatch.countDown();
		a.get(10, TimeUnit.SECONDS);
		b.get(10, TimeUnit.SECONDS);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
		executor.shutdown();

		assertThat(elapsedMillis)
				.as("before-lock이면 두 지연이 병렬로 겹쳐서 총 소요시간이 지연시간의 2배에 크게 못 미쳐야 한다")
				.isLessThan((long) (DELAY_MS * 1.8));
	}

	private void awaitAndAssign(CountDownLatch startLatch) {
		try {
			startLatch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		strategy.tryAssign(DRIVER_ID);
	}

}
