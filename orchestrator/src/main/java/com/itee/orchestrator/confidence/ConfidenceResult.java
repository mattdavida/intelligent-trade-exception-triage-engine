package com.itee.orchestrator.confidence;

import java.math.BigDecimal;
import java.util.List;

public record ConfidenceResult(
        BigDecimal score, String rubricVersion, List<ConfidenceFactor> factors) {}
