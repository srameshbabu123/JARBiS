package com.jarbis.brokerage.client;

import com.jarbis.brokerage.entity.Asset;

/**
 * Client interface for fetching market data from external providers.
 * Implementations should handle API calls to stock price providers.
 */
public interface MarketDataClient {

    /**
     * Get the current market price of an asset.
     *
     * @param asset the asset to get pricing for
     * @return the current market price
     * @throws RuntimeException if the external API call fails
     */
    Double getCurrentPrice(Asset asset);

    /**
     * Get the current market price by asset ID.
     *
     * @param assetId the ID of the asset
     * @return the current market price
     * @throws RuntimeException if the external API call fails
     */
    Double getCurrentPriceById(Long assetId);
}
