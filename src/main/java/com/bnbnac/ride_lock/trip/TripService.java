package com.bnbnac.ride_lock.trip;

import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

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

	// findByIdForUpdateSkipLocked: 여러 스케줄러 인스턴스가 같은 트립을 동시에 만료 처리하려
	// 하면, 먼저 잡은 쪽만 처리하고 나머지는 빈 결과를 받아 조용히 리턴한다(트립이 없는 것과
	// 구분하지 않는다 - 이 메서드는 스케줄러가 방금 조회한 존재하는 트립 id로만 호출되므로,
	// 빈 결과는 사실상 항상 "다른 인스턴스가 먼저 잡았다"는 뜻이다).
	// driverStatusRepository도 findByIdForUpdate로 잠근다 - matching.lock-strategy 설정과
	// 무관하게 release()는 항상 이 경로 하나뿐이라, assign() 쪽처럼 전략별로 갈아끼울 필요는
	// 없지만 잠금 없이 두면 나중에 ON_TRIP/취소 API가 이 경로를 재사용할 때 레이스가 열린다.
	// Trip이 이미 다른 경로로 ASSIGNED를 벗어났으면(트립 자신의 레이스) 조용히 스킵한다.
	// 하지만 Trip을 CANCELLED로 넘긴 이상 DriverStatus도 반드시 함께 풀려야 하므로,
	// release()가 실패하면 Trip과 DriverStatus가 불일치한 상태라는 뜻으로 보고 예외를 던져
	// 트랜잭션을 롤백시킨다 - 다음 스케줄러 사이클에서 다시 시도된다.
	@Transactional
	public void expire(Long tripId) {
		OffsetDateTime now = OffsetDateTime.now();
		Optional<Trip> locked = tripRepository.findByIdForUpdateSkipLocked(tripId);
		if (locked.isEmpty()) {
			return;
		}
		Trip trip = locked.get();
		if (!trip.expire(now)) {
			return;
		}
		tripRepository.save(trip);

		DriverStatus status = driverStatusRepository.findByIdForUpdate(trip.getDriverId()).orElseThrow();
		if (!status.release(now)) {
			throw new IllegalStateException("driver " + trip.getDriverId() + " is not ASSIGNED");
		}
		driverStatusRepository.save(status);
	}

}
