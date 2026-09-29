package com.jarbis.brokerage.dto.response;

import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;

public class OrderResponseDto {

	private Long id;
	private Long accountId;
	private Long assetId;
	private OrderStatus status;
	private OrderSide side;
	private Double quantity;
	private Double price;
	private Long transactionId;

	public OrderResponseDto() {
	}

	public OrderResponseDto(Long id, Long accountId, Long assetId, OrderStatus status, OrderSide side, Double quantity,
			Double price, Long transactionId) {
		this.id = id;
		this.accountId = accountId;
		this.assetId = assetId;
		this.status = status;
		this.side = side;
		this.quantity = quantity;
		this.price = price;
		this.transactionId = transactionId;
	}

	public static OrderResponseDto fromOrder(Order order) {
		Long accountId = order.getAccount() != null ? order.getAccount().getId() : null;
		Long assetId = order.getAsset() != null ? order.getAsset().getId() : null;
		Long transactionId = order.getTransaction() != null ? order.getTransaction().getId() : null;
		return new OrderResponseDto(order.getId(), accountId, assetId, order.getStatus(), order.getSide(),
				order.getQuantity(), order.getPrice(), transactionId);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
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

	public Long getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(Long transactionId) {
		this.transactionId = transactionId;
	}
}
