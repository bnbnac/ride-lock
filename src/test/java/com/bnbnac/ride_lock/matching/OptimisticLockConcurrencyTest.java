package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.matching.lock.OptimisticLockStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
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

// @DirtiesContext: 이 클래스만 HikariCP pool을 50으로 올린 별도 컨텍스트를 쓴다 - 안 닫고
// 두면 이후 다른 동시성 테스트(Pessimistic 등)의 오버사이즈 pool과 겹쳐 공유 Postgres
// 컨테이너의 max_connections를 넘겨버린다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=optimistic",
		"spring.datasource.hikari.maximum-pool-size=" + OptimisticLockConcurrencyTest.CONCURRENT_REQUESTS
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OptimisticLockConcurrencyTest extends AbstractIntegrationTest {

	static final int CONCURRENT_REQUESTS = 50;
	private static final long DRIVER_ID = 999L;

	@Autowired
	private OptimisticLockStrategy optimisticLockStrategy;

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
					if (optimisticLockStrategy.tryAssign(DRIVER_ID)) {
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
				.as("낙관적 락은 동시 요청 중 정확히 1명만 배정에 성공해야 한다")
				.isEqualTo(1);
	}

}
