package com.jarbis.brokerage.dto.response;

public class HoldingResponseDto {

	private Long id;
	private Long accountId;
	private Long assetId;
	private String assetName;
	private Double quantity;
	private Double averagePrice;
	private Double marketValue;
	private Double costBasis;
	private Double unrealizedPnL;
	private Double unrealizedPnLPercentage;
	private Boolean isProfitable;

	public HoldingResponseDto() {
	}

	public HoldingResponseDto(Long id, Long accountId, Long assetId, String assetName, Double quantity,
			Double averagePrice, Double marketValue, Double costBasis, Double unrealizedPnL,
			Double unrealizedPnLPercentage, Boolean isProfitable) {
		this.id = id;
		this.accountId = accountId;
		this.assetId = assetId;
		this.assetName = assetName;
		this.quantity = quantity;
		this.averagePrice = averagePrice;
		this.marketValue = marketValue;
		this.costBasis = costBasis;
		this.unrealizedPnL = unrealizedPnL;
		this.unrealizedPnLPercentage = unrealizedPnLPercentage;
		this.isProfitable = isProfitable;
	}

	public Long getId() {
		return id;
	}

	public Long getAccountId() {
		return accountId;
	}

	public Long getAssetId() {
		return assetId;
	}

	public String getAssetName() {
		return assetName;
	}

	public Double getQuantity() {
		return quantity;
	}

	public Double getAveragePrice() {
		return averagePrice;
	}

	public Double getMarketValue() {
		return marketValue;
	}

	public Double getCostBasis() {
		return costBasis;
	}

	public Double getUnrealizedPnL() {
		return unrealizedPnL;
	}

	public Double getUnrealizedPnLPercentage() {
		return unrealizedPnLPercentage;
	}

	public Boolean getIsProfitable() {
		return isProfitable;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
	}

	public void setAssetId(Long assetId) {
		this.assetId = assetId;
	}

	public void setAssetName(String assetName) {
		this.assetName = assetName;
	}

	public void setQuantity(Double quantity) {
		this.quantity = quantity;
	}

	public void setAveragePrice(Double averagePrice) {
		this.averagePrice = averagePrice;
	}

	public void setMarketValue(Double marketValue) {
		this.marketValue = marketValue;
	}

	public void setCostBasis(Double costBasis) {
		this.costBasis = costBasis;
	}

	public void setUnrealizedPnL(Double unrealizedPnL) {
		this.unrealizedPnL = unrealizedPnL;
	}

	public void setUnrealizedPnLPercentage(Double unrealizedPnLPercentage) {
		this.unrealizedPnLPercentage = unrealizedPnLPercentage;
	}

	public void setIsProfitable(Boolean isProfitable) {
		this.isProfitable = isProfitable;
	}
}
