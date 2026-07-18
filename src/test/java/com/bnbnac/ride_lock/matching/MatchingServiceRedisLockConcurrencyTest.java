package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractRedisIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.trip.TripRepository;
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

// RedissonLockStrategy.tryAssign()을 MatchingService 없이 직접 호출하는 RedissonLockConcurrencyTest와
// 달리, 실제 운영 경로(MatchingService가 tryAssign()+createTrip()을 하나의 트랜잭션으로 묶어 호출)를
// 그대로 거쳐서 동시성이 깨지지 않는지 검증한다. tryAssign() 내부에서 Redis 락을 finally에서 바로
// unlock()했다면, 바깥 트랜잭션(createTrip()까지 포함)이 커밋되기 전에 락이 풀려 다른 스레드가
// 아직 안 보이는 옛 IDLE 상태를 읽고 중복 배정할 수 있었다 - 이 테스트가 그 회귀를 잡는다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=redis",
		"spring.datasource.hikari.maximum-pool-size=" + MatchingServiceRedisLockConcurrencyTest.CONCURRENT_REQUESTS
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchingServiceRedisLockConcurrencyTest extends AbstractRedisIntegrationTest {

	static final int CONCURRENT_REQUESTS = 50;

	private static final double ORIGIN_LNG = 126.9707;
	private static final double ORIGIN_LAT = 37.5547;
	private static final long DRIVER_ID = 997L;

	@Autowired
	private MatchingService matchingService;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Autowired
	private TripRepository tripRepository;

	@AfterEach
	void cleanUp() {
		tripRepository.deleteAll(tripRepository.findByDriverId(DRIVER_ID));
		driverLocationRepository.deleteById(DRIVER_ID);
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void exactlyOneConcurrentMatchSucceedsForTheSameDriver() throws Exception {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));
		driverLocationRepository.upsertLocation(DRIVER_ID, ORIGIN_LNG, ORIGIN_LAT);

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch startLatch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();

		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			futures.add(executor.submit(() -> {
				try {
					startLatch.await();
					MatchingResult result = matchingService.match(ORIGIN_LNG, ORIGIN_LAT);
					if (result.driverId().equals(DRIVER_ID)) {
						successCount.incrementAndGet();
					}
				} catch (NoAvailableDriverException ignored) {
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
				.as("MatchingService를 통한 실제 경로에서도 Redis 분산락은 정확히 1명만 배정에 성공해야 한다")
				.isEqualTo(1);
	}

}
