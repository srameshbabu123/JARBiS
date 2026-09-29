package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.TransactionStatus;
import com.jarbis.brokerage.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
	private final TransactionService transactionService;

	public TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	/**
	 * Get a transaction by ID
	 */
	@GetMapping("/{transactionId}")
	public ResponseEntity<Transaction> getTransactionById(@PathVariable Long transactionId) {
		Transaction transaction = transactionService.getTransactionById(transactionId);
		return ResponseEntity.ok(transaction);
	}

	/**
	 * Get all transactions
	 */
	@GetMapping
	public ResponseEntity<List<Transaction>> getAllTransactions() {
		List<Transaction> transactions = transactionService.getAllTransactions();
		return ResponseEntity.ok(transactions);
	}

	/**
	 * Get all transactions by status
	 */
	@GetMapping("/status/{status}")
	public ResponseEntity<List<Transaction>> getTransactionsByStatus(@PathVariable TransactionStatus status) {
		List<Transaction> transactions = transactionService.getTransactionsByStatus(status);
		return ResponseEntity.ok(transactions);
	}

	/**
	 * Get all pending transactions
	 */
	@GetMapping("/status/pending")
	public ResponseEntity<List<Transaction>> getPendingTransactions() {
		List<Transaction> transactions = transactionService.getPendingTransactions();
		return ResponseEntity.ok(transactions);
	}

	/**
	 * Get all completed transactions
	 */
	@GetMapping("/status/completed")
	public ResponseEntity<List<Transaction>> getCompletedTransactions() {
		List<Transaction> transactions = transactionService.getCompletedTransactions();
		return ResponseEntity.ok(transactions);
	}

	/**
	 * Execute a transaction by executing all participant orders
	 */
	@PostMapping("/{transactionId}/execute")
	public ResponseEntity<Void> executeTransaction(@PathVariable Long transactionId) {
		transactionService.executeTransaction(transactionId);
		return ResponseEntity.status(HttpStatus.OK).build();
	}

	/**
	 * Complete a transaction (mark as completed)
	 */
	@PostMapping("/{transactionId}/complete")
	public ResponseEntity<Void> completeTransaction(@PathVariable Long transactionId) {
		transactionService.completeTransaction(transactionId);
		return ResponseEntity.status(HttpStatus.OK).build();
	}

	/**
	 * Fail a transaction (mark as failed)
	 */
	@PostMapping("/{transactionId}/fail")
	public ResponseEntity<Void> failTransaction(@PathVariable Long transactionId) {
		transactionService.failTransaction(transactionId);
		return ResponseEntity.status(HttpStatus.OK).build();
	}

	/**
	 * Get the trade value of a transaction
	 */
	@GetMapping("/{transactionId}/trade-value")
	public ResponseEntity<Double> getTradeValue(@PathVariable Long transactionId) {
		Double tradeValue = transactionService.computeTradeValueById(transactionId);
		return ResponseEntity.ok(tradeValue);
	}

	/**
	 * Get the number of orders in a transaction
	 */
	@GetMapping("/{transactionId}/order-count")
	public ResponseEntity<Integer> getOrderCount(@PathVariable Long transactionId) {
		Transaction transaction = transactionService.getTransactionById(transactionId);
		Integer orderCount = transactionService.getTransactionOrderCount(transaction);
		return ResponseEntity.ok(orderCount);
	}

	/**
	 * Check if all orders in a transaction are completed
	 */
	@GetMapping("/{transactionId}/all-orders-completed")
	public ResponseEntity<Boolean> areAllOrdersCompleted(@PathVariable Long transactionId) {
		Transaction transaction = transactionService.getTransactionById(transactionId);
		boolean allCompleted = transactionService.areAllOrdersCompleted(transaction);
		return ResponseEntity.ok(allCompleted);
	}
}
