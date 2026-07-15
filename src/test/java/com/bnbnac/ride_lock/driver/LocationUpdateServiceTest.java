package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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
		driverStatusRepository.save(new DriverStatus(2L, DriverState.ASSIGNED, 0L, OffsetDateTime.now()));

		locationUpdateService.reportLocation(2L, LNG, LAT);

		assertThat(driverLocationRepository.findById(2L)).isPresent();
		assertThat(driverStatusRepository.findById(2L))
				.get()
				.extracting(DriverStatus::getStatus)
				.isEqualTo(DriverState.ASSIGNED);
	}

}
