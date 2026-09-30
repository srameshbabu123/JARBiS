package com.jarbis.brokerage.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderSubmittedPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String orderSubmittedTopic;

	public OrderSubmittedPublisher(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${brokerage.kafka.topics.order-submitted}") String orderSubmittedTopic) {
		this.kafkaTemplate = kafkaTemplate;
		this.orderSubmittedTopic = orderSubmittedTopic;
	}

	public void publish(Long orderId) {
		kafkaTemplate.send(orderSubmittedTopic, orderId.toString());
	}
}
