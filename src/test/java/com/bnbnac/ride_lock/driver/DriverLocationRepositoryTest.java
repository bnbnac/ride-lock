package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
class DriverLocationRepositoryTest extends AbstractIntegrationTest {

	private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

	@Autowired
	private DriverLocationRepository driverLocationRepository;

	@Test
	void savesAndLoadsGeographyPoint() {
		Point seoulStation = GEOMETRY_FACTORY.createPoint(new Coordinate(126.9707, 37.5547));
		DriverLocation saved = driverLocationRepository.save(
				new DriverLocation(1L, seoulStation, OffsetDateTime.now()));

		DriverLocation found = driverLocationRepository.findById(saved.getDriverId()).orElseThrow();

		assertThat(found.getLocation().getX()).isCloseTo(126.9707, within(1e-9));
		assertThat(found.getLocation().getY()).isCloseTo(37.5547, within(1e-9));
		assertThat(found.getLocation().getSRID()).isEqualTo(4326);
	}

}
