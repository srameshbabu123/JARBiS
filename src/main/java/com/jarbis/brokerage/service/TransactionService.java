package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.enums.TransactionStatus;
import com.jarbis.brokerage.exception.EmptyTransactionException;
import com.jarbis.brokerage.exception.TransactionExecutionException;
import com.jarbis.brokerage.exception.TransactionNotFoundException;
import com.jarbis.brokerage.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Transaction management.
 * Handles transaction lifecycle, execution, settlement, and validation.
 */
@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OrderService orderService;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository,
                              OrderService orderService) {
        this.transactionRepository = transactionRepository;
        this.orderService = orderService;
    }

    // ==================== Transaction Status Management ====================

    /**
     * Update the status of a transaction.
     *
     * @param transactionId the transaction ID
     * @param newStatus the new transaction status
     * @return the updated transaction
     */
    public Transaction updateTransactionStatus(Long transactionId, TransactionStatus newStatus) {
        Transaction transaction = getTransactionById(transactionId);
        transaction.setStatus(newStatus);
        return transactionRepository.save(transaction);
    }

    /**
     * Transition transaction from PENDING to COMPLETED.
     * This marks the transaction as successfully executed.
     *
     * @param transactionId the transaction ID
     * @return the updated transaction
     */
    public Transaction completeTransaction(Long transactionId) {
        return updateTransactionStatus(transactionId, TransactionStatus.COMPLETED);
    }

    /**
     * Transition transaction from PENDING to FAILED.
     * This marks the transaction as failed and should trigger order cancellation.
     *
     * @param transactionId the transaction ID
     * @return the updated transaction
     */
    public Transaction failTransaction(Long transactionId) {
        Transaction transaction = updateTransactionStatus(transactionId, TransactionStatus.FAILED);
        // Automatically cancel all orders associated with this failed transaction
        cancelTransactionOrders(transaction);
        return transaction;
    }

    /**
     * Cancel all orders in a transaction when it fails.
     */
    private void cancelTransactionOrders(Transaction transaction) {
        if (transaction.getParticipants() != null) {
            transaction.getParticipants().forEach(order -> {
                try {
                    orderService.cancelOrder(order.getId());
                } catch (IllegalStateException e) {
                    // Order may already be completed, skip
                }
            });
        }
    }

    // ==================== Trade Value Calculations ====================

    /**
     * Calculate the total trade value (notional value) of a transaction.
     * Formula: Execution Price × Quantity
     *
     * @param transaction the transaction
     * @return the total trade value
     */
    public Double computeTradeValue(Transaction transaction) {
        return transaction.getExecutionPrice() * transaction.getQuantity();
    }

    /**
     * Calculate trade value by transaction ID.
     *
     * @param transactionId the transaction ID
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
     * @param transactionId the transaction ID
     * @return the transaction
     * @throws TransactionNotFoundException if transaction not found
     */
    public Transaction getTransactionById(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found with id: " + transactionId));
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
     * @param status the transaction status
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
     * Execute a transaction by executing all participant orders.
     * Each order is executed according to its side (BUY or SELL),
     * updating the respective user's account holdings and balance.
     *
     * @param transactionId the transaction ID
     * @throws TransactionNotFoundException if transaction not found
     * @throws EmptyTransactionException if transaction has no participant orders
     * @throws TransactionExecutionException if any order fails to execute
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
            } catch (Exception e) {
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
     * Calculate total notional value for all orders in a transaction.
     *
     * @param transaction the transaction
     * @return the sum of all order values
     */
    public Double getTransactionOrdersValue(Transaction transaction) {
        if (transaction.getParticipants() == null || transaction.getParticipants().isEmpty()) {
            return 0.0;
        }
        return (double) transaction.getParticipants().size() * computeTradeValue(transaction);
    }

    /**
     * Get count of orders in a transaction.
     *
     * @param transaction the transaction
     * @return number of orders
     */
    public Integer getTransactionOrderCount(Transaction transaction) {
        return (transaction.getParticipants() != null) ? transaction.getParticipants().size() : 0;
    }

    /**
     * Check if all orders in a transaction are completed.
     *
     * @param transaction the transaction
     * @return true if all orders are completed
     */
    public boolean areAllOrdersCompleted(Transaction transaction) {
        if (transaction.getParticipants() == null || transaction.getParticipants().isEmpty()) {
            return false;
        }
        return transaction.getParticipants().stream()
                .allMatch(order -> order.getStatus() == OrderStatus.COMPLETED);
    }

}

