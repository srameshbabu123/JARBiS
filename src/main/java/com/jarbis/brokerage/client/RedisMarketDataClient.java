package com.jarbis.brokerage.client;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.service.RedisMarketDataHistoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RedisMarketDataClient implements MarketDataClient {

	private static final Pattern SYMBOL_SUFFIX_PATTERN = Pattern.compile(".*\\(([A-Za-z0-9.\\-]+)\\)\\s*$");

	private final RedisMarketDataHistoryService marketDataHistoryService;

	public RedisMarketDataClient(RedisMarketDataHistoryService marketDataHistoryService) {
		this.marketDataHistoryService = marketDataHistoryService;
	}

	@Override
	public Double getCurrentPrice(Asset asset) {
		if (asset == null) {
			throw new MarketDataUnavailableException("Cannot fetch market data for a null asset");
		}
		if (!StringUtils.hasText(asset.getName())) {
			throw new MarketDataUnavailableException("Asset does not have a market-data symbol: " + asset.getName());
		}

		String symbol = resolveSymbol(asset.getName());

		return marketDataHistoryService.getLatest(symbol)
				.map(MarketDataEvent::price)
				.orElseThrow(() -> new MarketDataUnavailableException(
						"No current market price available for asset: " + asset.getName()));
	}

	private String resolveSymbol(String assetName) {
		String cleaned = assetName.trim();
		Matcher matcher = SYMBOL_SUFFIX_PATTERN.matcher(cleaned);
		if (matcher.matches() && StringUtils.hasText(matcher.group(1))) {
			return matcher.group(1);
		}
		return cleaned;
	}
}
