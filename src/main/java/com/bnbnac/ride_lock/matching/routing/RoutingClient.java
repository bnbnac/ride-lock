package com.bnbnac.ride_lock.matching.routing;

// 실제 라우팅 엔진(OSRM 등, §6 스코프 밖) 연동 시 이 인터페이스만 새 구현체로 교체하면 된다.
// 지금은 SimulatedRoutingClient가 지연만 흉내내는 유일한 구현체다. 반환값(RouteEstimate)은
// 현재 어느 호출부에서도 분기에 쓰이지 않는다 - 실제 라우팅이 붙으면 "경로 없음/너무 멂 -> 후보
// 포기" 분기가 필요해지겠지만, 그 분기 로직은 락 보유 시간 실험이라는 지금 스코프 밖이다.
public interface RoutingClient {

	RouteEstimate estimate(Long driverId);

}
