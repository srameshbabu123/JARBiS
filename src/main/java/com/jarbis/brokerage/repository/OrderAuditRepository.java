package com.jarbis.brokerage.repository;
import com.jarbis.brokerage.entity.OrderAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderAuditRepository extends JpaRepository<OrderAuditEvent,Long> { }
