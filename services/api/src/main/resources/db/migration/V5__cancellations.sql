CREATE TABLE cancellation_attempt (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    refund_amount_krw BIGINT NOT NULL CHECK (refund_amount_krw >= 0),
    refund_status VARCHAR(20) NOT NULL,
    reservation_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key)
);
