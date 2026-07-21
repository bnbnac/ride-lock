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

// routing 지연을 FOR UPDATE 락 획득 후~해제 전(inside-lock)에 두면, 같은 기사를 노리는 두 번째
// 호출은 첫 번째가 지연을 끝내고 커밋할 때까지 SQL 레벨에서 블로킹된다 - 총 소요시간이 지연시간의
// 약 2배로 직렬화되어야 한다. PessimisticLockRoutingBeforeLockTest(반대 배치)와 대조된다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=pessimistic",
		"matching.routing-delay-ms=300",
		"matching.routing-delay-inside-lock=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PessimisticLockRoutingInsideLockTest extends AbstractIntegrationTest {

	private static final long DRIVER_ID = 888L;
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
	void concurrentCallsSerializeRoughlyByDelay() throws Exception {
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
				.as("inside-lock이면 두 번째 호출이 첫 번째 지연이 끝날 때까지 블로킹돼 총 소요시간이 지연시간의 약 2배여야 한다")
				.isGreaterThanOrEqualTo((long) (DELAY_MS * 1.8));
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
