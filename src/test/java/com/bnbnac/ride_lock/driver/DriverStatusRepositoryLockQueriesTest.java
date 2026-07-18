package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

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
	void compareAndSetAssignedSucceedsOnlyWhenIdleAndVersionMatches() {
		DriverStatus saved = driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));
		OffsetDateTime beforeUpdatedAt = saved.getUpdatedAt();

		int updated = driverStatusRepository.compareAndSetAssigned(1L, saved.getVersion());

		assertThat(updated).isEqualTo(1);
		DriverStatus found = driverStatusRepository.findById(1L).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(DriverState.ASSIGNED);
		assertThat(found.getVersion()).isEqualTo(saved.getVersion() + 1);
		// isAfter 대신 isNotEqualTo를 쓴다 - beforeUpdatedAt은 JVM(호스트) 클럭, found.getUpdatedAt()은
		// Postgres 컨테이너 클럭으로 찍혀서 두 클럭이 어긋나 있으면(Docker 재시작 직후 등) 순서 비교가
		// 플레이키해진다. 여기서 검증하려는 건 "실제로 갱신됐는가"이지 클럭 간 선후관계가 아니다.
		assertThat(found.getUpdatedAt()).isNotEqualTo(beforeUpdatedAt);
	}

	@Test
	void compareAndSetAssignedFailsWhenNotIdle() {
		DriverStatus saved = driverStatusRepository.save(DriverStatus.of(1L, DriverState.ASSIGNED));

		int updated = driverStatusRepository.compareAndSetAssigned(1L, saved.getVersion());

		assertThat(updated).isEqualTo(0);
	}

	@Test
	void compareAndSetAssignedFailsWhenVersionIsStale() {
		DriverStatus saved = driverStatusRepository.save(DriverStatus.of(1L, DriverState.IDLE));

		int updated = driverStatusRepository.compareAndSetAssigned(1L, saved.getVersion() + 1);

		assertThat(updated)
				.as("버전이 실제 값과 다르면 status가 IDLE이어도 갱신되면 안 된다")
				.isEqualTo(0);
		assertThat(driverStatusRepository.findById(1L).orElseThrow().getStatus())
				.isEqualTo(DriverState.IDLE);
	}

}
