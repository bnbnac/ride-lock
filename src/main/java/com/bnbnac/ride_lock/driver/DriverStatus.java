package com.bnbnac.ride_lock.driver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "driver_status")
public class DriverStatus {

	@Id
	@Column(name = "driver_id")
	private Long driverId;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected DriverStatus() {
	}

	public DriverStatus(Long driverId, String status, Long version, OffsetDateTime updatedAt) {
		this.driverId = driverId;
		this.status = status;
		this.version = version;
		this.updatedAt = updatedAt;
	}

	public Long getDriverId() {
		return driverId;
	}

	public String getStatus() {
		return status;
	}

	public Long getVersion() {
		return version;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
