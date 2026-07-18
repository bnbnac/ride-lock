package com.bnbnac.ride_lock.matching.lock;

// 호출부(MatchingService)가 후보 1명당 TransactionTemplate으로 트랜잭션을 열어준다 - 성공 시
// 그 안에서 TripService.createTrip()까지 함께 커밋되어야 배정과 Trip 생성이 원자적으로 묶인다.
// 그래서 구현체는 REQUIRES_NEW로 독립 트랜잭션을 새로 여는 대신 REQUIRED(기본값)로 호출부
// 트랜잭션에 합류해야 한다 - REQUIRES_NEW를 쓰면 tryAssign()이 즉시 별도 커밋돼버려서
// 뒤이은 createTrip() 실패 시 배정만 살아남는 상태 불일치가 재발한다.
// FOR UPDATE 등 row 락을 쓰는 구현체는 이 트랜잭션이 끝날 때(실패 시 즉시, 성공 시
// createTrip 커밋과 함께) 락이 풀리므로 실패 후보의 락 보유 시간은 여전히 짧게 유지된다.
public interface DriverLockStrategy {

	// 실패(false)는 "이 driver는 지금 배정할 수 없음"을 뜻하며 예외를 던지지 않는다.
	// 내부적으로 재시도하지 않는다 - 한 번의 시도만 하고, 다음 후보로 넘어가는 것은
	// 호출부(MatchingService)의 책임이다.
	boolean tryAssign(Long driverId);

}
