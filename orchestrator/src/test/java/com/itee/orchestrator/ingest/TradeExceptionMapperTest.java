package com.itee.orchestrator.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.TradeException;

/**
 * Mapper owns the Kafka → entity fact mapping. Status always starts NEW.
 */
class TradeExceptionMapperTest {

    private final TradeExceptionMapper mapper = new TradeExceptionMapper();

    @Test
    void mapsEventToNewEntity() {
        RawTradeExceptionEvent event = sampleEvent();

        TradeException entity = mapper.toEntity(event);

        assertEquals("TRD-10042", entity.getTradeId());
        assertEquals("ACME-BANK", entity.getCounterparty());
        assertEquals("SSI_MISMATCH", entity.getDiscrepancyType());
        assertEquals("ZN", entity.getInstrument());
        assertEquals(new BigDecimal("2500000.00"), entity.getAmount());
        assertEquals("USD", entity.getCurrency());
        assertEquals("SELL", entity.getSide());
        assertEquals(Instant.parse("2026-08-12T13:00:00Z"), entity.getDetectedAt());
        assertEquals(
                "Settlement account on affirm differs from SSI master", entity.getRawDetails());
        assertEquals(ExceptionStatus.NEW, entity.getStatus());
    }

    @Test
    void uppercasesCurrencyAndSide() {
        RawTradeExceptionEvent event = sampleEvent();
        event.setCurrency("usd");
        event.setSide("buy");

        TradeException entity = mapper.toEntity(event);

        assertEquals("USD", entity.getCurrency());
        assertEquals("BUY", entity.getSide());
    }

    @Test
    void trimsTextFields() {
        RawTradeExceptionEvent event = sampleEvent();
        event.setTradeId("  TRD-10042  ");
        event.setCounterparty("  ACME-BANK ");
        event.setRawDetails("  padded details  ");

        TradeException entity = mapper.toEntity(event);

        assertEquals("TRD-10042", entity.getTradeId());
        assertEquals("ACME-BANK", entity.getCounterparty());
        assertEquals("padded details", entity.getRawDetails());
    }

    @Test
    void rejectsNullEvent() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(null));
    }

    @Test
    void rejectsNullAmount() {
        RawTradeExceptionEvent event = sampleEvent();
        event.setAmount(null);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(event));
        assertEquals("amount must not be null", ex.getMessage());
    }

    @Test
    void rejectsNullDetectedAt() {
        RawTradeExceptionEvent event = sampleEvent();
        event.setDetectedAt(null);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(event));
        assertEquals("detectedAt must not be null", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void rejectsBlankTradeId(String tradeId) {
        RawTradeExceptionEvent event = sampleEvent();
        event.setTradeId(tradeId);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(event));
        assertEquals("tradeId must not be blank", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "counterparty",
                "discrepancyType",
                "instrument",
                "currency",
                "side",
                "rawDetails"
            })
    void rejectsBlankRequiredTextFields(String field) {
        RawTradeExceptionEvent event = sampleEvent();
        switch (field) {
            case "counterparty" -> event.setCounterparty(" ");
            case "discrepancyType" -> event.setDiscrepancyType("");
            case "instrument" -> event.setInstrument(null);
            case "currency" -> event.setCurrency("\t");
            case "side" -> event.setSide("  ");
            case "rawDetails" -> event.setRawDetails("");
            default -> throw new IllegalStateException(field);
        }
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(event));
        assertEquals(field + " must not be blank", ex.getMessage());
    }

    private static RawTradeExceptionEvent sampleEvent() {
        RawTradeExceptionEvent event = new RawTradeExceptionEvent();
        event.setTradeId("TRD-10042");
        event.setCounterparty("ACME-BANK");
        event.setDiscrepancyType("SSI_MISMATCH");
        event.setInstrument("ZN");
        event.setAmount(new BigDecimal("2500000.00"));
        event.setCurrency("USD");
        event.setSide("SELL");
        event.setDetectedAt(Instant.parse("2026-08-12T13:00:00Z"));
        event.setRawDetails("Settlement account on affirm differs from SSI master");
        return event;
    }
}
