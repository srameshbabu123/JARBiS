package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.AssetType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ETF")
public class ETFAsset extends Asset {

    public ETFAsset() {}

    public ETFAsset(String name, Double price) {
        super(name, price);
    }

    @Override
    public AssetType getType() {
        return AssetType.ETF;
    }
}
