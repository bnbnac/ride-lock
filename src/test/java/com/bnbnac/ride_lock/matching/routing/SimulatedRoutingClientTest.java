package com.bnbnac.ride_lock.matching.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedRoutingClientTest {

	@Test
	void returnsImmediatelyWhenDelayIsZero() {
		SimulatedRoutingClient client = new SimulatedRoutingClient(0);

		long start = System.nanoTime();
		RouteEstimate estimate = client.estimate(1L);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		assertThat(estimate.etaMillis()).isZero();
		assertThat(elapsedMillis).isLessThan(50);
	}

	@Test
	void sleepsForConfiguredDelayWhenPositive() {
		SimulatedRoutingClient client = new SimulatedRoutingClient(100);

		long start = System.nanoTime();
		RouteEstimate estimate = client.estimate(1L);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		assertThat(estimate.etaMillis()).isEqualTo(100);
		assertThat(elapsedMillis).isGreaterThanOrEqualTo(95);
	}

}
