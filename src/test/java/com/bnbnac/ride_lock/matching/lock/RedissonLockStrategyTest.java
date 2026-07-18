package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.AbstractRedisIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "matching.lock-strategy=redis")
class RedissonLockStrategyTest extends AbstractRedisIntegrationTest {

	private static final long DRIVER_ID = 1L;

	@Autowired
	private RedissonLockStrategy redissonLockStrategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@AfterEach
	void cleanUp() {
		driverStatusRepository.deleteById(DRIVER_ID);
	}

	@Test
	void tryAssignSucceedsOnceThenFailsOnSecondCall() {
		driverStatusRepository.save(DriverStatus.of(DRIVER_ID, DriverState.IDLE));

		assertThat(redissonLockStrategy.tryAssign(DRIVER_ID)).isTrue();
		assertThat(redissonLockStrategy.tryAssign(DRIVER_ID)).isFalse();
	}

}
