package com.bnbnac.ride_lock.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

	List<Trip> findByStatusAndAssignedAtBefore(TripStatus status, OffsetDateTime cutoff);

}
