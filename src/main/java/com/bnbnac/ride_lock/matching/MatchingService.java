package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.NearbyDriver;
import com.bnbnac.ride_lock.matching.lock.DriverLockStrategy;
import com.bnbnac.ride_lock.trip.Trip;
import com.bnbnac.ride_lock.trip.TripService;
import org.springframework.stereotype.Service;

import java.util.List;

// match() 자체는 더 이상 @Transactional이 아니다 - 후보 1명당 락 획득~해제가
// DriverLockStrategy.tryAssign() 내부에서 완결돼야 한다. 전체를 하나의 트랜잭션으로 감싸면
// 비관적 락(FOR UPDATE)에서 실패한 후보의 row 락이 이 메서드가 리턴할 때까지 풀리지 않아
// 락 경합 측정이 왜곡된다 (설계문서 §2).
@Service
public class MatchingService {

	private static final double DEFAULT_RADIUS_METERS = 5000;
	private static final int DEFAULT_CANDIDATE_LIMIT = 20;

	private final DriverLocationRepository driverLocationRepository;
	private final DriverLockStrategy lockStrategy;
	private final TripService tripService;

	public MatchingService(DriverLocationRepository driverLocationRepository,
			DriverLockStrategy lockStrategy, TripService tripService) {
		this.driverLocationRepository = driverLocationRepository;
		this.lockStrategy = lockStrategy;
		this.tripService = tripService;
	}

	public MatchingResult match(double lng, double lat) {
		List<NearbyDriver> candidates = driverLocationRepository.findIdleDriversNear(
				lng, lat, DEFAULT_RADIUS_METERS, DEFAULT_CANDIDATE_LIMIT);

		for (NearbyDriver candidate : candidates) {
			if (lockStrategy.tryAssign(candidate.getDriverId())) {
				Trip trip = tripService.createTrip(candidate.getDriverId());
				return new MatchingResult(trip.getId(), candidate.getDriverId(), candidate.getDistanceMeters());
			}
		}
		throw new NoAvailableDriverException();
	}

}
