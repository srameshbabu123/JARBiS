package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataEvent;
import org.springframework.stereotype.Service;

@Service
public class MarketDataPersistenceService {

	private final MarketDataPostgresService postgresService;
	private final RedisMarketDataService redisService;

	public MarketDataPersistenceService(MarketDataPostgresService postgresService,
			RedisMarketDataService redisService) {
		this.postgresService = postgresService;
		this.redisService = redisService;
	}

	public void persist(MarketDataEvent event) {
		postgresService.storeSnapshot(event);
		redisService.storeSnapshot(event);
	}
}
