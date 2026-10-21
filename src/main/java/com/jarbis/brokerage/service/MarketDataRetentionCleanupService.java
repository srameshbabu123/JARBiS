package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class MarketDataRetentionCleanupService implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataRetentionCleanupService.class);

	private final MarketDataProperties marketDataProperties;
	private final RedisMarketDataService redisMarketDataService;
	private final MarketDataPostgresService marketDataPostgresService;

	public MarketDataRetentionCleanupService(MarketDataProperties marketDataProperties,
			RedisMarketDataService redisMarketDataService,
			MarketDataPostgresService marketDataPostgresService) {
		this.marketDataProperties = marketDataProperties;
		this.redisMarketDataService = redisMarketDataService;
		this.marketDataPostgresService = marketDataPostgresService;
	}

	@Override
	public void run(ApplicationArguments args) {
		cleanupExpiredSnapshots();
	}

	@Scheduled(fixedDelayString = "${brokerage.market-data.cleanup.fixed-delay-ms:3600000}",
			initialDelayString = "${brokerage.market-data.cleanup.initial-delay-ms:300000}")
	public void cleanupExpiredSnapshots() {
		Instant cutoff = cutoff();
		long redisRemoved = redisMarketDataService.purgeSnapshotsBefore(cutoff);
		long postgresRemoved = marketDataPostgresService.purgeSnapshotsBefore(cutoff);

		LOGGER.info("Market data retention cleanup completed: removed {} Redis entries and {} PostgreSQL rows",
				redisRemoved, postgresRemoved);
	}

	private Instant cutoff() {
		Duration retentionWindow = marketDataProperties.getRedis().getRetentionWindow();
		if (retentionWindow == null || retentionWindow.isZero() || retentionWindow.isNegative()) {
			throw new IllegalStateException("Redis market data retention window must be positive");
		}

		return Instant.now().minus(retentionWindow);
	}
}