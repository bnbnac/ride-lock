package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 락 없는 매칭 서비스가 실제로 레이스 컨디션을 일으키는지 재현하는 "before" 베이스라인.
// 각 스레드의 트랜잭션이 서로 독립적이어야 경합이 재현되므로 @Transactional을 쓰지 않고,
// 대신 싱글턴 컨테이너 DB를 다른 테스트와 공유하는 문제를 afterEach에서 직접 정리해 해결한다.
//
// read-check-write 사이 경합 윈도우는 JIT 예열 상태에 따라 좁아질 수 있어(실측: 격리 실행 시
// 재현, 전체 스위트 뒤쪽에서 실행 시 1회차엔 미재현) 단발 시도에 의존하지 않고 여러 라운드 재시도해서
// "적어도 한 번은 재현된다"를 검증한다. 프로덕션 코드에 인위적 지연은 넣지 않는다.
//
// HikariCP 기본 maximum-pool-size(10)보다 CONCURRENT_REQUESTS가 크면, match()가 @Transactional이라
// 커넥션을 트랜잭션 내내 물고 있는 구조상 스레드 일부가 커넥션 대기 큐에서 순차화돼 진짜 동시 경합이
// 줄어든다 - 이 테스트에서만 풀 크기를 CONCURRENT_REQUESTS 이상으로 올려서 그 병목을 없앤다.
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=" + MatchingRaceConditionTest.CONCURRENT_REQUESTS)
class MatchingRaceConditionTest extends AbstractIntegrationTest {

	static final int CONCURRENT_REQUESTS = 50;

	private static final double ORIGIN_LNG = 126.9707;
	private static final double ORIGIN_LAT = 37.5547;
	private static final long DRIVER_ID = 999L;
	private static final int MAX_ROUNDS = 20;

	@Autowired
	private MatchingService matchingService;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Autowired
	private DataSource dataSource;

	@AfterEach
	void cleanUp() {
		driverLocationRepository.deleteById(DRIVER_ID);
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void hikariPoolSizeIsRaisedToMatchConcurrentRequestCount() {
		assertThat(((HikariDataSource) dataSource).getMaximumPoolSize())
				.as("@TestPropertySource 설정이 실제로 반영됐는지 확인 - 오타로 조용히 기본값(10)으로 돌아가지 않았는지")
				.isEqualTo(CONCURRENT_REQUESTS);
	}

	@Test
	void unlockedMatchingLetsMultipleRequestsWinTheSameDriver() throws Exception {
		driverStatusRepository.save(new DriverStatus(DRIVER_ID, DriverState.IDLE, 0L, OffsetDateTime.now()));
		driverLocationRepository.upsertLocation(DRIVER_ID, ORIGIN_LNG, ORIGIN_LAT);

		int winners = 0;
		for (int round = 0; round < MAX_ROUNDS && winners <= 1; round++) {
			resetDriverToIdle();
			winners = runConcurrentMatchAttempts();
		}

		assertThat(winners)
				.as("락 없이 반복 시도하면 같은 기사가 여러 번 배정되는 순간이 있어야 한다 (레이스 컨디션 재현)")
				.isGreaterThan(1);
	}

	private void resetDriverToIdle() {
		driverStatusRepository.save(new DriverStatus(DRIVER_ID, DriverState.IDLE, 0L, OffsetDateTime.now()));
	}

	private int runConcurrentMatchAttempts() throws InterruptedException, ExecutionException, TimeoutException {
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
		return successCount.get();
	}

}
