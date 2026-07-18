package com.bnbnac.ride_lock.trip;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 여러 스케줄러 인스턴스가 같은 트립을 동시에 만료 처리하려는 상황을 재현한다 - @Transactional을
// 안 쓴다(테스트 트랜잭션이 열려 있으면 각 스레드의 expire() 호출이 진짜 독립 트랜잭션으로
// 실행되지 않아 SKIP LOCKED 경합 자체가 재현되지 않는다). HikariCP pool을 동시 요청 수만큼
// 올려서(다른 동시성 테스트와 동일한 이유) 커넥션 대기로 인한 순차화를 없앤다.
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size="
		+ AssignTimeoutSchedulerConcurrencyTest.CONCURRENT_REQUESTS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssignTimeoutSchedulerConcurrencyTest extends AbstractIntegrationTest {

	static final int CONCURRENT_REQUESTS = 50;
	private static final long DRIVER_ID = 996L;
	private static final int MAX_ROUNDS = 20;

	@Autowired
	private TripService tripService;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@AfterEach
	void cleanUp() {
		tripRepository.deleteAll(tripRepository.findByDriverId(DRIVER_ID));
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void concurrentExpireOfTheSameTripNeverThrowsAndConvergesOnce() throws Exception {
		List<Throwable> failures = new CopyOnWriteArrayList<>();

		for (int round = 0; round < MAX_ROUNDS && failures.isEmpty(); round++) {
			resetAssignedTripAndDriver();
			failures.addAll(runConcurrentExpireAttempts());
		}

		assertThat(failures)
				.as("SKIP LOCKED로 인해 나머지 스레드는 조용히 건너뛰어야 하고, 아무도 예외를 던지면 안 된다")
				.isEmpty();
	}

	private void resetAssignedTripAndDriver() {
		tripRepository.deleteAll(tripRepository.findByDriverId(DRIVER_ID));
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.ASSIGNED));
		OffsetDateTime old = OffsetDateTime.now().minusSeconds(60);
		tripRepository.save(Trip.of(DRIVER_ID, TripStatus.ASSIGNED, old));
	}

	private List<Throwable> runConcurrentExpireAttempts() throws Exception {
		Long tripId = tripRepository.findByDriverId(DRIVER_ID).get(0).getId();
		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		List<Throwable> failures = new CopyOnWriteArrayList<>();

		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			futures.add(executor.submit(() -> {
				try {
					startLatch.await();
					tripService.expire(tripId);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (Throwable t) {
					failures.add(t);
				}
			}));
		}

		startLatch.countDown();
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		executor.shutdown();
		return failures;
	}

}
