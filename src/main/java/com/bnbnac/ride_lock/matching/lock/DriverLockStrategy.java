package com.bnbnac.ride_lock.matching.lock;

// 구현체는 자기 트랜잭션 경계를 스스로 완결해야 한다(호출부 MatchingService.match()는
// 더 이상 @Transactional이 아니므로 트랜잭션을 대신 열어주지 않는다) - FOR UPDATE 등
// row 락을 쓰는 구현체는 이 메서드가 리턴하는 즉시(커밋과 함께) 락이 풀려야 후보별
// 락 경합이 왜곡되지 않는다.
public interface DriverLockStrategy {

	// 실패(false)는 "이 driver는 지금 배정할 수 없음"을 뜻하며 예외를 던지지 않는다.
	// 내부적으로 재시도하지 않는다 - 한 번의 시도만 하고, 다음 후보로 넘어가는 것은
	// 호출부(MatchingService)의 책임이다.
	boolean tryAssign(Long driverId);

}
