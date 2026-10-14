package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.StockAsset;
import com.jarbis.brokerage.repository.AssetRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MarketDataAssetInitializer implements ApplicationRunner {

	private final AssetRepository assetRepository;
	private final MarketDataSymbolProvider symbolProvider;

	public MarketDataAssetInitializer(AssetRepository assetRepository, MarketDataSymbolProvider symbolProvider) {
		this.assetRepository = assetRepository;
		this.symbolProvider = symbolProvider;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (String symbol : symbolProvider.getSymbols()) {
			assetRepository.findByNameIgnoreCase(symbol)
					.orElseGet(() -> assetRepository.save(new StockAsset(symbol)));
		}
	}
}
