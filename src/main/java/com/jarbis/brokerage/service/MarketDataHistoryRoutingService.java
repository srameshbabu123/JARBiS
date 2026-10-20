package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MarketDataHistoryRoutingService {

	private final RedisMarketDataHistoryService redisHistoryService;
	private final MarketDataCandlesHistoryService candlesHistoryService;
	private final MarketDataRangeWindowResolver rangeWindowResolver;

	public MarketDataHistoryRoutingService(RedisMarketDataHistoryService redisHistoryService,
			MarketDataCandlesHistoryService candlesHistoryService,
			MarketDataRangeWindowResolver rangeWindowResolver) {
		this.redisHistoryService = redisHistoryService;
		this.candlesHistoryService = candlesHistoryService;
		this.rangeWindowResolver = rangeWindowResolver;
	}

	public List<MarketDataEvent> getHistory(String symbol, MarketHistoryRange range, Instant to) {
		var window = rangeWindowResolver.resolve(range, to);
		if (range.isRedisBackedRange()) {
			return redisHistoryService.getHistory(symbol, window.from(), window.to());
		}

		return candlesHistoryService.getHistory(symbol, range, window.from(), window.to());
	}
}