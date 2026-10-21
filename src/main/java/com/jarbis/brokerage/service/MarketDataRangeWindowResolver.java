package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
public class MarketDataRangeWindowResolver {

	public RangeWindow resolve(MarketHistoryRange range, Instant to) {
		if (range == null) {
			throw new IllegalArgumentException("Market data range is required");
		}

		Instant end = to != null ? to : Instant.now();
		ZonedDateTime endUtc = end.atZone(ZoneOffset.UTC);

		Instant from = switch (range) {
			case ONE_DAY -> end.minusSeconds(24L * 60L * 60L);
			case ONE_WEEK -> end.minusSeconds(7L * 24L * 60L * 60L);
			case ONE_MONTH -> endUtc.minusMonths(1).toInstant();
			case THREE_MONTH -> endUtc.minusMonths(3).toInstant();
			case SIX_MONTH -> endUtc.minusMonths(6).toInstant();
			case YEAR_TO_DATE -> endUtc.withDayOfYear(1)
					.withHour(0)
					.withMinute(0)
					.withSecond(0)
					.withNano(0)
					.toInstant();
			case ONE_YEAR -> endUtc.minusYears(1).toInstant();
			case FIVE_YEAR -> endUtc.minusYears(5).toInstant();
		};

		return new RangeWindow(from, end);
	}

	public record RangeWindow(Instant from, Instant to) {
	}
}