package com.itee.orchestrator.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RawTradeExceptionListener {

    private static final Logger log = LoggerFactory.getLogger(RawTradeExceptionListener.class);

    private final ObjectMapper objectMapper;
    private final TradeExceptionIngestService ingestService;

    public RawTradeExceptionListener(ObjectMapper objectMapper, TradeExceptionIngestService ingestService) {
        this.objectMapper = objectMapper;
        this.ingestService = ingestService;
    }

    @KafkaListener(topics = "${itee.topics.raw-exceptions}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        try {
            RawTradeExceptionEvent event = objectMapper.readValue(payload, RawTradeExceptionEvent.class);
            ingestService.ingest(event);
        } catch (Exception e) {
            // Let the error handler / default logging surface poison messages; avoid silent drop.
            log.error("Failed to ingest raw-trade-exceptions payload: {}", payload, e);
            throw new IllegalStateException("Failed to ingest trade exception", e);
        }
    }
}
