package com.jarbis.brokerage.marketdata;

import java.time.Instant;

public final class MarketDataEventTimestamp {

	private MarketDataEventTimestamp() {
	}

	public static Instant resolve(MarketDataEvent event) {
		if (event == null) {
			throw new IllegalArgumentException("Market data event cannot be null");
		}

		Instant timestamp = event.asOf() != null ? event.asOf() : event.capturedAt();
		if (timestamp == null) {
			throw new IllegalArgumentException("Market data event requires asOf or capturedAt");
		}
		return timestamp;
	}
}
