package com.itee.orchestrator.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.itee.orchestrator.confidence.ConfidenceFactor;
import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.ResolveAction;
import com.itee.orchestrator.domain.TradeException;

public record ExceptionResponse(
        UUID id,
        String tradeId,
        String counterparty,
        String discrepancyType,
        String instrument,
        BigDecimal amount,
        String currency,
        String side,
        Instant detectedAt,
        String rawDetails,
        ExceptionStatus status,
        String severity,
        String recommendation,
        String reasoning,
        BigDecimal confidenceScore,
        String confidenceRubricVersion,
        List<ConfidenceFactor> confidenceFactors,
        ResolveAction resolveAction,
        String resolveNotes,
        String overrideRecommendation,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static ExceptionResponse from(TradeException ex) {
        return new ExceptionResponse(
                ex.getId(),
                ex.getTradeId(),
                ex.getCounterparty(),
                ex.getDiscrepancyType(),
                ex.getInstrument(),
                ex.getAmount(),
                ex.getCurrency(),
                ex.getSide(),
                ex.getDetectedAt(),
                ex.getRawDetails(),
                ex.getStatus(),
                ex.getSeverity(),
                ex.getRecommendation(),
                ex.getReasoning(),
                ex.getConfidenceScore(),
                ex.getConfidenceRubricVersion(),
                ex.getConfidenceFactors(),
                ex.getResolveAction(),
                ex.getResolveNotes(),
                ex.getOverrideRecommendation(),
                ex.getResolvedAt(),
                ex.getCreatedAt(),
                ex.getUpdatedAt());
    }
}
