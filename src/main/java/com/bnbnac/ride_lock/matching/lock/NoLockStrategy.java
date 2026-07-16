package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

// 락 없는 baseline - MatchingRaceConditionTest가 검증하는 레이스 컨디션 재현의 기준점.
// 이전 MatchingService.tryAssign()이 하던 것을 그대로 옮겨왔다.
@Component
@ConditionalOnProperty(name = "matching.lock-strategy", havingValue = "none", matchIfMissing = true)
public class NoLockStrategy implements DriverLockStrategy {

	private final DriverStatusRepository driverStatusRepository;

	public NoLockStrategy(DriverStatusRepository driverStatusRepository) {
		this.driverStatusRepository = driverStatusRepository;
	}

	@Override
	public boolean tryAssign(Long driverId) {
		DriverStatus status = driverStatusRepository.findById(driverId).orElseThrow();
		if (!status.assign(OffsetDateTime.now())) {
			return false;
		}
		driverStatusRepository.save(status);
		return true;
	}

}
