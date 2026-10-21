package com.jarbis.brokerage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataEventTimestamp;
import com.jarbis.brokerage.marketdata.MarketDataProperties;
import com.jarbis.brokerage.marketdata.MarketDataRedisKey;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class RedisMarketDataService {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final MarketDataProperties marketDataProperties;

	public RedisMarketDataService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
			MarketDataProperties marketDataProperties) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.marketDataProperties = marketDataProperties;
	}

	public void storeSnapshot(MarketDataEvent event) {
		if (event == null) {
			throw new IllegalArgumentException("Market data event cannot be null");
		}
		if (!StringUtils.hasText(event.symbol())) {
			throw new IllegalArgumentException("Market data event symbol cannot be blank");
		}

		try {
			String key = getKey(event.symbol());
			String payload = objectMapper.writeValueAsString(event);
			double score = MarketDataEventTimestamp.resolve(event).toEpochMilli();

			redisTemplate.opsForZSet().add(key, payload, score);
			removeEntriesOutsideRetentionWindow(key);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize market data event for Redis", exception);
		}
	}

	private void removeEntriesOutsideRetentionWindow(String key) {
		Duration retentionWindow = marketDataProperties.getRedis().getRetentionWindow();
		if (retentionWindow == null || retentionWindow.isZero() || retentionWindow.isNegative()) {
			throw new IllegalStateException("Redis market data retention window must be positive");
		}

		long cutoff = Instant.now().minus(retentionWindow).toEpochMilli();
		redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff - 1);
	}

	public long purgeSnapshotsBefore(Instant cutoff) {
		if (cutoff == null) {
			throw new IllegalArgumentException("Market data cleanup cutoff must not be null");
		}

		Set<String> keys = redisTemplate.keys(marketDataProperties.getRedis().getKeyPrefix() + "*");
		if (keys == null || keys.isEmpty()) {
			return 0L;
		}

		long removed = 0L;
		double maxScore = cutoff.toEpochMilli() - 1;
		for (String key : keys) {
			if (key == null || isCandlesCacheKey(key)) {
				continue;
			}
			if (redisTemplate.type(key) != DataType.ZSET) {
				continue;
			}

			Long deleted = redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, maxScore);
			removed += deleted != null ? deleted : 0L;
		}

		return removed;
	}

	private String getKey(String symbol) {
		return MarketDataRedisKey.forSymbol(marketDataProperties.getRedis().getKeyPrefix(), symbol);
	}

	private boolean isCandlesCacheKey(String key) {
		return key.startsWith("market-data:candles:");
	}

}
