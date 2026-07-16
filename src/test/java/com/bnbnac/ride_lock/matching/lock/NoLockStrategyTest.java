package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NoLockStrategyTest extends AbstractIntegrationTest {

	@Autowired
	private NoLockStrategy noLockStrategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void tryAssignSucceedsOnceThenFailsOnSecondCall() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));

		assertThat(noLockStrategy.tryAssign(1L)).isTrue();
		assertThat(noLockStrategy.tryAssign(1L)).isFalse();
	}

}
