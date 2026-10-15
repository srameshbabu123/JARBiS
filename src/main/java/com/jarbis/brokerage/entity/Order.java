package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {
	// id, asset, account, status, side, quantity, price

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@ManyToOne
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private OrderStatus status;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private OrderSide side;

	@Column(nullable = false)
	private Double quantity;

	@Column(nullable = false)
	private Double remainingQuantity;

	@Column(nullable = false)
	private Double price;

	@Column(nullable = false, updatable = false)
	private LocalDateTime submittedAt;

	public Order() {
	}

	public Order(Asset asset, Account account, OrderStatus status) {
		this.asset = asset;
		this.account = account;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public Asset getAsset() {
		return asset;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
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
		if (this.remainingQuantity == null) {
			this.remainingQuantity = quantity;
		}
	}

	public Double getRemainingQuantity() {
		return remainingQuantity != null ? remainingQuantity : quantity;
	}

	public void setRemainingQuantity(Double remainingQuantity) {
		this.remainingQuantity = remainingQuantity;
	}

	public Double getPrice() {
		return price;
	}

	public void setPrice(Double price) {
		this.price = price;
	}

	public LocalDateTime getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(LocalDateTime submittedAt) {
		this.submittedAt = submittedAt;
	}

	@PrePersist
	void initializeSubmittedAt() {
		if (submittedAt == null) {
			submittedAt = LocalDateTime.now();
		}
	}
}
