package com.bnbnac.ride_lock;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

// 3주차 부하테스트에서 실행마다 바꿔가며 비교할 값(락 전략, 스레드 모델, 커넥션 풀 크기,
// 핫존 후보 반경/인원수)을 기동 완료 시점에 한 줄로 찍는다. @Value("${...:기본값}") 플레이스홀더는
// 프로퍼티 키를 오타내도 에러 없이 조용히 기본값으로 떨어지므로, 로그로 실제 적용된 값을 눈으로
// 확인하지 않으면 오타 한 번으로 그 실행의 측정 전체가 의도한 조건과 다른 채 조용히 무효가 될 수 있다.
@Component
public class StartupConfigLogger {

	private static final Logger log = LoggerFactory.getLogger(StartupConfigLogger.class);

	private final DataSource dataSource;
	private final String lockStrategy;
	private final double candidateRadiusMeters;
	private final int candidateLimit;
	private final boolean virtualThreadsEnabled;

	public StartupConfigLogger(DataSource dataSource,
			@Value("${matching.lock-strategy:none}") String lockStrategy,
			@Value("${matching.candidate-radius-meters:5000}") double candidateRadiusMeters,
			@Value("${matching.candidate-limit:20}") int candidateLimit,
			@Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled) {
		this.dataSource = dataSource;
		this.lockStrategy = lockStrategy;
		this.candidateRadiusMeters = candidateRadiusMeters;
		this.candidateLimit = candidateLimit;
		this.virtualThreadsEnabled = virtualThreadsEnabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void logEffectiveConfig() {
		int maximumPoolSize = dataSource instanceof HikariDataSource hikari ? hikari.getMaximumPoolSize() : -1;
		log.info("실행 설정 확인 - matching.lock-strategy={}, spring.threads.virtual.enabled={}, "
						+ "hikari.maximum-pool-size={}, matching.candidate-radius-meters={}, matching.candidate-limit={}",
				lockStrategy, virtualThreadsEnabled, maximumPoolSize, candidateRadiusMeters, candidateLimit);
	}

}
