package com.bnbnac.ride_lock.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

	List<Trip> findByStatusAndAssignedAtBefore(TripStatus status, OffsetDateTime cutoff);

	List<Trip> findByDriverId(Long driverId);

	// SKIP LOCKED: 여러 인스턴스가 동시에 만료 폴링을 돌릴 때, 이미 다른 인스턴스가 처리 중인
	// 트립(잠긴 row)은 기다리지 않고 건너뛴다 - 대기했다가 중복으로 expire()를 재시도해서
	// 헛쓰기+경고 로그를 만드는 대신, 애초에 그 트립을 못 본 것처럼 빈 결과를 돌려준다.
	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = "SELECT * FROM trip WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
	Optional<Trip> findByIdForUpdateSkipLocked(@Param("id") Long id);

}
