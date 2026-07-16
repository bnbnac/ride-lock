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
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void createTripPersistsAssignedTripAndAssignsDriver() {
		seedDriverStatus(DriverState.IDLE);

		Trip trip = tripService.createTrip(1L);

		assertThat(trip.getId()).isNotNull();
		assertThat(trip.getDriverId()).isEqualTo(1L);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.ASSIGNED);
		assertThat(driverStatusRepository.findById(1L)).get()
				.extracting(DriverStatus::getStatus).isEqualTo(DriverState.ASSIGNED);
	}

	@Test
	void createTripThrowsWhenDriverIsNotIdle() {
		seedDriverStatus(DriverState.ASSIGNED);

		assertThatThrownBy(() -> tripService.createTrip(1L))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void createTripThrowsWhenDriverStatusMissing() {
		assertThatThrownBy(() -> tripService.createTrip(1L))
				.isInstanceOf(NoSuchElementException.class);
	}

	@Test
	void expireRevertsAssignedTripAndDriverToCancelledAndIdle() {
		seedDriverStatus(DriverState.IDLE);
		Trip trip = tripService.createTrip(1L);

		tripService.expire(trip.getId());

		assertThat(tripRepository.findById(trip.getId())).get()
				.extracting(Trip::getStatus).isEqualTo(TripStatus.CANCELLED);
		assertThat(driverStatusRepository.findById(1L)).get()
				.extracting(DriverStatus::getStatus).isEqualTo(DriverState.IDLE);
	}

	@Test
	void expireOnNonAssignedTripDoesNothing() {
		seedDriverStatus(DriverState.ON_TRIP);
		OffsetDateTime now = OffsetDateTime.now();
		Trip onTripTrip = tripRepository.save(Trip.of(1L, TripStatus.ON_TRIP, now));

		tripService.expire(onTripTrip.getId());

		assertThat(tripRepository.findById(onTripTrip.getId())).get()
				.extracting(Trip::getStatus).isEqualTo(TripStatus.ON_TRIP);
		assertThat(driverStatusRepository.findById(1L)).get()
				.extracting(DriverStatus::getStatus).isEqualTo(DriverState.ON_TRIP);
	}

	private void seedDriverStatus(DriverState state) {
		driverStatusRepository.save(new DriverStatus(1L, state, 0L, OffsetDateTime.now()));
	}

}
