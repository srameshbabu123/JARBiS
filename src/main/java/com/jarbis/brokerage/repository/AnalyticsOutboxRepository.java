package com.jarbis.brokerage.repository;
import com.jarbis.brokerage.entity.AnalyticsOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AnalyticsOutboxRepository extends JpaRepository<AnalyticsOutbox, UUID> {
    boolean existsByTransactionId(Long transactionId);
}
