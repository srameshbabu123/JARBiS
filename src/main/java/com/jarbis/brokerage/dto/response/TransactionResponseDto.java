package com.jarbis.brokerage.dto.response;

import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.TransactionStatus;

public class TransactionResponseDto {

	private Long id;
	private Long assetId;
	private Double quantity;
	private Double executionPrice;
	private TransactionStatus status;
	private Integer participantCount;

	public TransactionResponseDto() {
	}

	public TransactionResponseDto(Long id, Long assetId, Double quantity, Double executionPrice,
			TransactionStatus status, Integer participantCount) {
		this.id = id;
		this.assetId = assetId;
		this.quantity = quantity;
		this.executionPrice = executionPrice;
		this.status = status;
		this.participantCount = participantCount;
	}

	public static TransactionResponseDto fromTransaction(Transaction transaction) {
		Long assetId = transaction.getAsset() != null ? transaction.getAsset().getId() : null;
		Integer participantCount = transaction.getParticipants() != null ? transaction.getParticipants().size() : 0;
		return new TransactionResponseDto(transaction.getId(), assetId, transaction.getQuantity(),
				transaction.getExecutionPrice(), transaction.getStatus(), participantCount);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public Double getExecutionPrice() {
		return executionPrice;
	}

	public void setExecutionPrice(Double executionPrice) {
		this.executionPrice = executionPrice;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public void setStatus(TransactionStatus status) {
		this.status = status;
	}

	public Integer getParticipantCount() {
		return participantCount;
	}

	public void setParticipantCount(Integer participantCount) {
		this.participantCount = participantCount;
	}
}
