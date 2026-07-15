package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DriverLocationRadiusSearchTest extends AbstractIntegrationTest {

	private static final double ORIGIN_LNG = 126.9707;
	private static final double ORIGIN_LAT = 37.5547;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void returnsOnlyIdleDriversWithinRadiusOrderedByDistance() {
		// 반경 5km 이내, IDLE -> 후보에 포함
		saveDriver(1L, "IDLE", ORIGIN_LNG + 0.001, ORIGIN_LAT);
		// 반경 5km 이내지만 ASSIGNED -> 상태 필터로 제외
		saveDriver(2L, "ASSIGNED", ORIGIN_LNG + 0.002, ORIGIN_LAT);
		// 반경 5km 밖, IDLE -> 거리 필터로 제외
		saveDriver(3L, "IDLE", ORIGIN_LNG, ORIGIN_LAT + 0.1);
		// 반경 5km 이내, IDLE, 1번보다 멀리 -> 후보에 포함, 순서상 뒤
		saveDriver(4L, "IDLE", ORIGIN_LNG + 0.02, ORIGIN_LAT);

		List<NearbyDriver> result = driverLocationRepository.findIdleDriversNear(
				ORIGIN_LNG, ORIGIN_LAT, 5000, 20);

		assertThat(result).extracting(NearbyDriver::getDriverId)
				.containsExactly(1L, 4L);
		assertThat(result.get(0).getDistanceMeters())
				.isLessThan(result.get(1).getDistanceMeters());
	}

	private void saveDriver(Long driverId, String status, double lng, double lat) {
		driverStatusRepository.save(new DriverStatus(driverId, status, 0L, OffsetDateTime.now()));
		driverLocationRepository.upsertLocation(driverId, lng, lat);
	}

}
