package com.bnbnac.ride_lock.trip;

import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class TripService {

	private final TripRepository tripRepository;
	private final DriverStatusRepository driverStatusRepository;

	public TripService(TripRepository tripRepository, DriverStatusRepository driverStatusRepository) {
		this.tripRepository = tripRepository;
		this.driverStatusRepository = driverStatusRepository;
	}

	@Transactional
	public Trip createTrip(Long driverId) {
		OffsetDateTime now = OffsetDateTime.now();
		return tripRepository.save(new Trip(driverId, TripStatus.ASSIGNED, now, now));
	}

	// 스케줄러가 조회한 시점과 처리 시점 사이에 이미 다른 경로로 상태가 바뀌었으면 조용히 스킵한다 -
	// Trip.expire()/DriverStatus.release() 둘 다 자기 상태를 스스로 확인하고 실패 시 false를
	// 돌려주므로 예외를 던지지 않는다.
	@Transactional
	public void expire(Long tripId) {
		OffsetDateTime now = OffsetDateTime.now();
		Trip trip = tripRepository.findById(tripId).orElseThrow();
		if (!trip.expire(now)) {
			return;
		}
		tripRepository.save(trip);

		DriverStatus status = driverStatusRepository.findById(trip.getDriverId()).orElseThrow();
		if (status.release(now)) {
			driverStatusRepository.save(status);
		}
	}

}
