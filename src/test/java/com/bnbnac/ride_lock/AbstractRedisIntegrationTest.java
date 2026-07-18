package com.bnbnac.ride_lock;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.utility.DockerImageName;

// Redis가 필요한 테스트만 이 클래스를 상속한다 - 모든 통합 테스트에 컨테이너를 강제하면
// Redis를 안 쓰는 테스트까지 느려진다.
public abstract class AbstractRedisIntegrationTest extends AbstractIntegrationTest {

	@ServiceConnection
	static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

	static {
		redis.start();
	}

}
