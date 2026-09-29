package com.jarbis.brokerage.dto.request;

public class HoldingRequestDto {

	private Long accountId;
	private Long assetId;
	private Double quantity;
	private Double averagePrice;

	public HoldingRequestDto() {
	}

	public HoldingRequestDto(Long accountId, Long assetId, Double quantity, Double averagePrice) {
		this.accountId = accountId;
		this.assetId = assetId;
		this.quantity = quantity;
		this.averagePrice = averagePrice;
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
	}

	public Long getAssetId() {
		return assetId;
	}

	public void setAssetId(Long assetId) {
		this.assetId = assetId;
	}

	public Double getQuantity() {
		return quantity;
	}

	public void setQuantity(Double quantity) {
		this.quantity = quantity;
	}

	public Double getAveragePrice() {
		return averagePrice;
	}

	public void setAveragePrice(Double averagePrice) {
		this.averagePrice = averagePrice;
	}
}
