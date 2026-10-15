package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.StockAsset;
import com.jarbis.brokerage.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataAssetInitializer Tests")
class MarketDataAssetInitializerTest {

	@Mock
	private AssetRepository assetRepository;

	@Mock
	private MarketDataSymbolProvider symbolProvider;

	private MarketDataAssetInitializer initializer;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		initializer = new MarketDataAssetInitializer(assetRepository, symbolProvider);
	}

	@Test
	@DisplayName("Should create missing stock assets for configured symbols")
	void runCreatesMissingAssets() {
		when(symbolProvider.getSymbols()).thenReturn(List.of("AAPL", "MSFT"));
		when(assetRepository.findByNameIgnoreCase("AAPL")).thenReturn(Optional.empty());
		when(assetRepository.findByNameIgnoreCase("MSFT")).thenReturn(Optional.empty());

		initializer.run(new DefaultApplicationArguments(new String[0]));

		verify(assetRepository, times(2)).save(any(StockAsset.class));
	}

	@Test
	@DisplayName("Should not duplicate existing assets")
	void runDoesNotDuplicateExistingAssets() {
		when(symbolProvider.getSymbols()).thenReturn(List.of("AAPL"));
		when(assetRepository.findByNameIgnoreCase("AAPL")).thenReturn(Optional.of(new StockAsset("AAPL")));

		initializer.run(new DefaultApplicationArguments(new String[0]));

		verify(assetRepository, never()).save(any());
	}
}
