package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.matching.routing.RoutingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 락 없이 현재 version을 읽은 뒤, 그 version이 그대로일 때만 통과하는 조건부 UPDATE로
// 배정을 시도한다 - 읽기와 쓰기 사이에 다른 트랜잭션이 끼어들어 값을 바꿨으면
// compareAndSetAssigned()의 WHERE절이 걸려 0건 갱신으로 실패한다.
// @Transactional을 안 붙인다 - findById()와 compareAndSetAssigned()는 각각 자체
// @Transactional(REQUIRED)을 갖고 있어 스스로 안전하고, 이 둘을 하나의 물리 트랜잭션으로
// 묶어야 할 원자성 요구도 없다(비관적 락과 달리 여기선 유지해야 할 락이 없다).
//
// routingClient는 비관적/Redis와 달리 배치 선택지가 없다 - 보유할 락 자체가 없어서 "락 전/후"라는
// 구분이 성립하지 않는다. 대신 버전을 읽은 직후~CAS 시도 직전, 유일하게 의미 있는 자리에 고정된다.
// 이 창이 길어질수록 그 사이 다른 트랜잭션이 값을 바꿨을 확률이 커지는데, 낙관적 락은 그 창을
// 막아줄 수단이 없다 - 느린 검증 단계와 낙관적 락을 같이 쓰면 실패율(재시도 필요)이 커진다는
// 실무 트레이드오프를 그대로 재현한다.
@Component
@ConditionalOnProperty(name = "matching.lock-strategy", havingValue = "optimistic")
public class OptimisticLockStrategy implements DriverLockStrategy {

	private final DriverStatusRepository driverStatusRepository;
	private final RoutingClient routingClient;

	public OptimisticLockStrategy(DriverStatusRepository driverStatusRepository, RoutingClient routingClient) {
		this.driverStatusRepository = driverStatusRepository;
		this.routingClient = routingClient;
	}

	@Override
	public boolean tryAssign(Long driverId) {
		DriverStatus status = driverStatusRepository.findById(driverId).orElseThrow();
		routingClient.estimate(driverId);
		return driverStatusRepository.compareAndSetAssigned(driverId, status.getVersion()) > 0;
	}

}
