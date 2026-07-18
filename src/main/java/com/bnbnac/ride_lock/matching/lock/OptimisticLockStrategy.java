package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 락 없이 현재 version을 읽은 뒤, 그 version이 그대로일 때만 통과하는 조건부 UPDATE로
// 배정을 시도한다 - 읽기와 쓰기 사이에 다른 트랜잭션이 끼어들어 값을 바꿨으면
// compareAndSetAssigned()의 WHERE절이 걸려 0건 갱신으로 실패한다.
// @Transactional을 안 붙인다 - findById()와 compareAndSetAssigned()는 각각 자체
// @Transactional(REQUIRED)을 갖고 있어 스스로 안전하고, 이 둘을 하나의 물리 트랜잭션으로
// 묶어야 할 원자성 요구도 없다(비관적 락과 달리 여기선 유지해야 할 락이 없다).
@Component
@ConditionalOnProperty(name = "matching.lock-strategy", havingValue = "optimistic")
public class OptimisticLockStrategy implements DriverLockStrategy {

	private final DriverStatusRepository driverStatusRepository;

	public OptimisticLockStrategy(DriverStatusRepository driverStatusRepository) {
		this.driverStatusRepository = driverStatusRepository;
	}

	@Override
	public boolean tryAssign(Long driverId) {
		DriverStatus status = driverStatusRepository.findById(driverId).orElseThrow();
		return driverStatusRepository.compareAndSetAssigned(driverId, status.getVersion()) > 0;
	}

}
