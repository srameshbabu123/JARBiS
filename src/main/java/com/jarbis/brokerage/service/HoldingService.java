package com.jarbis.brokerage.service;

import com.jarbis.brokerage.client.MarketDataClient;
import com.jarbis.brokerage.entity.Holding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service layer for Holding management.
 * Handles holding calculations including unrealized PnL and market value.
 */
@Service
public class HoldingService {

    private final MarketDataClient marketDataClient;

    @Autowired
    public HoldingService(MarketDataClient marketDataClient) {
        this.marketDataClient = marketDataClient;
    }

    // ==================== Market Value ====================

    /**
     * Calculate the market value of a holding (current price * quantity).
     * Fetches current price from external market data provider.
     *
     * @param holding the holding to calculate market value for
     * @return the current market value of the holding
     */
    public Double getMarketValue(Holding holding) {
        Double currentPrice = marketDataClient.getCurrentPrice(holding.getAsset());
        return currentPrice * holding.getQuantity();
    }

    // ==================== Cost Basis ====================

    /**
     * Calculate the cost basis of a holding (average price * quantity).
     * This represents the total amount invested in the holding.
     *
     * @param holding the holding
     * @return the cost basis
     */
    public Double getCostBasis(Holding holding) {
        return holding.getAveragePrice() * holding.getQuantity();
    }

    // ==================== Unrealized PnL ====================

    /**
     * Calculate unrealized profit and loss for a holding.
     * Formula: (Current Price - Average Price) * Quantity
     *
     * @param holding the holding
     * @return the unrealized PnL (positive = profit, negative = loss)
     */
    public Double computeUnrealizedPnL(Holding holding) {
        Double currentPrice = marketDataClient.getCurrentPrice(holding.getAsset());
        return (currentPrice - holding.getAveragePrice()) * holding.getQuantity();
    }

    /**
     * Calculate unrealized PnL as a percentage of cost basis.
     * Formula: (Unrealized PnL / Cost Basis) * 100
     *
     * @param holding the holding
     * @return the unrealized PnL percentage
     */
    public Double computeUnrealizedPnLPercentage(Holding holding) {
        Double costBasis = getCostBasis(holding);
        if (costBasis == 0) {
            return 0.0;
        }
        Double unrealizedPnL = computeUnrealizedPnL(holding);
        return (unrealizedPnL / costBasis) * 100;
    }

    /**
     * Check if the holding is profitable (unrealized PnL > 0).
     *
     * @param holding the holding
     * @return true if profitable, false otherwise
     */
    public boolean isProfitable(Holding holding) {
        return computeUnrealizedPnL(holding) > 0;
    }

}
