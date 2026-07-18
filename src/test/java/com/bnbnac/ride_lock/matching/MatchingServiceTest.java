package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.trip.Trip;
import com.bnbnac.ride_lock.trip.TripRepository;
import com.bnbnac.ride_lock.trip.TripStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "matching.lock-strategy=none")
@Transactional
class MatchingServiceTest extends AbstractIntegrationTest {

	private static final double ORIGIN_LNG = 126.9707;
	private static final double ORIGIN_LAT = 37.5547;

	@Autowired
	private MatchingService matchingService;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Autowired
	private TripRepository tripRepository;

	@Test
	void matchingSuccessfullyCreatesTripAndReturnsItsId() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));
		driverLocationRepository.upsertLocation(1L, ORIGIN_LNG, ORIGIN_LAT);

		MatchingResult result = matchingService.match(ORIGIN_LNG, ORIGIN_LAT);

		assertThat(result.driverId()).isEqualTo(1L);
		assertThat(result.tripId()).isNotNull();
		Trip trip = tripRepository.findById(result.tripId()).orElseThrow();
		assertThat(trip.getDriverId()).isEqualTo(1L);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.ASSIGNED);
	}

	@Test
	void matchingWithNoIdleDriversThrows() {
		assertThatThrownBy(() -> matchingService.match(ORIGIN_LNG, ORIGIN_LAT))
				.isInstanceOf(NoAvailableDriverException.class);
	}

}
