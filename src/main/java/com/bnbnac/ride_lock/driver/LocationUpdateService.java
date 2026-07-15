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

	@Transactional
	public void reportLocation(Long driverId, double lng, double lat) {
		driverLocationRepository.upsertLocation(driverId, lng, lat);
		driverStatusRepository.insertIdleIfAbsent(driverId);
	}

}
