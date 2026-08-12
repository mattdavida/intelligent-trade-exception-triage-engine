package com.itee.orchestrator.ai;

import java.math.BigDecimal;
import java.util.UUID;

public record AiAnalyzeRequest(
        UUID id,
        String tradeId,
        String counterparty,
        String discrepancyType,
        String instrument,
        BigDecimal amount,
        String currency,
        String side,
        String detectedAt,
        String rawDetails) {}
