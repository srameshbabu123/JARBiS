package com.jarbis.brokerage.client;

import com.jarbis.brokerage.entity.Asset;

/**
 * Client interface for fetching the latest market data used by the application.
 */
public interface MarketDataClient {

	/**
	 * Get the current market price of an asset.
	 *
	 * @param asset
	 *            the asset to get pricing for
	 * @return the current market price
	 * @throws RuntimeException
	 *             if market data is unavailable
	 */
	Double getCurrentPrice(Asset asset);
}
