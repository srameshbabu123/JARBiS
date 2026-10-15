package com.jarbis.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

@Component
public class TradingEventListener {
    private final ObjectMapper mapper;
    private final AnalyticsProjector projector;
    public TradingEventListener(ObjectMapper mapper, AnalyticsProjector projector) { this.mapper = mapper; this.projector = projector; }
    @KafkaListener(topics = "${analytics.topic}")
    public void receive(String json) throws Exception { projector.apply(mapper.readValue(json, SettlementEvent.class)); }

    @Configuration
    static class RetryConfiguration {
        @Bean
        DefaultErrorHandler settlementErrorHandler() {
            // Never silently skip financial events. An invalid event blocks this partition until repaired.
            DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS));
            handler.addRetryableExceptions(Exception.class);
            return handler;
        }
    }
}
