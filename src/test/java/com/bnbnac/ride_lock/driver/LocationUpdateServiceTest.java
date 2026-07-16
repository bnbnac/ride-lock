package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.assertThat;

// LocationUpdateService 결과 검증(DB에 실제로 뭐가 남았는지) 전용 - 호출 순서 자체는
// LocationUpdateServiceOrderTest(Mockito 단위 테스트)가 맡는다. 관심사가 달라 파일을 나눴다.
@SpringBootTest
@Transactional
class LocationUpdateServiceTest extends AbstractIntegrationTest {

	private static final double LNG = 126.9707;
	private static final double LAT = 37.5547;

	@Autowired
	private LocationUpdateService locationUpdateService;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void reportingLocationForNewDriverCreatesLocationAndIdleStatus() {
		locationUpdateService.reportLocation(1L, LNG, LAT);

		assertThat(driverLocationRepository.findById(1L)).isPresent();
		assertThat(driverStatusRepository.findById(1L))
				.get()
				.extracting(DriverStatus::getStatus)
				.isEqualTo(DriverState.IDLE);
	}

	@Test
	void reportingLocationForAssignedDriverDoesNotResetStatusToIdle() {
		driverStatusRepository.save(DriverStatus.of(2L, DriverState.ASSIGNED));

		locationUpdateService.reportLocation(2L, LNG, LAT);

		assertThat(driverLocationRepository.findById(2L)).isPresent();
		assertThat(driverStatusRepository.findById(2L))
				.get()
				.extracting(DriverStatus::getStatus)
				.isEqualTo(DriverState.ASSIGNED);
	}

}
