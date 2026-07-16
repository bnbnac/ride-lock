package com.bnbnac.ride_lock.trip;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TripServiceTest extends AbstractIntegrationTest {

	@Autowired
	private TripService tripService;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void createTripPersistsAssignedTrip() {
		Trip trip = tripService.createTrip(1L);

		assertThat(trip.getId()).isNotNull();
		assertThat(trip.getDriverId()).isEqualTo(1L);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.ASSIGNED);
	}

	@Test
	void expireRevertsAssignedTripAndDriverToCancelledAndIdle() {
		driverStatusRepository.save(new DriverStatus(1L, DriverState.ASSIGNED, 0L, OffsetDateTime.now()));
		Trip trip = tripService.createTrip(1L);

		tripService.expire(trip.getId());

		assertThat(tripRepository.findById(trip.getId())).get()
				.extracting(Trip::getStatus).isEqualTo(TripStatus.CANCELLED);
		assertThat(driverStatusRepository.findById(1L)).get()
				.extracting(DriverStatus::getStatus).isEqualTo(DriverState.IDLE);
	}

	@Test
	void expireOnNonAssignedTripDoesNothing() {
		driverStatusRepository.save(new DriverStatus(1L, DriverState.ON_TRIP, 0L, OffsetDateTime.now()));
		OffsetDateTime now = OffsetDateTime.now();
		Trip onTripTrip = tripRepository.save(new Trip(1L, TripStatus.ON_TRIP, now, now));

		tripService.expire(onTripTrip.getId());

		assertThat(tripRepository.findById(onTripTrip.getId())).get()
				.extracting(Trip::getStatus).isEqualTo(TripStatus.ON_TRIP);
		assertThat(driverStatusRepository.findById(1L)).get()
				.extracting(DriverStatus::getStatus).isEqualTo(DriverState.ON_TRIP);
	}

}
