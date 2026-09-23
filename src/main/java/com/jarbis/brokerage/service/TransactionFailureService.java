package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.enums.TransactionStatus;
import com.jarbis.brokerage.exception.TransactionNotFoundException;
import com.jarbis.brokerage.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists transaction failure state independently from the main execution flow.
 */
@Service
public class TransactionFailureService {

    private final TransactionRepository transactionRepository;
    private final OrderService orderService;

    @Autowired
    public TransactionFailureService(TransactionRepository transactionRepository,
                                     OrderService orderService) {
        this.transactionRepository = transactionRepository;
        this.orderService = orderService;
    }

    /**
     * Mark a transaction as FAILED and cancel any remaining pending orders.
     * This work runs in its own transaction so the failure state is committed
     * even if the caller later rolls back.
     *
     * @param transactionId the transaction ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failTransaction(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found with id: " + transactionId));

        transaction.setStatus(TransactionStatus.FAILED);

        for (Order order : transaction.getParticipants()) {
            if (order.getStatus() == OrderStatus.PENDING) {
                orderService.cancelOrder(order.getId());
            }
        }

        transactionRepository.save(transaction);
    }
}
