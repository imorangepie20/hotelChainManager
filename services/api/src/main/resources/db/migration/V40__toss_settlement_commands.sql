CREATE TABLE toss_adjustment_order (
    attempt_id UUID PRIMARY KEY REFERENCES payment_adjustment_attempt(id) ON DELETE CASCADE,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    merchant_account VARCHAR(80) NOT NULL,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    payment_key VARCHAR(160) UNIQUE,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    amount_krw BIGINT NOT NULL CHECK (amount_krw > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','APPROVING','SUCCEEDED','FAILED','UNKNOWN')),
    provider_event_id VARCHAR(160),
    claimed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE toss_refund_command (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES payment_transaction(id) ON DELETE CASCADE,
    adjustment_attempt_id UUID UNIQUE REFERENCES payment_adjustment_attempt(id) ON DELETE CASCADE,
    cancellation_attempt_id UUID REFERENCES cancellation_attempt(id) ON DELETE CASCADE,
    merchant_account VARCHAR(80) NOT NULL,
    payment_key VARCHAR(160) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    amount_krw BIGINT NOT NULL CHECK (amount_krw > 0),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    reason VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','PROCESSING','SUCCEEDED','FAILED','UNKNOWN')),
    claim_token UUID,
    lease_expires_at TIMESTAMPTZ,
    provider_event_id VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((adjustment_attempt_id IS NULL) <> (cancellation_attempt_id IS NULL)),
    UNIQUE (merchant_account, provider_event_id),
    UNIQUE (cancellation_attempt_id, transaction_id)
);
CREATE UNIQUE INDEX toss_refund_active_transaction_idx ON toss_refund_command(transaction_id)
    WHERE status IN ('NEW','PROCESSING','UNKNOWN');
CREATE TABLE toss_webhook_lookup (
    order_id VARCHAR(64) PRIMARY KEY,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);
