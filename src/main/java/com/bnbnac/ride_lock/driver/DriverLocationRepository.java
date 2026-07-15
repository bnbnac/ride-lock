package com.bnbnac.ride_lock.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {

	// WGS84 위경도를 받아 EPSG:5179로 투영해서 저장한다. 컬럼 자체는 캐스팅/변환 없이 저장되므로
	// location 위의 GiST 인덱스가 이후 읽기 쿼리에서 그대로 쓰인다.
	// save()/deleteById()와 달리 커스텀 @Modifying 쿼리는 호출자 트랜잭션에 자동으로 안 얹히므로,
	// 클래스 레벨 @Transactional이 없는 호출부(MatchingRaceConditionTest 등)를 위해 직접 선언한다.
	@Transactional
	@Modifying
	@Query(value = """
			INSERT INTO driver_location (driver_id, location, updated_at)
			VALUES (:driverId, ST_Transform(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 5179), now())
			ON CONFLICT (driver_id) DO UPDATE
			    SET location = EXCLUDED.location,
			        updated_at = EXCLUDED.updated_at
			""", nativeQuery = true)
	void upsertLocation(@Param("driverId") Long driverId, @Param("lng") double lng, @Param("lat") double lat);

	@Query(value = """
			SELECT l.driver_id AS driverId,
			       ST_Distance(l.location, ST_Transform(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 5179)) AS distanceMeters
			FROM driver_location l
			JOIN driver_status s ON s.driver_id = l.driver_id
			WHERE s.status = 'IDLE'
			  AND ST_DWithin(l.location,
			                 ST_Transform(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 5179),
			                 :radiusMeters)
			ORDER BY distanceMeters
			LIMIT :limit
			""", nativeQuery = true)
	List<NearbyDriver> findIdleDriversNear(
			@Param("lng") double lng,
			@Param("lat") double lat,
			@Param("radiusMeters") double radiusMeters,
			@Param("limit") int limit);

}
