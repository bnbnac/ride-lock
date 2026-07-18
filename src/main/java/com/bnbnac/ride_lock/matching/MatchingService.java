package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.NearbyDriver;
import com.bnbnac.ride_lock.matching.lock.DriverLockStrategy;
import com.bnbnac.ride_lock.trip.Trip;
import com.bnbnac.ride_lock.trip.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

// match() 자체는 더 이상 @Transactional이 아니다 - 후보 1명당 tryAssign()~createTrip()이
// TransactionTemplate으로 감싼 하나의 트랜잭션 안에서 완결된다. 전체 루프를 하나의 트랜잭션으로
// 감싸면 비관적 락(FOR UPDATE)에서 실패한 후보의 row 락이 이 메서드가 리턴할 때까지 풀리지 않아
// 락 경합 측정이 왜곡되므로, 트랜잭션 범위를 "후보 1명당"으로 좁혀서 그 문제를 피하면서도
// tryAssign 성공과 createTrip을 원자적으로 묶는다 (설계문서 §2) - self-invocation 문제 때문에
// @Transactional 대신 TransactionTemplate을 직접 쓴다.
@Service
public class MatchingService {

	private static final double DEFAULT_RADIUS_METERS = 5000;
	private static final int DEFAULT_CANDIDATE_LIMIT = 20;

	private final DriverLocationRepository driverLocationRepository;
	private final DriverLockStrategy lockStrategy;
	private final TripService tripService;
	private final TransactionTemplate transactionTemplate;

	public MatchingService(DriverLocationRepository driverLocationRepository,
			DriverLockStrategy lockStrategy, TripService tripService,
			PlatformTransactionManager transactionManager) {
		this.driverLocationRepository = driverLocationRepository;
		this.lockStrategy = lockStrategy;
		this.tripService = tripService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public MatchingResult match(double lng, double lat) {
		List<NearbyDriver> candidates = driverLocationRepository.findIdleDriversNear(
				lng, lat, DEFAULT_RADIUS_METERS, DEFAULT_CANDIDATE_LIMIT);

		for (NearbyDriver candidate : candidates) {
			MatchingResult result = transactionTemplate.execute(status -> tryAssignAndCreateTrip(candidate));
			if (result != null) {
				return result;
			}
		}
		throw new NoAvailableDriverException();
	}

	private MatchingResult tryAssignAndCreateTrip(NearbyDriver candidate) {
		if (!lockStrategy.tryAssign(candidate.getDriverId())) {
			return null;
		}
		Trip trip = tripService.createTrip(candidate.getDriverId());
		return new MatchingResult(trip.getId(), candidate.getDriverId(), candidate.getDistanceMeters());
	}

}
