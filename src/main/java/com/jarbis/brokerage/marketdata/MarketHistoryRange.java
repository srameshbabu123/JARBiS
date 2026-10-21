package com.jarbis.brokerage.marketdata;

import java.util.Arrays;

public enum MarketHistoryRange {

	ONE_DAY("1d"),
	ONE_WEEK("1w"),
	ONE_MONTH("1m"),
	THREE_MONTH("3m"),
	SIX_MONTH("6m"),
	YEAR_TO_DATE("ytd"),
	ONE_YEAR("1y"),
	FIVE_YEAR("5y");

	private final String token;

	MarketHistoryRange(String token) {
		this.token = token;
	}

	public String token() {
		return token;
	}

	public boolean isRedisBackedRange() {
		return this == ONE_DAY || this == ONE_WEEK;
	}

	public static MarketHistoryRange parse(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Market data range is required");
		}

		return Arrays.stream(values())
				.filter(range -> range.token.equalsIgnoreCase(value.trim()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Unsupported market data range: " + value + ". Supported ranges: 1d, 1w, 1m, 3m, 6m, ytd, 1y, 5y"));
	}
}