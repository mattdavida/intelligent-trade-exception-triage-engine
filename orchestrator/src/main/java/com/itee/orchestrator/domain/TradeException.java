package com.itee.orchestrator.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.itee.orchestrator.confidence.ConfidenceFactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_exceptions")
public class TradeException {

    @Id
    private UUID id;

    @Column(name = "trade_id", nullable = false, length = 64)
    private String tradeId;

    @Column(nullable = false, length = 128)
    private String counterparty;

    @Column(name = "discrepancy_type", nullable = false, length = 64)
    private String discrepancyType;

    @Column(nullable = false, length = 32)
    private String instrument;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "raw_details", nullable = false, columnDefinition = "TEXT")
    private String rawDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExceptionStatus status;

    @Column(length = 16)
    private String severity;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "confidence_rubric_version", length = 16)
    private String confidenceRubricVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confidence_factors", columnDefinition = "jsonb")
    private List<ConfidenceFactor> confidenceFactors = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "resolve_action", length = 16)
    private ResolveAction resolveAction;

    @Column(name = "resolve_notes", columnDefinition = "TEXT")
    private String resolveNotes;

    @Column(name = "override_recommendation", columnDefinition = "TEXT")
    private String overrideRecommendation;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = ExceptionStatus.NEW;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public ExceptionStatus getStatus() {
        return status;
    }

    public void setStatus(ExceptionStatus status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getConfidenceRubricVersion() {
        return confidenceRubricVersion;
    }

    public void setConfidenceRubricVersion(String confidenceRubricVersion) {
        this.confidenceRubricVersion = confidenceRubricVersion;
    }

    public List<ConfidenceFactor> getConfidenceFactors() {
        return confidenceFactors;
    }

    public void setConfidenceFactors(List<ConfidenceFactor> confidenceFactors) {
        this.confidenceFactors = confidenceFactors;
    }

    public ResolveAction getResolveAction() {
        return resolveAction;
    }

    public void setResolveAction(ResolveAction resolveAction) {
        this.resolveAction = resolveAction;
    }

    public String getResolveNotes() {
        return resolveNotes;
    }

    public void setResolveNotes(String resolveNotes) {
        this.resolveNotes = resolveNotes;
    }

    public String getOverrideRecommendation() {
        return overrideRecommendation;
    }

    public void setOverrideRecommendation(String overrideRecommendation) {
        this.overrideRecommendation = overrideRecommendation;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
