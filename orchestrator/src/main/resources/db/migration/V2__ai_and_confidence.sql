ALTER TABLE trade_exceptions
    ADD COLUMN severity VARCHAR(16),
    ADD COLUMN recommendation TEXT,
    ADD COLUMN reasoning TEXT,
    ADD COLUMN confidence_score NUMERIC(5, 4),
    ADD COLUMN confidence_rubric_version VARCHAR(16),
    ADD COLUMN confidence_factors JSONB,
    ADD COLUMN resolve_action VARCHAR(16),
    ADD COLUMN resolve_notes TEXT,
    ADD COLUMN override_recommendation TEXT,
    ADD COLUMN resolved_at TIMESTAMPTZ;

CREATE INDEX idx_trade_exceptions_pending
    ON trade_exceptions (status)
    WHERE status IN ('NEW', 'ANALYZING', 'PENDING_REVIEW', 'ANALYZING_FAILED');
