package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.AssetType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CRYPTOCURRENCY")
public class CryptoAsset extends Asset {

    public CryptoAsset() {}

    public CryptoAsset(String name, double price) {
        super(name, (double) price);
    }

    @Override
    public AssetType getType() {
        return AssetType.CRYPTOCURRENCY;
    }
}
