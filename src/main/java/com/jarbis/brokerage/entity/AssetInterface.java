package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.AssetType;

/**
 * Interface defining the contract for all asset types.
 */
public interface AssetInterface {

    String getName();

    Double getPrice();

    AssetType getType();
}
