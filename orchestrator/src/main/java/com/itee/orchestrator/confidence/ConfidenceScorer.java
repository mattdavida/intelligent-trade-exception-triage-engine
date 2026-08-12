package com.itee.orchestrator.confidence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.itee.orchestrator.domain.TradeException;

/**
 * Deterministic rubric v1 — recomputable from exception fields. Not LLM self-grade.
 */
@Component
public class ConfidenceScorer {

    public static final String RUBRIC_VERSION = "v1";

    private static final Set<String> KNOWN_TYPES = Set.of(
            "SSI_MISMATCH",
            "QUANTITY_BREAK",
            "PRICE_TOLERANCE",
            "LATE_CONFIRMATION",
            "MISSING_LEI",
            "CURRENCY_MISMATCH",
            "DUPLICATE_TRADE",
            "UNKNOWN_COUNTERPARTY");

    private static final Set<String> KNOWN_COUNTERPARTIES = Set.of(
            "ACME-BANK", "NORTH-CLEARING", "PACIFIC-BROKER", "EURO-DESK", "LATAM-PRIME");

    private static final Set<String> KNOWN_INSTRUMENTS =
            Set.of("ZN", "ZB", "ZF", "ES", "NQ", "CL");

    private static final BigDecimal AMOUNT_HIGH = new BigDecimal("1000000");

    public ConfidenceResult score(TradeException ex) {
        List<ConfidenceFactor> factors = new ArrayList<>();

        boolean knownType = ex.getDiscrepancyType() != null
                && KNOWN_TYPES.contains(ex.getDiscrepancyType());
        factors.add(factor("KNOWN_DISCREPANCY_TYPE", "0.35", knownType));
        factors.add(factor("UNKNOWN_DISCREPANCY_TYPE", "-0.25", !knownType));

        boolean amountHigh =
                ex.getAmount() != null && ex.getAmount().compareTo(AMOUNT_HIGH) >= 0;
        factors.add(factor("AMOUNT_BAND_HIGH", "0.20", amountHigh));

        boolean fieldsComplete = notBlank(ex.getTradeId())
                && notBlank(ex.getCounterparty())
                && notBlank(ex.getInstrument())
                && notBlank(ex.getCurrency())
                && notBlank(ex.getSide())
                && notBlank(ex.getRawDetails());
        factors.add(factor("FIELDS_COMPLETE", "0.15", fieldsComplete));

        boolean counterpartyKnown =
                ex.getCounterparty() != null && KNOWN_COUNTERPARTIES.contains(ex.getCounterparty());
        factors.add(factor("COUNTERPARTY_KNOWN", "0.15", counterpartyKnown));

        boolean instrumentKnown =
                ex.getInstrument() != null && KNOWN_INSTRUMENTS.contains(ex.getInstrument());
        factors.add(factor("INSTRUMENT_KNOWN", "0.15", instrumentKnown));

        BigDecimal sum = BigDecimal.ZERO;
        for (ConfidenceFactor f : factors) {
            if (f.fired()) {
                sum = sum.add(f.weight());
            }
        }
        BigDecimal score = sum.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
        return new ConfidenceResult(score, RUBRIC_VERSION, List.copyOf(factors));
    }

    private static ConfidenceFactor factor(String code, String weight, boolean fired) {
        return new ConfidenceFactor(code, new BigDecimal(weight), fired);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
