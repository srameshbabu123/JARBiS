package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByStatus(TransactionStatus status);
}
