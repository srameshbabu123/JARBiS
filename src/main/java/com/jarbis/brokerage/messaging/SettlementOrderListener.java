package com.jarbis.brokerage.messaging;

import com.jarbis.brokerage.service.SettlementEngineService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SettlementOrderListener {

	private final SettlementEngineService settlementEngineService;

	public SettlementOrderListener(SettlementEngineService settlementEngineService) {
		this.settlementEngineService = settlementEngineService;
	}

	@KafkaListener(topics = "${brokerage.kafka.topics.order-submitted}", groupId = "${spring.kafka.consumer.group-id}")
	public void onOrderSubmitted(String payload) {
		settlementEngineService.processSubmittedOrder(Long.parseLong(payload.trim()));
	}
}
