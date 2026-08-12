package com.itee.orchestrator.confidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.TradeException;

/**
 * Locks rubric v1 math — auditable confidence is Java-owned and recomputable.
 * Weights: docs/confidence-rubric-v1.md
 */
class ConfidenceScorerTest {

    private final ConfidenceScorer scorer = new ConfidenceScorer();

    @Test
    void scoresFullHouseAtOne() {
        // known type + high amount + complete + known cpty + known instrument
        // 0.35 + 0.20 + 0.15 + 0.15 + 0.15 = 1.00
        TradeException ex = base("SSI_MISMATCH", "ACME-BANK", "ZN", "2500000.00");

        ConfidenceResult result = scorer.score(ex);

        assertEquals("v1", result.rubricVersion());
        assertEquals(bd("1.0000"), result.score());
        assertFired(result, "KNOWN_DISCREPANCY_TYPE", true);
        assertFired(result, "UNKNOWN_DISCREPANCY_TYPE", false);
        assertFired(result, "AMOUNT_BAND_HIGH", true);
        assertFired(result, "FIELDS_COMPLETE", true);
        assertFired(result, "COUNTERPARTY_KNOWN", true);
        assertFired(result, "INSTRUMENT_KNOWN", true);
        assertFactorWeight(result, "KNOWN_DISCREPANCY_TYPE", "0.35");
        assertFactorWeight(result, "UNKNOWN_DISCREPANCY_TYPE", "-0.25");
    }

    @Test
    void scoresDemoUnknownCounterpartyAtPointEightyFive() {
        // TRD-10049-shaped: UNKNOWN_COUNTERPARTY is in taxonomy; cpty not on allow-list
        // 0.35 + 0.20 + 0.15 + 0.15 = 0.85 (COUNTERPARTY_KNOWN off)
        TradeException ex = base("UNKNOWN_COUNTERPARTY", "UNKNOWN-DESK-99", "ZN", "1500000.00");

        ConfidenceResult result = scorer.score(ex);

        assertEquals(bd("0.8500"), result.score());
        assertFired(result, "KNOWN_DISCREPANCY_TYPE", true);
        assertFired(result, "AMOUNT_BAND_HIGH", true);
        assertFired(result, "FIELDS_COMPLETE", true);
        assertFired(result, "COUNTERPARTY_KNOWN", false);
        assertFired(result, "INSTRUMENT_KNOWN", true);
        assertFired(result, "UNKNOWN_DISCREPANCY_TYPE", false);
    }

    @Test
    void scoresDemoDuplicateTradeAtPointEighty() {
        // TRD-10048-shaped: below 1M amount band
        // 0.35 + 0.15 + 0.15 + 0.15 = 0.80
        TradeException ex = base("DUPLICATE_TRADE", "NORTH-CLEARING", "NQ", "320000.00");

        ConfidenceResult result = scorer.score(ex);

        assertEquals(bd("0.8000"), result.score());
        assertFired(result, "AMOUNT_BAND_HIGH", false);
        assertFired(result, "KNOWN_DISCREPANCY_TYPE", true);
        assertFired(result, "COUNTERPARTY_KNOWN", true);
        assertFired(result, "INSTRUMENT_KNOWN", true);
    }

    @Test
    void appliesUnknownTypePenalty() {
        // -0.25 + 0.15 + 0.15 + 0.15 = 0.20 (low amount, known cpty/instrument, complete)
        TradeException ex = base("WEIRD_NEW_TYPE", "ACME-BANK", "ZN", "100.00");

        ConfidenceResult result = scorer.score(ex);

        assertEquals(bd("0.2000"), result.score());
        assertFired(result, "KNOWN_DISCREPANCY_TYPE", false);
        assertFired(result, "UNKNOWN_DISCREPANCY_TYPE", true);
        assertFired(result, "AMOUNT_BAND_HIGH", false);
    }

    @Test
    void incompleteFieldsDropCompletenessFactor() {
        TradeException ex = base("SSI_MISMATCH", "ACME-BANK", "ZN", "2500000.00");
        ex.setRawDetails("  ");

        ConfidenceResult result = scorer.score(ex);

        // 0.35 + 0.20 + 0.15 + 0.15 = 0.85 (FIELDS_COMPLETE off)
        assertEquals(bd("0.8500"), result.score());
        assertFired(result, "FIELDS_COMPLETE", false);
    }

    @Test
    void amountBandFiresAtExactlyOneMillion() {
        TradeException ex = base("SSI_MISMATCH", "ACME-BANK", "ZN", "1000000.00");

        ConfidenceResult result = scorer.score(ex);

        assertFired(result, "AMOUNT_BAND_HIGH", true);
        assertEquals(bd("1.0000"), result.score());
    }

    @Test
    void amountBandDoesNotFireJustBelowOneMillion() {
        TradeException ex = base("SSI_MISMATCH", "ACME-BANK", "ZN", "999999.99");

        ConfidenceResult result = scorer.score(ex);

        assertFired(result, "AMOUNT_BAND_HIGH", false);
        // 0.35 + 0.15 + 0.15 + 0.15 = 0.80
        assertEquals(bd("0.8000"), result.score());
    }

    @Test
    void clampsScoreAtZeroWhenOnlyUnknownTypeFires() {
        TradeException ex = base("WEIRD_NEW_TYPE", "NOT-A-CPTY", "XX", "1.00");
        ex.setRawDetails("");

        ConfidenceResult result = scorer.score(ex);

        // only UNKNOWN_DISCREPANCY_TYPE (-0.25) would fire among scoring factors that apply;
        // FIELDS_COMPLETE off (blank rawDetails), cpty/instrument unknown, amount low
        assertFired(result, "UNKNOWN_DISCREPANCY_TYPE", true);
        assertFired(result, "FIELDS_COMPLETE", false);
        assertFired(result, "COUNTERPARTY_KNOWN", false);
        assertFired(result, "INSTRUMENT_KNOWN", false);
        assertEquals(bd("0.0000"), result.score());
    }

    @ParameterizedTest(name = "taxonomy type {0} is known")
    @CsvSource({
        "SSI_MISMATCH",
        "QUANTITY_BREAK",
        "PRICE_TOLERANCE",
        "LATE_CONFIRMATION",
        "MISSING_LEI",
        "CURRENCY_MISMATCH",
        "DUPLICATE_TRADE",
        "UNKNOWN_COUNTERPARTY"
    })
    void everyTaxonomyTypeIsKnown(String type) {
        TradeException ex = base(type, "ACME-BANK", "ZN", "100.00");
        ConfidenceResult result = scorer.score(ex);
        assertFired(result, "KNOWN_DISCREPANCY_TYPE", true);
        assertFired(result, "UNKNOWN_DISCREPANCY_TYPE", false);
    }

    @Test
    void alwaysEmitsSixFactorsInStableOrder() {
        ConfidenceResult result = scorer.score(base("SSI_MISMATCH", "ACME-BANK", "ZN", "100.00"));

        assertEquals(6, result.factors().size());
        assertEquals(
                "KNOWN_DISCREPANCY_TYPE,UNKNOWN_DISCREPANCY_TYPE,AMOUNT_BAND_HIGH,"
                        + "FIELDS_COMPLETE,COUNTERPARTY_KNOWN,INSTRUMENT_KNOWN",
                result.factors().stream().map(ConfidenceFactor::code).collect(Collectors.joining(",")));
    }

    @Test
    void isRecomputable() {
        TradeException ex = base("DUPLICATE_TRADE", "NORTH-CLEARING", "NQ", "320000.00");
        ConfidenceResult a = scorer.score(ex);
        ConfidenceResult b = scorer.score(ex);
        assertEquals(a.score(), b.score());
        assertEquals(a.rubricVersion(), b.rubricVersion());
        assertEquals(a.factors(), b.factors());
    }

    private static void assertFired(ConfidenceResult result, String code, boolean fired) {
        Map<String, ConfidenceFactor> byCode = result.factors().stream()
                .collect(Collectors.toMap(ConfidenceFactor::code, Function.identity()));
        assertTrue(byCode.containsKey(code), "missing factor " + code);
        assertEquals(fired, byCode.get(code).fired(), code + " fired");
        if (fired) {
            assertTrue(byCode.get(code).fired());
        } else {
            assertFalse(byCode.get(code).fired());
        }
    }

    private static void assertFactorWeight(ConfidenceResult result, String code, String weight) {
        ConfidenceFactor factor = result.factors().stream()
                .filter(f -> f.code().equals(code))
                .findFirst()
                .orElseThrow();
        assertEquals(0, bd(weight).compareTo(factor.weight()), code + " weight");
    }

    private static TradeException base(
            String discrepancyType, String counterparty, String instrument, String amount) {
        TradeException ex = new TradeException();
        ex.setTradeId("TRD-1");
        ex.setCounterparty(counterparty);
        ex.setDiscrepancyType(discrepancyType);
        ex.setInstrument(instrument);
        ex.setAmount(new BigDecimal(amount));
        ex.setCurrency("USD");
        ex.setSide("SELL");
        ex.setDetectedAt(Instant.parse("2026-08-12T13:00:00Z"));
        ex.setRawDetails("details");
        ex.setStatus(ExceptionStatus.NEW);
        return ex;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
