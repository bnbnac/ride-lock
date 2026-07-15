package com.bnbnac.ride_lock.driver;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationUpdateService {

	private final DriverLocationRepository driverLocationRepository;
	private final DriverStatusRepository driverStatusRepository;

	public LocationUpdateService(DriverLocationRepository driverLocationRepository,
			DriverStatusRepository driverStatusRepository) {
		this.driverLocationRepository = driverLocationRepository;
		this.driverStatusRepository = driverStatusRepository;
	}

	// driver_status를 먼저 등록하고 driver_location을 나중에 써야 한다 - 지금은 두 테이블 사이에
	// FK가 없어서 순서 상관없이 동작하지만, findIdleDriversNear가 두 테이블을 JOIN하는 이상
	// "위치가 있으면 상태도 있다"는 불변식은 코드가 지켜야 한다. FK가 나중에 추가돼도 안전한 순서.
	@Transactional
	public void reportLocation(Long driverId, double lng, double lat) {
		driverStatusRepository.insertIdleIfAbsent(driverId);
		driverLocationRepository.upsertLocation(driverId, lng, lat);
	}

}
