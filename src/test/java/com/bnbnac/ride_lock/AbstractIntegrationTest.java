package com.bnbnac.ride_lock;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:14-3.4").asCompatibleSubstituteFor("postgres"));

	static {
		postgres.start();
	}

}
