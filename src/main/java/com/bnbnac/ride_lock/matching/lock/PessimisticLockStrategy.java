package com.bnbnac.ride_lock.matching.lock;

import com.bnbnac.ride_lock.driver.DriverStatus;
import com.bnbnac.ride_lock.driver.DriverStatusRepository;
import com.bnbnac.ride_lock.matching.routing.RoutingClient;
import org.springframework.beans.factory.annotation.Value;
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
//
// routingClient 호출 위치는 matching.routing-delay-inside-lock으로 전환한다 - false(기본,
// before-lock)면 락 획득 전에 불러 대기 없이 경쟁자와 병렬로 겹치고, true(inside-lock)면 FOR
// UPDATE 획득 후~해제 전에 불러 그 시간만큼 경쟁자를 실제로 블로킹시킨다. 후자는 "락을 쥔 채
// 느린 I/O를 한다"는 안티패턴을 의도적으로 재현하기 위한 것이다.
@Component
@ConditionalOnProperty(name = "matching.lock-strategy", havingValue = "pessimistic")
public class PessimisticLockStrategy implements DriverLockStrategy {

	private final DriverStatusRepository driverStatusRepository;
	private final RoutingClient routingClient;
	private final boolean routingInsideLock;

	public PessimisticLockStrategy(DriverStatusRepository driverStatusRepository, RoutingClient routingClient,
			@Value("${matching.routing-delay-inside-lock:false}") boolean routingInsideLock) {
		this.driverStatusRepository = driverStatusRepository;
		this.routingClient = routingClient;
		this.routingInsideLock = routingInsideLock;
	}

	@Override
	@Transactional
	public boolean tryAssign(Long driverId) {
		if (!routingInsideLock) {
			routingClient.estimate(driverId);
		}
		DriverStatus status = driverStatusRepository.findByIdForUpdate(driverId).orElseThrow();
		if (routingInsideLock) {
			routingClient.estimate(driverId);
		}
		if (!status.assign(OffsetDateTime.now())) {
			return false;
		}
		driverStatusRepository.save(status);
		return true;
	}

}
