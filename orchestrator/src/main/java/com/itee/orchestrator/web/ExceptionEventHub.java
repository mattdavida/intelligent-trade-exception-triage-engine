package com.itee.orchestrator.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itee.orchestrator.domain.TradeException;

@Component
public class ExceptionEventHub {

    private static final Logger log = LoggerFactory.getLogger(ExceptionEventHub.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public ExceptionEventHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"connected\"}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(TradeException ex) {
        Map<String, Object> payload = Map.of(
                "type",
                "EXCEPTION_UPDATED",
                "id",
                ex.getId().toString(),
                "tradeId",
                ex.getTradeId(),
                "status",
                ex.getStatus().name(),
                "severity",
                ex.getSeverity() == null ? "" : ex.getSeverity(),
                "confidenceScore",
                ex.getConfidenceScore() == null ? "" : ex.getConfidenceScore().toPlainString(),
                "discrepancyType",
                ex.getDiscrepancyType());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize SSE payload: {}", e.getMessage());
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("exception").data(json));
            } catch (Exception e) {
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already dead
                }
            }
        }
    }
}
