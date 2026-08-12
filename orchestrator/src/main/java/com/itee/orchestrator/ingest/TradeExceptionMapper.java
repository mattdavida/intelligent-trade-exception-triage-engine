package com.itee.orchestrator.ingest;

import org.springframework.stereotype.Component;

import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.TradeException;

@Component
public class TradeExceptionMapper {

    public TradeException toEntity(RawTradeExceptionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        requireText(event.getTradeId(), "tradeId");
        requireText(event.getCounterparty(), "counterparty");
        requireText(event.getDiscrepancyType(), "discrepancyType");
        requireText(event.getInstrument(), "instrument");
        requireText(event.getCurrency(), "currency");
        requireText(event.getSide(), "side");
        requireText(event.getRawDetails(), "rawDetails");
        if (event.getAmount() == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (event.getDetectedAt() == null) {
            throw new IllegalArgumentException("detectedAt must not be null");
        }

        TradeException entity = new TradeException();
        entity.setTradeId(event.getTradeId().trim());
        entity.setCounterparty(event.getCounterparty().trim());
        entity.setDiscrepancyType(event.getDiscrepancyType().trim());
        entity.setInstrument(event.getInstrument().trim());
        entity.setAmount(event.getAmount());
        entity.setCurrency(event.getCurrency().trim().toUpperCase());
        entity.setSide(event.getSide().trim().toUpperCase());
        entity.setDetectedAt(event.getDetectedAt());
        entity.setRawDetails(event.getRawDetails().trim());
        entity.setStatus(ExceptionStatus.NEW);
        return entity;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
