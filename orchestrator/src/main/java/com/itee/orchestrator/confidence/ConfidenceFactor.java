package com.itee.orchestrator.confidence;

import java.math.BigDecimal;

public record ConfidenceFactor(String code, BigDecimal weight, boolean fired) {}
