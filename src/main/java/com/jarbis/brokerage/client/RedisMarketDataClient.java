package com.jarbis.brokerage.client;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.service.RedisMarketDataHistoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RedisMarketDataClient implements MarketDataClient {

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

		return marketDataHistoryService.getLatest(asset.getName())
				.map(MarketDataEvent::price)
				.orElseThrow(() -> new MarketDataUnavailableException(
						"No current market price available for asset: " + asset.getName()));
	}
}
