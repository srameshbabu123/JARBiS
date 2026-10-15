package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.TransactionStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(nullable = false)
	private Double quantity;

	@Column(nullable = false)
	private Double executionPrice;

	@ManyToOne
	@JoinColumn(name = "buy_order_id", nullable = false)
	private Order buyOrder;

	@ManyToOne
	@JoinColumn(name = "sell_order_id", nullable = false)
	private Order sellOrder;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TransactionStatus status;

	public Transaction() {
	}

	public Long getId() {
		return id;
	}

	public Asset getAsset() {
		return asset;
	}

	public void setAsset(Asset asset) {
		this.asset = asset;
	}

	public Double getQuantity() {
		return quantity;
	}

	public void setQuantity(Double quantity) {
		this.quantity = quantity;
	}

	public Double getExecutionPrice() {
		return executionPrice;
	}

	public void setExecutionPrice(Double executionPrice) {
		this.executionPrice = executionPrice;
	}

	public Order getBuyOrder() {
		return buyOrder;
	}

	public void setBuyOrder(Order buyOrder) {
		this.buyOrder = buyOrder;
	}

	public Order getSellOrder() {
		return sellOrder;
	}

	public void setSellOrder(Order sellOrder) {
		this.sellOrder = sellOrder;
	}

	public List<Long> getParticipantOrderIds() {
		List<Long> participantOrderIds = new ArrayList<>(2);
		if (buyOrder != null && buyOrder.getId() != null) {
			participantOrderIds.add(buyOrder.getId());
		}
		if (sellOrder != null && sellOrder.getId() != null) {
			participantOrderIds.add(sellOrder.getId());
		}
		return participantOrderIds;
	}

	public void setParticipants(List<Order> participants) {
		buyOrder = null;
		sellOrder = null;
		if (participants != null) {
			participants.forEach(this::addParticipant);
		}
	}

	public void addParticipant(Order order) {
		if (order == null) {
			return;
		}
		if (order.getSide() == OrderSide.BUY) {
			if (buyOrder != null && !buyOrder.equals(order)) {
				throw new IllegalStateException("Transaction already has a buy order");
			}
			setBuyOrder(order);
			return;
		}
		if (order.getSide() == OrderSide.SELL) {
			if (sellOrder != null && !sellOrder.equals(order)) {
				throw new IllegalStateException("Transaction already has a sell order");
			}
			setSellOrder(order);
			return;
		}
		if (buyOrder == null) {
			setBuyOrder(order);
			return;
		}
		if (sellOrder == null) {
			setSellOrder(order);
			return;
		}
		throw new IllegalStateException("A transaction can only contain two participant orders");
	}

	public void removeParticipant(Order order) {
		if (order == null) {
			return;
		}
		if (buyOrder != null && buyOrder.equals(order)) {
			buyOrder = null;
		}
		if (sellOrder != null && sellOrder.equals(order)) {
			sellOrder = null;
		}
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public void setStatus(TransactionStatus status) {
		this.status = status;
	}
}
