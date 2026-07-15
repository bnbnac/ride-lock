package com.bnbnac.ride_lock.driver;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drivers")
public class DriverLocationController {

	private final LocationUpdateService locationUpdateService;

	public DriverLocationController(LocationUpdateService locationUpdateService) {
		this.locationUpdateService = locationUpdateService;
	}

	@PutMapping("/{driverId}/location")
	public ResponseEntity<Void> updateLocation(@PathVariable Long driverId,
			@RequestBody LocationUpdateRequest request) {
		locationUpdateService.reportLocation(driverId, request.lng(), request.lat());
		return ResponseEntity.noContent().build();
	}

}
