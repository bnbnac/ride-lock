package com.bnbnac.ride_lock.driver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

@Entity
@Table(name = "driver_location")
public class DriverLocation {

	@Id
	@Column(name = "driver_id")
	private Long driverId;

	// EPSG:5179(한국 투영좌표계, 미터) 좌표다. WGS84 위경도가 아니다 - 위경도는
	// DriverLocationRepository.upsertLocation()에서 쓰기 시점에 ST_Transform으로 변환된다.
	@Column(name = "location", columnDefinition = "geometry(Point,5179)", nullable = false)
	private Point location;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected DriverLocation() {
	}

	public DriverLocation(Long driverId, Point location, OffsetDateTime updatedAt) {
		this.driverId = driverId;
		this.location = location;
		this.updatedAt = updatedAt;
	}

	public Long getDriverId() {
		return driverId;
	}

	public Point getLocation() {
		return location;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
