package com.bnbnac.ride_lock.trip;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TripRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private TripRepository tripRepository;

	@Test
	void savesAndLoadsTrip() {
		Trip saved = tripRepository.save(new Trip(1L, TripStatus.ASSIGNED, OffsetDateTime.now(), OffsetDateTime.now()));

		Trip found = tripRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getDriverId()).isEqualTo(1L);
		assertThat(found.getStatus()).isEqualTo(TripStatus.ASSIGNED);
	}

	@Test
	void findsAssignedTripsOlderThanCutoff() {
		OffsetDateTime past = OffsetDateTime.now().minusSeconds(60);
		OffsetDateTime recent = OffsetDateTime.now();
		Trip old = tripRepository.save(new Trip(1L, TripStatus.ASSIGNED, past, past));
		tripRepository.save(new Trip(2L, TripStatus.ASSIGNED, recent, recent));

		List<Trip> expired = tripRepository.findByStatusAndAssignedAtBefore(
				TripStatus.ASSIGNED, OffsetDateTime.now().minusSeconds(30));

		assertThat(expired).extracting(Trip::getId).containsExactly(old.getId());
	}

}
