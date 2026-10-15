package com.jarbis.brokerage.marketdata;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class MarketDataRedisKey {

	private MarketDataRedisKey() {
	}

	public static String forSymbol(String keyPrefix, String symbol) {
		return keyPrefix + normalizeSymbol(symbol);
	}

	public static String normalizeSymbol(String symbol) {
		if (!StringUtils.hasText(symbol)) {
			throw new IllegalArgumentException("Market data symbol cannot be blank");
		}
		return symbol.trim().toUpperCase(Locale.ROOT);
	}
}
