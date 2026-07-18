package com.bnbnac.ride_lock.trip;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "matching.assign-timeout-seconds=1")
@Transactional
class AssignTimeoutSchedulerTest extends AbstractIntegrationTest {

	@Autowired
	private AssignTimeoutScheduler assignTimeoutScheduler;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void expiresAssignedTripsOlderThanTimeout() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.ASSIGNED));
		OffsetDateTime old = OffsetDateTime.now().minusSeconds(5);
		Trip trip = tripRepository.save(Trip.of(1L, TripStatus.ASSIGNED, old));

		assignTimeoutScheduler.checkExpiredAssignments();

		assertThat(tripRepository.findById(trip.getId())).get()
				.extracting(Trip::getStatus).isEqualTo(TripStatus.CANCELLED);
		assertThat(driverStatusRepository.findById(1L)).get()
				.extracting(DriverStatus::getStatus).isEqualTo(DriverState.IDLE);
	}

	@Test
	void doesNotExpireRecentAssignedTrips() {
		driverStatusRepository.save(DriverStatus.of(2L, DriverState.ASSIGNED));
		Trip trip = tripRepository.save(Trip.of(2L, TripStatus.ASSIGNED, OffsetDateTime.now()));

		assignTimeoutScheduler.checkExpiredAssignments();

		assertThat(tripRepository.findById(trip.getId())).get()
				.extracting(Trip::getStatus).isEqualTo(TripStatus.ASSIGNED);
	}

}
