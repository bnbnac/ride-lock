package com.bnbnac.ride_lock.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface DriverStatusRepository extends JpaRepository<DriverStatus, Long> {

	// 위치 보고 시 기사가 driver_status에 없으면 IDLE로 등록한다. 이미 있으면(ASSIGNED/ON_TRIP 포함)
	// 절대 덮어쓰지 않는다 - 위치 갱신이 상태 전이를 유발하면 안 되기 때문.
	@Transactional
	@Modifying
	@Query(value = """
			INSERT INTO driver_status (driver_id, status, version, updated_at)
			VALUES (:driverId, 'IDLE', 0, now())
			ON CONFLICT (driver_id) DO NOTHING
			""", nativeQuery = true)
	void insertIdleIfAbsent(@Param("driverId") Long driverId);

	// FOR UPDATE 락은 이 메서드를 감싸는 트랜잭션이 끝날 때까지만 유지된다 - 호출부가 자기
	// 트랜잭션 없이 부르면 즉시 커밋되어 락이 곧바로 풀린다. MANDATORY로 강제해서, 트랜잭션
	// 없이 호출되면 락이 조용히 무력화되는 대신 즉시 예외를 던지게 한다.
	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = "SELECT * FROM driver_status WHERE driver_id = :driverId FOR UPDATE", nativeQuery = true)
	Optional<DriverStatus> findByIdForUpdate(@Param("driverId") Long driverId);

	// version까지 WHERE절에 넣어야 진짜 낙관적 락이다 - status만 비교하면 호출 사이에 버전이
	// 바뀌었어도(예: 다른 트랜잭션이 IDLE로 되돌렸다가 다시 ASSIGNED한 경우) 감지하지 못한다.
	// updated_at은 now()(트랜잭션 시작 시각 고정) 대신 clock_timestamp()(statement 실행 시각)를
	// 쓴다 - 이 메서드를 감싸는 트랜잭션이 길게 이어지면 now()는 실제 갱신 시점보다 앞선
	// 트랜잭션 시작 시각을 반환해 감사(audit) 목적에 안 맞는다.
	@Transactional
	@Modifying(clearAutomatically = true)
	@Query(value = """
			UPDATE driver_status SET status = 'ASSIGNED', version = version + 1, updated_at = clock_timestamp()
			WHERE driver_id = :driverId AND status = 'IDLE' AND version = :version
			""", nativeQuery = true)
	int compareAndSetAssigned(@Param("driverId") Long driverId, @Param("version") Long version);
}
