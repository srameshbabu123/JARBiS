package com.jarbis.brokerage.marketdata;

import java.time.Instant;

public record MarketDataEvent(String symbol, Double price, Double bid, Double ask, Double spreadBps, String currency,
		Double change, Double changePercent, Double previousClose, String source, boolean stale, String marketState,
		Instant asOf, Instant capturedAt) {
}
