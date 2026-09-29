package com.jarbis.brokerage.service;

import com.jarbis.brokerage.client.MarketDataClient;
import com.jarbis.brokerage.entity.Holding;
import com.jarbis.brokerage.entity.Asset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HoldingService. Tests all calculation methods for market
 * value, cost basis, and PnL analytics.
 */
@DisplayName("HoldingService Tests")
class HoldingServiceTest {

	private HoldingService holdingService;

	@Mock
	private MarketDataClient marketDataClient;

	@Mock
	private Asset asset;

	@Mock
	private Holding holding;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		holdingService = new HoldingService(marketDataClient);
	}

	@Nested
	@DisplayName("Market Value Tests")
	class MarketValueTests {

		@Test
		@DisplayName("Should calculate market value as current price times quantity")
		void getMarketValueCalculatesCurrentPriceTimesQuantity() {
			// Arrange
			Double currentPrice = 150.0;
			Double quantity = 10.0;
			when(asset.getPrice()).thenReturn(currentPrice);
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double marketValue = holdingService.getMarketValue(holding);

			// Assert
			assertEquals(1500.0, marketValue);
			verify(marketDataClient).getCurrentPrice(asset);
		}

		@Test
		@DisplayName("Should handle zero quantity in market value calculation")
		void getMarketValueHandlesZeroQuantity() {
			// Arrange
			Double currentPrice = 150.0;
			Double quantity = 0.0;
			when(asset.getPrice()).thenReturn(currentPrice);
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double marketValue = holdingService.getMarketValue(holding);

			// Assert
			assertEquals(0.0, marketValue);
		}

		@Test
		@DisplayName("Should calculate market value with fractional shares")
		void getMarketValueHandlesFractionalShares() {
			// Arrange
			Double currentPrice = 100.0;
			Double quantity = 2.5;
			when(asset.getPrice()).thenReturn(currentPrice);
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double marketValue = holdingService.getMarketValue(holding);

			// Assert
			assertEquals(250.0, marketValue);
		}

		@Test
		@DisplayName("Should calculate market value with high-priced assets")
		void getMarketValueHandlesHighPricedAssets() {
			// Arrange
			Double currentPrice = 5000.0;
			Double quantity = 5.0;
			when(asset.getPrice()).thenReturn(currentPrice);
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double marketValue = holdingService.getMarketValue(holding);

			// Assert
			assertEquals(25000.0, marketValue);
		}
	}

	@Nested
	@DisplayName("Cost Basis Tests")
	class CostBasisTests {

		@Test
		@DisplayName("Should calculate cost basis as average price times quantity")
		void getCostBasisCalculatesAveragePriceTimesQuantity() {
			// Arrange
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);

			// Act
			Double costBasis = holdingService.getCostBasis(holding);

			// Assert
			assertEquals(1500.0, costBasis);
		}

		@Test
		@DisplayName("Should handle zero quantity in cost basis calculation")
		void getCostBasisHandlesZeroQuantity() {
			// Arrange
			Double averagePrice = 150.0;
			Double quantity = 0.0;
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);

			// Act
			Double costBasis = holdingService.getCostBasis(holding);

			// Assert
			assertEquals(0.0, costBasis);
		}

		@Test
		@DisplayName("Should calculate cost basis with fractional shares")
		void getCostBasisHandlesFractionalShares() {
			// Arrange
			Double averagePrice = 100.0;
			Double quantity = 3.5;
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);

			// Act
			Double costBasis = holdingService.getCostBasis(holding);

			// Assert
			assertEquals(350.0, costBasis);
		}

		@Test
		@DisplayName("Should return zero cost basis when average price is zero")
		void getCostBasisReturnZeroWhenAveragePriceIsZero() {
			// Arrange
			Double averagePrice = 0.0;
			Double quantity = 100.0;
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);

			// Act
			Double costBasis = holdingService.getCostBasis(holding);

			// Assert
			assertEquals(0.0, costBasis);
		}
	}

	@Nested
	@DisplayName("Unrealized PnL Tests")
	class UnrealizedPnLTests {

		@Test
		@DisplayName("Should return positive PnL when current price is higher than average price")
		void computeUnrealizedPnLReturnsPositiveWhenProfitable() {
			// Arrange - Bought at 150, now 160, quantity 10
			Double currentPrice = 160.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double pnl = holdingService.computeUnrealizedPnL(holding);

			// Assert - (160-150)*10 = 100
			assertEquals(100.0, pnl);
		}

		@Test
		@DisplayName("Should return negative PnL when current price is lower than average price")
		void computeUnrealizedPnLReturnsNegativeWhenUnprofitable() {
			// Arrange - Bought at 150, now 140, quantity 10
			Double currentPrice = 140.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double pnl = holdingService.computeUnrealizedPnL(holding);

			// Assert - (140-150)*10 = -100
			assertEquals(-100.0, pnl);
		}

		@Test
		@DisplayName("Should return zero PnL when current price equals average price")
		void computeUnrealizedPnLReturnsZeroWhenBreakEven() {
			// Arrange - Bought at 150, now 150
			Double currentPrice = 150.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double pnl = holdingService.computeUnrealizedPnL(holding);

			// Assert
			assertEquals(0.0, pnl);
		}

		@Test
		@DisplayName("Should return zero PnL when quantity is zero")
		void computeUnrealizedPnLReturnsZeroWhenQuantityIsZero() {
			// Arrange
			Double currentPrice = 160.0;
			Double averagePrice = 150.0;
			Double quantity = 0.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double pnl = holdingService.computeUnrealizedPnL(holding);

			// Assert
			assertEquals(0.0, pnl);
		}

		@Test
		@DisplayName("Should handle fractional share PnL calculation")
		void computeUnrealizedPnLHandlesFractionalShares() {
			// Arrange - Bought at 100, now 110, quantity 2.5
			Double currentPrice = 110.0;
			Double averagePrice = 100.0;
			Double quantity = 2.5;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double pnl = holdingService.computeUnrealizedPnL(holding);

			// Assert - (110-100)*2.5 = 25
			assertEquals(25.0, pnl);
		}

		@Test
		@DisplayName("Should handle large PnL values correctly")
		void computeUnrealizedPnLHandlesLargePnLValues() {
			// Arrange - Bought at 100, now 500, quantity 1000
			Double currentPrice = 500.0;
			Double averagePrice = 100.0;
			Double quantity = 1000.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double pnl = holdingService.computeUnrealizedPnL(holding);

			// Assert - (500-100)*1000 = 400000
			assertEquals(400000.0, pnl);
		}
	}

	@Nested
	@DisplayName("PnL Percentage Tests")
	class PnLPercentageTests {

		@Test
		@DisplayName("Should return positive percentage when profitable")
		void computeUnrealizedPnLPercentageReturnsPositivePercentage() {
			// Arrange - Bought at 150, now 160, quantity 10
			// Cost basis = 150*10 = 1500
			// PnL = (160-150)*10 = 100
			// Percentage = (100/1500)*100 ≈ 6.67%
			Double currentPrice = 160.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double percentage = holdingService.computeUnrealizedPnLPercentage(holding);

			// Assert
			assertEquals(6.666666666666667, percentage, 0.01);
		}

		@Test
		@DisplayName("Should return negative percentage when unprofitable")
		void computeUnrealizedPnLPercentageReturnsNegativePercentage() {
			// Arrange - Bought at 150, now 120, quantity 10
			// Cost basis = 150*10 = 1500
			// PnL = (120-150)*10 = -300
			// Percentage = (-300/1500)*100 = -20%
			Double currentPrice = 120.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double percentage = holdingService.computeUnrealizedPnLPercentage(holding);

			// Assert
			assertEquals(-20.0, percentage);
		}

		@Test
		@DisplayName("Should return zero percentage when at break-even")
		void computeUnrealizedPnLPercentageReturnsZeroWhenBreakEven() {
			// Arrange - Bought at 150, now 150
			Double currentPrice = 150.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double percentage = holdingService.computeUnrealizedPnLPercentage(holding);

			// Assert
			assertEquals(0.0, percentage);
		}

		@Test
		@DisplayName("Should return zero when cost basis is zero")
		void computeUnrealizedPnLPercentageReturnsZeroWhenCostBasisIsZero() {
			// Arrange
			Double currentPrice = 100.0;
			Double averagePrice = 0.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double percentage = holdingService.computeUnrealizedPnLPercentage(holding);

			// Assert
			assertEquals(0.0, percentage);
		}

		@Test
		@DisplayName("Should calculate percentage correctly for large gains")
		void computeUnrealizedPnLPercentageHandlesLargeGains() {
			// Arrange - Bought at 100, now 300, quantity 10
			// Cost basis = 100*10 = 1000
			// PnL = (300-100)*10 = 2000
			// Percentage = (2000/1000)*100 = 200%
			Double currentPrice = 300.0;
			Double averagePrice = 100.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double percentage = holdingService.computeUnrealizedPnLPercentage(holding);

			// Assert
			assertEquals(200.0, percentage);
		}

		@Test
		@DisplayName("Should calculate percentage correctly for large losses")
		void computeUnrealizedPnLPercentageHandlesLargeLosses() {
			// Arrange - Bought at 100, now 10, quantity 10
			// Cost basis = 100*10 = 1000
			// PnL = (10-100)*10 = -900
			// Percentage = (-900/1000)*100 = -90%
			Double currentPrice = 10.0;
			Double averagePrice = 100.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			Double percentage = holdingService.computeUnrealizedPnLPercentage(holding);

			// Assert
			assertEquals(-90.0, percentage);
		}
	}

	@Nested
	@DisplayName("Profitability Tests")
	class ProfitabilityTests {

		@Test
		@DisplayName("Should return true when holding is profitable")
		void isProfitableReturnsTrueWhenProfitable() {
			// Arrange - Bought at 150, now 160
			Double currentPrice = 160.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			boolean profitable = holdingService.isProfitable(holding);

			// Assert
			assertTrue(profitable);
		}

		@Test
		@DisplayName("Should return false when holding is unprofitable")
		void isProfitableReturnsFalseWhenUnprofitable() {
			// Arrange - Bought at 150, now 140
			Double currentPrice = 140.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			boolean profitable = holdingService.isProfitable(holding);

			// Assert
			assertFalse(profitable);
		}

		@Test
		@DisplayName("Should return false when holding is at break-even")
		void isProfitableReturnsFalseWhenBreakEven() {
			// Arrange - Bought at 150, now 150
			Double currentPrice = 150.0;
			Double averagePrice = 150.0;
			Double quantity = 10.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			boolean profitable = holdingService.isProfitable(holding);

			// Assert
			assertFalse(profitable);
		}

		@Test
		@DisplayName("Should return false when quantity is zero")
		void isProfitableReturnsFalseWhenQuantityIsZero() {
			// Arrange
			Double currentPrice = 160.0;
			Double averagePrice = 150.0;
			Double quantity = 0.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			boolean profitable = holdingService.isProfitable(holding);

			// Assert
			assertFalse(profitable);
		}

		@Test
		@DisplayName("Should correctly identify profitability with fractional shares")
		void isProfitableHandlesFractionalShares() {
			// Arrange - Bought at 100, now 150, quantity 2.5
			Double currentPrice = 150.0;
			Double averagePrice = 100.0;
			Double quantity = 2.5;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			boolean profitable = holdingService.isProfitable(holding);

			// Assert
			assertTrue(profitable);
		}

		@Test
		@DisplayName("Should correctly identify minimal profitability")
		void isProfitableHandlesMinimalProfit() {
			// Arrange - Bought at 100.00, now 100.01, quantity 1
			Double currentPrice = 100.01;
			Double averagePrice = 100.0;
			Double quantity = 1.0;
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getAveragePrice()).thenReturn(averagePrice);
			when(holding.getQuantity()).thenReturn(quantity);
			when(marketDataClient.getCurrentPrice(asset)).thenReturn(currentPrice);

			// Act
			boolean profitable = holdingService.isProfitable(holding);

			// Assert
			assertTrue(profitable);
		}
	}
}
