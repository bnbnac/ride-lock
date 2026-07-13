package com.bnbnac.ride_lock.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {

	@Query(value = """
			SELECT l.driver_id AS driverId,
			       ST_Distance(l.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distanceMeters
			FROM driver_location l
			JOIN driver_status s ON s.driver_id = l.driver_id
			WHERE s.status = 'IDLE'
			  AND ST_DWithin(l.location,
			                 ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
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
