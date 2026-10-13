package com.jarbis.brokerage.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "brokerage.market-data")
public class MarketDataProperties {

	private String baseUrl;
	private String apiKey;
	private String symbolsFile = "classpath:market-data-symbols.txt";
	private int batchSize = 25;
	private Redis redis = new Redis();

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getSymbolsFile() {
		return symbolsFile;
	}

	public void setSymbolsFile(String symbolsFile) {
		this.symbolsFile = symbolsFile;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public Redis getRedis() {
		return redis;
	}

	public void setRedis(Redis redis) {
		this.redis = redis;
	}

	public static class Redis {

		private String keyPrefix = "market-data:";
		private Duration retentionWindow = Duration.ofDays(7);

		public String getKeyPrefix() {
			return keyPrefix;
		}

		public void setKeyPrefix(String keyPrefix) {
			this.keyPrefix = keyPrefix;
		}

		public Duration getRetentionWindow() {
			return retentionWindow;
		}

		public void setRetentionWindow(Duration retentionWindow) {
			this.retentionWindow = retentionWindow;
		}
	}
}
