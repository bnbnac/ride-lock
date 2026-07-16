package com.bnbnac.ride_lock.driver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "driver_status")
public class DriverStatus {

	@Id
	@Column(name = "driver_id")
	private Long driverId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private DriverState status;

	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected DriverStatus() {
	}

	public DriverStatus(Long driverId, DriverState status, Long version, OffsetDateTime updatedAt) {
		this.driverId = driverId;
		this.status = status;
		this.version = version;
		this.updatedAt = updatedAt;
	}

	public Long getDriverId() {
		return driverId;
	}

	public DriverState getStatus() {
		return status;
	}

	public Long getVersion() {
		return version;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	// IDLE일 때만 ASSIGNED로 전이시키고 성공 여부를 돌려준다 - 상태 전이 규칙을 엔티티가
	// 직접 지켜서, 호출부가 임의 문자열로 상태를 덮어쓸 수 없게 한다.
	public boolean assign(OffsetDateTime now) {
		if (status != DriverState.IDLE) {
			return false;
		}
		status = DriverState.ASSIGNED;
		updatedAt = now;
		return true;
	}

	// ASSIGNED일 때만 IDLE로 되돌리고 성공 여부를 돌려준다 - 이미 ON_TRIP으로 넘어간 기사를
	// 타임아웃 스케줄러가 실수로 IDLE로 되돌리지 않도록 막는다.
	public boolean release(OffsetDateTime now) {
		if (status != DriverState.ASSIGNED) {
			return false;
		}
		status = DriverState.IDLE;
		updatedAt = now;
		return true;
	}
}
