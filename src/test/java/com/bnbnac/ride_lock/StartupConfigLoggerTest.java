package com.bnbnac.ride_lock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StartupConfigLoggerTest {

	private ListAppender<ILoggingEvent> appender;
	private Logger logbackLogger;

	@BeforeEach
	void attachAppender() {
		logbackLogger = (Logger) LoggerFactory.getLogger(StartupConfigLogger.class);
		appender = new ListAppender<>();
		appender.start();
		logbackLogger.addAppender(appender);
	}

	@AfterEach
	void detachAppender() {
		logbackLogger.detachAppender(appender);
	}

	@Test
	void logsEffectiveVirtualThreadsSetting() {
		HikariDataSource dataSource = mock(HikariDataSource.class);
		when(dataSource.getMaximumPoolSize()).thenReturn(10);
		StartupConfigLogger logger = new StartupConfigLogger(dataSource, "redis", 5000, 20, true, 0, false);

		logger.logEffectiveConfig();

		assertThat(appender.list)
				.extracting(ILoggingEvent::getFormattedMessage)
				.anyMatch(message -> message.contains("spring.threads.virtual.enabled=true"));
	}

	@Test
	void logsFalseWhenVirtualThreadsDisabled() {
		HikariDataSource dataSource = mock(HikariDataSource.class);
		when(dataSource.getMaximumPoolSize()).thenReturn(10);
		StartupConfigLogger logger = new StartupConfigLogger(dataSource, "pessimistic", 5000, 20, false, 0, false);

		logger.logEffectiveConfig();

		assertThat(appender.list)
				.extracting(ILoggingEvent::getFormattedMessage)
				.anyMatch(message -> message.contains("spring.threads.virtual.enabled=false"));
	}

	@Test
	void logsRoutingDelaySettings() {
		HikariDataSource dataSource = mock(HikariDataSource.class);
		when(dataSource.getMaximumPoolSize()).thenReturn(10);
		StartupConfigLogger logger = new StartupConfigLogger(dataSource, "pessimistic", 5000, 20, false, 300, true);

		logger.logEffectiveConfig();

		assertThat(appender.list)
				.extracting(ILoggingEvent::getFormattedMessage)
				.anyMatch(message -> message.contains("matching.routing-delay-ms=300")
						&& message.contains("matching.routing-delay-inside-lock=true"));
	}

}
