package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class MarketDataRedisBackfill implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataRedisBackfill.class);

	private final MarketDataPostgresService postgresService;
	private final RedisMarketDataService redisService;
	private final MarketDataProperties marketDataProperties;

	public MarketDataRedisBackfill(MarketDataPostgresService postgresService, RedisMarketDataService redisService,
			MarketDataProperties marketDataProperties) {
		this.postgresService = postgresService;
		this.redisService = redisService;
		this.marketDataProperties = marketDataProperties;
	}

	@Override
	public void run(ApplicationArguments args) {
		Duration retentionWindow = marketDataProperties.getRedis().getRetentionWindow();
		if (retentionWindow == null || retentionWindow.isZero() || retentionWindow.isNegative()) {
			throw new IllegalStateException("Redis market data retention window must be positive");
		}

		Instant to = Instant.now();
		Instant from = to.minus(retentionWindow);
		List<MarketDataEvent> snapshots = postgresService.findSnapshots(from, to);
		snapshots.forEach(redisService::storeSnapshot);
		LOGGER.info("Redis market data backfill completed: restored {} events", snapshots.size());
	}
}
