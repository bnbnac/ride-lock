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
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// PessimisticLockStrategy가 동시 요청 중 정확히 1명만 성공시키는지 확인 -
// MatchingRaceConditionTest(락 없음, 여러 명 성공)와 반대 방향 단언.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=pessimistic",
		"spring.datasource.hikari.maximum-pool-size=" + PessimisticLockConcurrencyTest.CONCURRENT_REQUESTS
})
class PessimisticLockConcurrencyTest extends AbstractIntegrationTest {

	static final int CONCURRENT_REQUESTS = 50;
	private static final long DRIVER_ID = 999L;

	@Autowired
	private PessimisticLockStrategy pessimisticLockStrategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@AfterEach
	void cleanUp() {
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void exactlyOneConcurrentTryAssignSucceeds() throws Exception {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();

		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			futures.add(executor.submit(() -> {
				try {
					startLatch.await();
					if (pessimisticLockStrategy.tryAssign(DRIVER_ID)) {
						successCount.incrementAndGet();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
		}

		startLatch.countDown();
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		executor.shutdown();

		assertThat(successCount.get())
				.as("비관적 락은 동시 요청 중 정확히 1명만 배정에 성공해야 한다")
				.isEqualTo(1);
	}

}
