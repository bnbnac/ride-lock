package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
class DriverLocationRepositoryTest extends AbstractIntegrationTest {

	private static final double SEOUL_STATION_LNG = 126.9707;
	private static final double SEOUL_STATION_LAT = 37.5547;

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savesAndLoadsProjectedPointRoundTrippingBackToOriginalLngLat() {
		driverLocationRepository.upsertLocation(1L, SEOUL_STATION_LNG, SEOUL_STATION_LAT);

		DriverLocation found = driverLocationRepository.findById(1L).orElseThrow();
		assertThat(found.getLocation().getSRID()).isEqualTo(5179);

		Object[] roundTripped = (Object[]) entityManager.createNativeQuery(
				"SELECT ST_X(ST_Transform(location, 4326)), ST_Y(ST_Transform(location, 4326)) " +
						"FROM driver_location WHERE driver_id = 1")
				.getSingleResult();

		assertThat((Double) roundTripped[0]).isCloseTo(SEOUL_STATION_LNG, within(1e-6));
		assertThat((Double) roundTripped[1]).isCloseTo(SEOUL_STATION_LAT, within(1e-6));
	}

}
