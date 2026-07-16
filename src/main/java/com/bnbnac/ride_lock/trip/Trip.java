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

	private Trip(Long driverId, TripStatus status, OffsetDateTime assignedAt, OffsetDateTime updatedAt) {
		this.driverId = driverId;
		this.status = status;
		this.assignedAt = assignedAt;
		this.updatedAt = updatedAt;
	}

	// assignedAt/updatedAt이 항상 같은 시각으로 함께 생성되므로, 호출부가 같은 값을
	// 두 번 넘기다 순서를 헷갈리지 않도록 하나로 합친다.
	public static Trip of(Long driverId, TripStatus status, OffsetDateTime at) {
		return new Trip(driverId, status, at, at);
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
	public boolean expire(OffsetDateTime now) {
		if (status != TripStatus.ASSIGNED) {
			return false;
		}
		status = TripStatus.CANCELLED;
		updatedAt = now;
		return true;
	}

}
