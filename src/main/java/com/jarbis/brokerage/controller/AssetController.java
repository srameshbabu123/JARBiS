package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.enums.AssetType;
import com.jarbis.brokerage.exception.AssetNotFoundException;
import com.jarbis.brokerage.repository.AssetRepository;
import com.jarbis.brokerage.service.MarketDataSymbolProvider;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.*;

/** The UI reads the same asset identities used by the order engine. */
@RestController
@RequestMapping("/assets")
public class AssetController {
	private static final Pattern TICKER_SUFFIX_PATTERN = Pattern.compile("\\(([A-Z0-9.\\-]{1,20})\\)\\s*$");

    private final AssetRepository assets;
    private final MarketDataSymbolProvider symbolProvider;

    public AssetController(AssetRepository assets, MarketDataSymbolProvider symbolProvider) {
        this.assets = assets;
        this.symbolProvider = symbolProvider;
    }

    @GetMapping
    public List<AssetView> list() {
        Map<String, Integer> symbolOrder = new HashMap<>();
        List<String> orderedSymbols = symbolProvider.getSymbols();
        for (int index = 0; index < orderedSymbols.size(); index++) {
            symbolOrder.put(orderedSymbols.get(index), index);
        }

        return assets.findAll().stream()
                .sorted(Comparator
                        .comparingInt((Asset asset) -> symbolOrder.getOrDefault(extractSymbol(asset.getName()),
                                Integer.MAX_VALUE))
                        .thenComparing(asset -> extractSymbol(asset.getName())))
                .map(AssetView::from)
                .toList();
    }

    @GetMapping("/by-symbol/{symbol}")
    public AssetView bySymbol(@PathVariable String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return assets.findAll().stream()
                .filter(asset -> normalized.equals(extractSymbol(asset.getName())))
                .findFirst()
                .map(AssetView::from)
                .orElseThrow(() -> new AssetNotFoundException("Unknown asset symbol: " + symbol));
    }

    private String extractSymbol(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) {
            return "";
        }

        Matcher matcher = TICKER_SUFFIX_PATTERN.matcher(cleaned.toUpperCase(Locale.ROOT));
        if (matcher.find()) {
            return matcher.group(1);
        }

        return cleaned.toUpperCase(Locale.ROOT);
    }

    public record AssetView(Long id, String name, AssetType type) {
        static AssetView from(Asset asset) {
            return new AssetView(asset.getId(), asset.getName(), asset.getType());
        }
    }
}
