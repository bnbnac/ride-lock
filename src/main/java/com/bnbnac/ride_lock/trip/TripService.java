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

	// Trip은 "배차가 성사된 상황"만 표현하므로, DriverStatus를 ASSIGNED로 전이시키는 데
	// 성공했을 때만 Trip을 생성한다.
	@Transactional
	public Trip createTrip(Long driverId) {
		DriverStatus status = driverStatusRepository.findById(driverId).orElseThrow();
		OffsetDateTime now = OffsetDateTime.now();
		if (!status.assign(now)) {
			throw new IllegalStateException("driver " + driverId + " is not IDLE");
		}
		driverStatusRepository.save(status);
		return tripRepository.save(Trip.of(driverId, TripStatus.ASSIGNED, now));
	}

	// Trip이 이미 다른 경로로 ASSIGNED를 벗어났으면(트립 자신의 레이스) 조용히 스킵한다.
	// 하지만 Trip을 CANCELLED로 넘긴 이상 DriverStatus도 반드시 함께 풀려야 하므로,
	// release()가 실패하면 Trip과 DriverStatus가 불일치한 상태라는 뜻으로 보고 예외를 던져
	// 트랜잭션을 롤백시킨다 - 다음 스케줄러 사이클에서 다시 시도된다.
	@Transactional
	public void expire(Long tripId) {
		OffsetDateTime now = OffsetDateTime.now();
		Trip trip = tripRepository.findById(tripId).orElseThrow();
		if (!trip.expire(now)) {
			return;
		}
		tripRepository.save(trip);

		DriverStatus status = driverStatusRepository.findById(trip.getDriverId()).orElseThrow();
		if (!status.release(now)) {
			throw new IllegalStateException("driver " + trip.getDriverId() + " is not ASSIGNED");
		}
		driverStatusRepository.save(status);
	}

}
