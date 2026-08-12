CREATE TABLE trade_exceptions (
    id              UUID PRIMARY KEY,
    trade_id        VARCHAR(64)  NOT NULL,
    counterparty    VARCHAR(128) NOT NULL,
    discrepancy_type VARCHAR(64) NOT NULL,
    instrument      VARCHAR(32)  NOT NULL,
    amount          NUMERIC(18, 2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    side            VARCHAR(8)   NOT NULL,
    detected_at     TIMESTAMPTZ  NOT NULL,
    raw_details     TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trade_exceptions_status ON trade_exceptions (status);
CREATE INDEX idx_trade_exceptions_trade_id ON trade_exceptions (trade_id);
