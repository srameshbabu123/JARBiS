package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.AssetType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("STOCK")
public class StockAsset extends Asset {

    public StockAsset() {
        super();
    }

    public StockAsset(String name, Double price) {
        super(name, price);
    }

    @Override
    public AssetType getType() {
        return AssetType.STOCK;
    }
}
