package com.jarbis.brokerage.client;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import org.springframework.stereotype.Component;

/**
 * Temporary market data client backed by the persisted asset price.
 * This keeps market-value calculations routed through a client bean until
 * an external market data provider is integrated.
 */
@Component
public class StoredPriceMarketDataClient implements MarketDataClient {

    @Override
    public Double getCurrentPrice(Asset asset) {
        if (asset == null) {
            throw new MarketDataUnavailableException("Cannot fetch market data for a null asset");
        }
        if (asset.getPrice() == null) {
            throw new MarketDataUnavailableException(
                    "No stored market price available for asset: " + asset.getName());
        }
        return asset.getPrice();
    }
}
