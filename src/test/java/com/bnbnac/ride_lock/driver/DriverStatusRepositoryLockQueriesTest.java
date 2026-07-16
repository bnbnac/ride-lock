package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DriverStatusRepositoryLockQueriesTest extends AbstractIntegrationTest {

	@Autowired
	private DriverStatusRepository driverStatusRepository;

	@Test
	void findByIdForUpdateReturnsExistingRow() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));

		assertThat(driverStatusRepository.findByIdForUpdate(1L))
				.get()
				.extracting(DriverStatus::getStatus)
				.isEqualTo(DriverState.IDLE);
	}

	@Test
	void compareAndSetAssignedSucceedsOnlyWhenIdle() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));

		int updated = driverStatusRepository.compareAndSetAssigned(1L);

		assertThat(updated).isEqualTo(1);
		assertThat(driverStatusRepository.findById(1L)).get()
				.extracting(DriverStatus::getStatus).isEqualTo(DriverState.ASSIGNED);
	}

	@Test
	void compareAndSetAssignedFailsWhenNotIdle() {
		driverStatusRepository.save(DriverStatus.of(1L, DriverState.ASSIGNED));

		int updated = driverStatusRepository.compareAndSetAssigned(1L);

		assertThat(updated).isEqualTo(0);
	}

}
