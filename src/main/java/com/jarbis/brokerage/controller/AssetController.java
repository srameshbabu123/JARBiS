package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.enums.AssetType;
import com.jarbis.brokerage.exception.AssetNotFoundException;
import com.jarbis.brokerage.repository.AssetRepository;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** The UI reads the same asset identities used by the order engine. */
@RestController
@RequestMapping("/assets")
public class AssetController {
    private final AssetRepository assets;

    public AssetController(AssetRepository assets) { this.assets = assets; }

    @GetMapping
    public List<AssetView> list() {
        return assets.findAll().stream().map(AssetView::from).toList();
    }

    @GetMapping("/by-symbol/{symbol}")
    public AssetView bySymbol(@PathVariable String symbol) {
        return assets.findByNameIgnoreCase(symbol.trim())
            .map(AssetView::from)
            .orElseThrow(() -> new AssetNotFoundException("Unknown asset symbol: " + symbol));
    }

    public record AssetView(Long id, String name, AssetType type) {
        static AssetView from(Asset asset) {
            return new AssetView(asset.getId(), asset.getName(), asset.getType());
        }
    }
}
