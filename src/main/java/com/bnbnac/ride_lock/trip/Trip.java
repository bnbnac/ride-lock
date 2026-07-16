package com.bnbnac.ride_lock.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "trip")
public class Trip {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "driver_id", nullable = false)
	private Long driverId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private TripStatus status;

	@Column(name = "assigned_at", nullable = false)
	private OffsetDateTime assignedAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Trip() {
	}

	public Trip(Long driverId, TripStatus status, OffsetDateTime assignedAt, OffsetDateTime updatedAt) {
		this.driverId = driverId;
		this.status = status;
		this.assignedAt = assignedAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getDriverId() {
		return driverId;
	}

	public TripStatus getStatus() {
		return status;
	}

	public OffsetDateTime getAssignedAt() {
		return assignedAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	// ASSIGNED일 때만 CANCELLED로 전이시키고 성공 여부를 돌려준다 - 스케줄러가 조회한 시점과
	// 실제 갱신 시점 사이에 이미 다른 경로로 상태가 바뀌었을 가능성을 엔티티가 직접 막는다.
	public boolean expire() {
		if (status != TripStatus.ASSIGNED) {
			return false;
		}
		status = TripStatus.CANCELLED;
		return true;
	}

}
