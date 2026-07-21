package com.bnbnac.ride_lock.matching.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 실제 라우팅 API 왕복시간을 흉내내는 스텁 - matching.routing-delay-ms=0(기본)이면 기존
// 실험(3주차 부하테스트)과 동일하게 지연 없이 즉시 반환해 재현성을 보존한다.
@Component
public class SimulatedRoutingClient implements RoutingClient {

	private final long delayMillis;

	public SimulatedRoutingClient(@Value("${matching.routing-delay-ms:0}") long delayMillis) {
		this.delayMillis = delayMillis;
	}

	@Override
	public RouteEstimate estimate(Long driverId) {
		if (delayMillis > 0) {
			try {
				Thread.sleep(delayMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("라우팅 시뮬레이션 지연 중 인터럽트됨", e);
			}
		}
		return new RouteEstimate(delayMillis);
	}

}
