package com.bnbnac.ride_lock.trip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class AssignTimeoutScheduler {

	private static final Logger log = LoggerFactory.getLogger(AssignTimeoutScheduler.class);

	private final TripRepository tripRepository;
	private final TripService tripService;
	private final long assignTimeoutSeconds;

	public AssignTimeoutScheduler(TripRepository tripRepository, TripService tripService,
			@Value("${matching.assign-timeout-seconds:30}") long assignTimeoutSeconds) {
		this.tripRepository = tripRepository;
		this.tripService = tripService;
		this.assignTimeoutSeconds = assignTimeoutSeconds;
	}

	// fixedDelayString에 "000"을 이어붙여 초→ms로 바꾸지 않는다 - 프로퍼티가 정수가 아니면
	// (예: 10.5) 문자열 접합 결과가 깨져서 기동 시점에야 실패한다. timeUnit을 SECONDS로 지정해
	// 프로퍼티 값을 그대로 초 단위로 해석하게 한다.
	@Scheduled(fixedDelayString = "${matching.timeout-check-interval-seconds:10}", timeUnit = TimeUnit.SECONDS)
	public void checkExpiredAssignments() {
		OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(assignTimeoutSeconds);
		List<Trip> expired = tripRepository.findByStatusAndAssignedAtBefore(TripStatus.ASSIGNED, cutoff);
		for (Trip trip : expired) {
			try {
				tripService.expire(trip.getId());
			} catch (RuntimeException e) {
				log.warn("Trip {} 만료 처리 실패, 다음 폴링에서 재시도", trip.getId(), e);
			}
		}
	}

}
