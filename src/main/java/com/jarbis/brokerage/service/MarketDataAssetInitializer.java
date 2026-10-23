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
		for (MarketDataSymbolProvider.SymbolEntry entry : symbolProvider.getSymbolEntries()) {
			String symbol = entry.symbol();
			String assetName = entry.assetName();

			assetRepository.findByNameIgnoreCase(assetName)
					.or(() -> assetRepository.findByNameIgnoreCase(symbol))
					.ifPresentOrElse(existing -> {
						if (!assetName.equalsIgnoreCase(existing.getName())) {
							existing.setName(assetName);
							assetRepository.save(existing);
						}
					}, () -> assetRepository.save(new StockAsset(assetName)));
		}
	}
}
