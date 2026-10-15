package com.jarbis.brokerage.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.entity.*;
import com.jarbis.brokerage.repository.OrderAuditRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;

@Component
public class OrderAuditWriter {
    private final OrderAuditRepository repository;
    private final ObjectMapper mapper;
    private final TransactionTemplate independent;
    public OrderAuditWriter(OrderAuditRepository repository,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.repository=repository;this.mapper=mapper;
        independent=new TransactionTemplate(manager);independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Map<String,Object> snapshot(Order order) {
        Map<String,Object> s=new LinkedHashMap<>();
        s.put("orderId",order.getId());s.put("clientId",order.getAccount().getOwner().getId());
        s.put("accountId",order.getAccount().getId());s.put("assetId",order.getAsset().getId());
        s.put("instrument",order.getAsset().getName());s.put("side",order.getSide().name());
        s.put("status",order.getStatus().name());s.put("quantity",order.getQuantity());
        s.put("remainingQuantity",order.getRemainingQuantity());s.put("orderPrice",order.getPrice());
        s.put("cashBalance",order.getAccount().getBalance());s.put("currency",order.getAccount().getCurrency().name());
        double held=order.getAccount().getHoldings().stream().filter(h->h.getAsset().getId().equals(order.getAsset().getId()))
            .mapToDouble(h->h.getQuantity()).sum();
        s.put("holdingQuantity",held);
        return s;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Order order,String kind,Map<String,Object> details) { write(snapshot(order),kind,details); }
    private void write(Map<String,Object> snapshot,String kind,Map<String,Object> details) {
        UUID id=UUID.randomUUID();Instant at=Instant.now();
        Map<String,Object> e=new LinkedHashMap<>(snapshot);
        e.put("schemaVersion",1);e.put("eventId",id);e.put("type","OrderLifecycle");
        e.put("eventType",kind);e.put("occurredAt",at);e.put("details",details);
        var auth=SecurityContextHolder.getContext().getAuthentication();
        e.put("actor",auth==null?"SYSTEM":auth.getName());
        try { repository.save(new OrderAuditEvent(id,(Long)snapshot.get("orderId"),mapper.writeValueAsString(e),at)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot record order evidence",ex); }
    }
    public void failureAfterRollback(List<Map<String,Object>> originals,String errorCode) {
        failureAfterRollback(originals,errorCode,Map.of());
    }
    public void failureAfterRollback(List<Map<String,Object>> originals,String errorCode,Map<String,Object> attempt) {
        Map<String,Object> details=new LinkedHashMap<>(attempt);details.put("errorCode",errorCode);details.put("financialChangesCommitted",false);
        Runnable persist=()->independent.executeWithoutResult(status->originals.forEach(s->write(s,"SETTLEMENT_FAILED",details)));
        if(!TransactionSynchronizationManager.isSynchronizationActive()) { persist.run();return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if(status==STATUS_ROLLED_BACK) persist.run(); }
        });
    }
}
