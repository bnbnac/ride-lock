package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.trip.TripRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// @Transactional을 일부러 안 쓴다 - 테스트 트랜잭션이 열려 있으면 MatchingService 내부의
// 트랜잭션 경계가 테스트 트랜잭션에 흡수돼 실제 커밋/롤백 여부를 관찰할 수 없다.
@SpringBootTest
class MatchingServiceAtomicityTest extends AbstractIntegrationTest {

	private static final double ORIGIN_LNG = 126.9707;
	private static final double ORIGIN_LAT = 37.5547;
	private static final long DRIVER_ID = 998L;

	@Autowired
	private MatchingService matchingService;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@MockitoBean
	private TripRepository tripRepository;

	@AfterEach
	void cleanUp() {
		driverLocationRepository.deleteById(DRIVER_ID);
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void assignmentRollsBackWhenTripCreationFails() {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));
		driverLocationRepository.upsertLocation(DRIVER_ID, ORIGIN_LNG, ORIGIN_LAT);
		when(tripRepository.save(any())).thenThrow(new RuntimeException("trip save failed"));

		assertThatThrownBy(() -> matchingService.match(ORIGIN_LNG, ORIGIN_LAT))
				.isInstanceOf(RuntimeException.class);

		DriverStatus status = driverStatusRepository.findById(DRIVER_ID).orElseThrow();
		assertThat(status.getStatus())
				.as("Trip 생성이 실패하면 배정도 함께 롤백돼야 한다 - 안 그러면 기사가 ASSIGNED로 " +
						"갇힌 채 대응하는 Trip이 영영 생기지 않는다")
				.isEqualTo(DriverState.IDLE);
	}

}
