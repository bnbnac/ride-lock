package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import com.bnbnac.ride_lock.driver.DriverState;
import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "matching.lock-strategy=optimistic")
@Transactional
class OptimisticLockStrategyTest extends AbstractIntegrationTest {

	@Autowired
	private OptimisticLockStrategy optimisticLockStrategy;

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void tryAssignSucceedsOnceThenFailsOnSecondCall() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));

		assertThat(optimisticLockStrategy.tryAssign(1L)).isTrue();
		assertThat(optimisticLockStrategy.tryAssign(1L)).isFalse();
	}

}
