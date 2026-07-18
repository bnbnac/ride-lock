package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// matching.candidate-radius-meters가 실제로 findIdleDriversNear() 호출에 전달되는지 확인한다 -
// 생성자 파라미터 배선이 잘못되거나(순서 실수 등) 다시 하드코딩 상수로 되돌아가는 회귀를 잡는다.
@SpringBootTest
@TestPropertySource(properties = {
		"matching.lock-strategy=none",
		"matching.candidate-radius-meters=100"
})
@Transactional
class MatchingServiceCandidateRadiusTest extends AbstractIntegrationTest {

	private static final double ORIGIN_LNG = 126.9707;
	private static final double ORIGIN_LAT = 37.5547;
	// 위도 0.01도 ≈ 1.11km 떨어진 지점 - 기본 반경(5000m)이면 후보에 잡히지만, 여기서 좁힌
	// 100m 반경 밖이라 candidate-radius-meters가 실제로 반영됐다면 후보에서 빠져야 한다.
	private static final double FAR_DRIVER_LAT = ORIGIN_LAT + 0.01;

	@Autowired
	private MatchingService matchingService;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void driverOutsideConfiguredRadiusIsNotMatched() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));
		driverLocationRepository.upsertLocation(1L, ORIGIN_LNG, FAR_DRIVER_LAT);

		assertThatThrownBy(() -> matchingService.match(ORIGIN_LNG, ORIGIN_LAT))
				.isInstanceOf(NoAvailableDriverException.class);
	}

}
