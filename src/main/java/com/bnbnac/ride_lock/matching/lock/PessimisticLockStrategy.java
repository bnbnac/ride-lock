package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

// 이 메서드가 트랜잭션 경계를 직접 소유하지 않는다 - MatchingService가 후보 1명당
// TransactionTemplate으로 이미 열어둔 트랜잭션 안에서 호출하므로, 아래 @Transactional은
// REQUIRED(기본값)로 그 트랜잭션에 합류할 뿐이다. 실패 후보의 FOR UPDATE 락이 짧게 유지되는
// 이유는 MatchingService 쪽 트랜잭션 스코프가 후보 1명당으로 좁혀져 있기 때문
// (DriverLockStrategy 인터페이스 주석 참고). @Transactional이 실제로 하는 일은, 트랜잭션 없이
// 직접 호출되는 경우(PessimisticLockConcurrencyTest 등) findByIdForUpdate()의
// Propagation.MANDATORY 요구를 충족시키기 위해 트랜잭션을 새로 여는 것뿐이다.
@Component
@ConditionalOnProperty(name = "matching.lock-strategy", havingValue = "pessimistic")
public class PessimisticLockStrategy implements DriverLockStrategy {

	private final DriverStatusRepository driverStatusRepository;

	public PessimisticLockStrategy(DriverStatusRepository driverStatusRepository) {
		this.driverStatusRepository = driverStatusRepository;
	}

	@Override
	@Transactional
	public boolean tryAssign(Long driverId) {
		DriverStatus status = driverStatusRepository.findByIdForUpdate(driverId).orElseThrow();
		if (!status.assign(OffsetDateTime.now())) {
			return false;
		}
		driverStatusRepository.save(status);
		return true;
	}

}
