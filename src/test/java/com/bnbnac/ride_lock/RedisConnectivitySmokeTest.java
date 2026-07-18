package com.bnbnac.ride_lock;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// redisson-spring-boot-starter가 Testcontainers @ServiceConnection을 인식해서 RedissonClient가
// 컨테이너에 실제로 연결되는지 확인하는 최소 확인 테스트 - 이게 실패하면 RedissonLockStrategy를
// 만들기 전에 연결 설정부터 고쳐야 한다.
@SpringBootTest
class RedisConnectivitySmokeTest extends AbstractRedisIntegrationTest {

	@Autowired
	private RedissonClient redissonClient;

	@Test
	void canSetAndGetAValueAgainstTheContainer() {
		redissonClient.getBucket("smoke-test-key").set("ok");

		assertThat(redissonClient.getBucket("smoke-test-key").get()).isEqualTo("ok");
	}

}
