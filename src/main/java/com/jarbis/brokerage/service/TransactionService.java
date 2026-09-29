package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.enums.TransactionStatus;
import com.jarbis.brokerage.exception.EmptyTransactionException;
import com.jarbis.brokerage.exception.InvalidAmountException;
import com.jarbis.brokerage.exception.InvalidOrderRequestException;
import com.jarbis.brokerage.exception.TransactionExecutionException;
import com.jarbis.brokerage.exception.TransactionNotFoundException;
import com.jarbis.brokerage.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Transaction management. Handles transaction lifecycle,
 * execution, settlement, and validation.
 */
@Service
@Transactional
public class TransactionService {

	private final TransactionRepository transactionRepository;
	private final OrderService orderService;
	private final TransactionFailureService transactionFailureService;

	@Autowired
	public TransactionService(TransactionRepository transactionRepository, OrderService orderService,
			TransactionFailureService transactionFailureService) {
		this.transactionRepository = transactionRepository;
		this.orderService = orderService;
		this.transactionFailureService = transactionFailureService;
	}

	// ==================== Transaction Creation ====================

	/**
	 * Create a new transaction with the provided status.
	 *
	 * @param asset
	 *            the asset being traded
	 * @param quantity
	 *            the executed quantity
	 * @param executionPrice
	 *            the execution price per unit
	 * @param participantOrders
	 *            the participant orders in the transaction
	 * @param status
	 *            the initial transaction status
	 * @return the created transaction
	 */
	public Transaction createTransaction(Asset asset, Double quantity, Double executionPrice,
			List<Order> participantOrders, TransactionStatus status) {
		if (asset == null) {
			throw new InvalidOrderRequestException("Asset cannot be null");
		}
		if (status == null) {
			throw new InvalidOrderRequestException("Transaction status cannot be null");
		}
		if (quantity == null || quantity <= 0) {
			throw new InvalidAmountException("Quantity must be greater than 0");
		}
		if (executionPrice == null || executionPrice <= 0) {
			throw new InvalidAmountException("Execution price must be greater than 0");
		}
		if (participantOrders == null || participantOrders.isEmpty()) {
			throw new EmptyTransactionException("Transaction must contain at least one participant order");
		}

		Transaction transaction = new Transaction();
		transaction.setAsset(asset);
		transaction.setQuantity(quantity);
		transaction.setExecutionPrice(executionPrice);
		transaction.setStatus(status);

		for (Order order : participantOrders) {
			if (order == null) {
				throw new InvalidOrderRequestException("Participant orders cannot contain null entries");
			}
			transaction.addParticipant(order);
		}

		return transactionRepository.save(transaction);
	}

	/**
	 * Create a new pending transaction.
	 *
	 * @param asset
	 *            the asset being traded
	 * @param quantity
	 *            the executed quantity
	 * @param executionPrice
	 *            the execution price per unit
	 * @param participantOrders
	 *            the participant orders in the transaction
	 * @return the created pending transaction
	 */
	public Transaction createPendingTransaction(Asset asset, Double quantity, Double executionPrice,
			List<Order> participantOrders) {
		return createTransaction(asset, quantity, executionPrice, participantOrders, TransactionStatus.PENDING);
	}

	// ==================== Transaction Status Management ====================

	/**
	 * Update the status of a transaction.
	 *
	 * @param transactionId
	 *            the transaction ID
	 * @param newStatus
	 *            the new transaction status
	 */
	public void updateTransactionStatus(Long transactionId, TransactionStatus newStatus) {
		Transaction transaction = getTransactionById(transactionId);
		transaction.setStatus(newStatus);
		transactionRepository.save(transaction);
	}

	/**
	 * Transition transaction from PENDING to COMPLETED. This marks the transaction
	 * as successfully executed.
	 *
	 * @param transactionId
	 *            the transaction ID
	 */
	public void completeTransaction(Long transactionId) {
		updateTransactionStatus(transactionId, TransactionStatus.COMPLETED);
	}

	/**
	 * Transition transaction from PENDING to FAILED. This persists the failure
	 * state in a separate transaction so it survives the outer execution rollback.
	 *
	 * @param transactionId
	 *            the transaction ID
	 */
	public void failTransaction(Long transactionId) {
		transactionFailureService.failTransaction(transactionId);
	}

	// ==================== Trade Value Calculations ====================

	/**
	 * Calculate the total trade value (notional value) of a transaction. Formula:
	 * Execution Price × Quantity
	 *
	 * @param transaction
	 *            the transaction
	 * @return the total trade value
	 */
	public Double computeTradeValue(Transaction transaction) {
		return transaction.getExecutionPrice() * transaction.getQuantity();
	}

	/**
	 * Calculate trade value by transaction ID.
	 *
	 * @param transactionId
	 *            the transaction ID
	 * @return the total trade value
	 */
	public Double computeTradeValueById(Long transactionId) {
		Transaction transaction = getTransactionById(transactionId);
		return computeTradeValue(transaction);
	}

	// ==================== Transaction Retrieval ====================

	/**
	 * Get transaction by ID.
	 *
	 * @param transactionId
	 *            the transaction ID
	 * @return the transaction
	 * @throws TransactionNotFoundException
	 *             if transaction not found
	 */
	public Transaction getTransactionById(Long transactionId) {
		return transactionRepository.findById(transactionId)
				.orElseThrow(() -> new TransactionNotFoundException("Transaction not found with id: " + transactionId));
	}

	/**
	 * Get all transactions.
	 *
	 * @return list of all transactions
	 */
	public List<Transaction> getAllTransactions() {
		return transactionRepository.findAll();
	}

	/**
	 * Get all transactions with a specific status.
	 *
	 * @param status
	 *            the transaction status
	 * @return list of transactions with the given status
	 */
	public List<Transaction> getTransactionsByStatus(TransactionStatus status) {
		return transactionRepository.findByStatus(status);
	}

	/**
	 * Get all pending transactions.
	 *
	 * @return list of pending transactions
	 */
	public List<Transaction> getPendingTransactions() {
		return getTransactionsByStatus(TransactionStatus.PENDING);
	}

	/**
	 * Get all completed transactions.
	 *
	 * @return list of completed transactions
	 */
	public List<Transaction> getCompletedTransactions() {
		return getTransactionsByStatus(TransactionStatus.COMPLETED);
	}

	// ==================== Transaction Execution ====================

	/**
	 * Execute a transaction by executing all participant orders. Each order is
	 * executed according to its side (BUY or SELL), updating the respective user's
	 * account holdings and balance.
	 *
	 * @param transactionId
	 *            the transaction ID
	 * @throws TransactionNotFoundException
	 *             if transaction not found
	 * @throws EmptyTransactionException
	 *             if transaction has no participant orders
	 * @throws TransactionExecutionException
	 *             if any order fails to execute
	 */
	public void executeTransaction(Long transactionId) {
		Transaction transaction = getTransactionById(transactionId);

		if (transaction.getParticipants() == null || transaction.getParticipants().isEmpty()) {
			throw new EmptyTransactionException("Transaction has no participant orders to execute");
		}

		// Execute each participant order individually
		for (Order order : transaction.getParticipants()) {
			try {
				orderService.executeOrder(order.getId());
			} catch (RuntimeException e) {
				// If any order fails, fail the entire transaction
				failTransaction(transactionId);
				throw new TransactionExecutionException(
						"Transaction execution failed while executing order " + order.getId(), e);
			}
		}

		// Mark transaction as completed after all orders execute successfully
		completeTransaction(transactionId);
	}

	/**
	 * Get count of orders in a transaction.
	 *
	 * @param transaction
	 *            the transaction
	 * @return number of orders
	 */
	public Integer getTransactionOrderCount(Transaction transaction) {
		return (transaction.getParticipants() != null) ? transaction.getParticipants().size() : 0;
	}

	/**
	 * Check if all orders in a transaction are completed.
	 *
	 * @param transaction
	 *            the transaction
	 * @return true if all orders are completed
	 */
	public boolean areAllOrdersCompleted(Transaction transaction) {
		if (transaction.getParticipants() == null || transaction.getParticipants().isEmpty()) {
			return false;
		}
		return transaction.getParticipants().stream().allMatch(order -> order.getStatus() == OrderStatus.COMPLETED);
	}

}
