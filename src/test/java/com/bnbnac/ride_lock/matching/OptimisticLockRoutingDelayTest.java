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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 낙관적 락은 보유할 락이 없으니 배치 선택지가 없다 - routingClient는 항상 버전을 읽은 직후,
// CAS를 시도하기 직전 한 곳에서만 불린다. 이 자리가 바로 낙관적 락의 알려진 약점을 재현하는
// 지점이다: 읽기~쓰기 사이 창이 길어질수록, 그 사이 다른 트랜잭션이 값을 바꿔놨을 확률이
// 커지고 아무도 그 창을 막아주지 않는다. 이 테스트는 그 창 안에서 실제로 값이 바뀌면 원래
// 호출의 CAS가 정확히 실패하는지(버전 불일치 감지)를 검증한다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=optimistic",
		"matching.routing-delay-ms=300"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OptimisticLockRoutingDelayTest extends AbstractIntegrationTest {

	private static final long DRIVER_ID = 892L;

	@Autowired
	private OptimisticLockStrategy strategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@AfterEach
	void cleanUp() {
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void staleWriteDuringRoutingDelayMakesTheOriginalCasFail() throws Exception {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));

		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Boolean> original = executor.submit(() -> strategy.tryAssign(DRIVER_ID));

		// original이 findById()로 version=0을 읽고 routing 지연(300ms)에 들어갈 시간을 준 뒤,
		// 그 창 안에서 끼어들어 값을 먼저 바꿔버린다.
		Thread.sleep(50);
		boolean interloperWon = driverStatusRepository.compareAndSetAssigned(DRIVER_ID, 0L) > 0;

		boolean originalWon = original.get(5, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(interloperWon).as("끼어든 갱신은 지연 중이라 아직 안 바뀐 version=0을 그대로 맞춰야 성공한다").isTrue();
		assertThat(originalWon)
				.as("원래 호출은 지연이 끝난 뒤 자신이 읽었던 옛 version으로 CAS를 시도하므로 실패해야 한다")
				.isFalse();
	}

}
