package com.bnbnac.ride_lock.matching;

import com.bnbnac.ride_lock.driver.DriverLocationRepository;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.driver.NearbyDriver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

// 락 없는 "before" 베이스라인. 조회-배정 사이 검증이 없어 동시 요청 시 같은 기사가
// 중복 배정될 수 있다 (§4.1) - 이후 비관적/낙관적/분산락 버전과 비교할 기준점으로 의도적으로 남겨둠.
@Service
public class MatchingService {

	private static final double DEFAULT_RADIUS_METERS = 5000;
	private static final int DEFAULT_CANDIDATE_LIMIT = 20;

	private final DriverLocationRepository driverLocationRepository;
	private final DriverStatusRepository driverStatusRepository;

	public MatchingService(DriverLocationRepository driverLocationRepository,
			DriverStatusRepository driverStatusRepository) {
		this.driverLocationRepository = driverLocationRepository;
		this.driverStatusRepository = driverStatusRepository;
	}

	@Transactional
	public MatchingResult match(double lng, double lat) {
		List<NearbyDriver> candidates = driverLocationRepository.findIdleDriversNear(
				lng, lat, DEFAULT_RADIUS_METERS, DEFAULT_CANDIDATE_LIMIT);

		for (NearbyDriver candidate : candidates) {
			if (tryAssign(candidate.getDriverId())) {
				return new MatchingResult(candidate.getDriverId(), candidate.getDistanceMeters());
			}
		}
		throw new NoAvailableDriverException();
	}

	private boolean tryAssign(Long driverId) {
		DriverStatus status = driverStatusRepository.findById(driverId).orElseThrow();
		if (!status.assign(OffsetDateTime.now())) {
			return false;
		}
		driverStatusRepository.save(status);
		return true;
	}

}
