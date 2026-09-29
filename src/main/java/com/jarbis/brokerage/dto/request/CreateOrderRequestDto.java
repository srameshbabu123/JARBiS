package com.jarbis.brokerage.dto.request;

import com.jarbis.brokerage.enums.OrderSide;

public class CreateOrderRequestDto {

	private Long accountId;
	private Long assetId;
	private OrderSide side;
	private Double quantity;
	private Double price;

	public CreateOrderRequestDto() {
	}

	public CreateOrderRequestDto(Long accountId, Long assetId, OrderSide side, Double quantity, Double price) {
		this.accountId = accountId;
		this.assetId = assetId;
		this.side = side;
		this.quantity = quantity;
		this.price = price;
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

	public OrderSide getSide() {
		return side;
	}

	public void setSide(OrderSide side) {
		this.side = side;
	}

	public Double getQuantity() {
		return quantity;
	}

	public void setQuantity(Double quantity) {
		this.quantity = quantity;
	}

	public Double getPrice() {
		return price;
	}

	public void setPrice(Double price) {
		this.price = price;
	}
}
