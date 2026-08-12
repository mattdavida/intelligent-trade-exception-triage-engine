package com.itee.producer;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RawTradeExceptionEvent {

    private String tradeId;
    private String counterparty;
    private String discrepancyType;
    private String instrument;
    private BigDecimal amount;
    private String currency;
    private String side;
    private Instant detectedAt;
    private String rawDetails;

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getDiscrepancyType() {
        return discrepancyType;
    }

    public void setDiscrepancyType(String discrepancyType) {
        this.discrepancyType = discrepancyType;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getRawDetails() {
        return rawDetails;
    }

    public void setRawDetails(String rawDetails) {
        this.rawDetails = rawDetails;
    }
}
