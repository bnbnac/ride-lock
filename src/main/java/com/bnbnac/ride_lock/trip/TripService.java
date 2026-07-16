package com.bnbnac.ride_lock.trip;

import com.bnbnac.ride_lock.driver.DriverState;
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

	// driver를 IDLE→ASSIGNED로 전이시키는 책임은 DriverLockStrategy.tryAssign()에 있다 -
	// 동시성 제어가 필요한 그 전이를 여기서 다시 시도하면 이미 ASSIGNED인 상태라 항상 실패한다.
	// 대신 그 결과가 실제로 반영됐는지만 읽어서 확인한다.
	@Transactional
	public Trip createTrip(Long driverId) {
		DriverStatus status = driverStatusRepository.findById(driverId).orElseThrow();
		if (status.getStatus() != DriverState.ASSIGNED) {
			throw new IllegalStateException("driver " + driverId + " is not ASSIGNED");
		}
		OffsetDateTime now = OffsetDateTime.now();
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
