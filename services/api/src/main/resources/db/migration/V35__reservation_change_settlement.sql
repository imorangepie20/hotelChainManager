CREATE TABLE payment_transaction (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    change_request_id UUID REFERENCES reservation_change_request(id),
    provider VARCHAR(30) NOT NULL,
    merchant_account VARCHAR(80) NOT NULL,
    gateway_transaction_id VARCHAR(160) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL CHECK (transaction_type IN ('ORIGINAL_CHARGE', 'CHANGE_CHARGE')),
    captured_amount_krw BIGINT NOT NULL CHECK (captured_amount_krw >= 0),
    refunded_amount_krw BIGINT NOT NULL DEFAULT 0 CHECK (refunded_amount_krw >= 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (refunded_amount_krw <= captured_amount_krw),
    UNIQUE (provider, merchant_account, gateway_transaction_id),
    UNIQUE (change_request_id)
);

CREATE INDEX payment_transaction_reservation_idx
    ON payment_transaction (reservation_id, created_at);

CREATE TABLE reservation_change_hold_day (
    request_id UUID NOT NULL REFERENCES reservation_change_request(id) ON DELETE CASCADE,
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    stay_date DATE NOT NULL,
    rooms INTEGER NOT NULL CHECK (rooms > 0),
    hold_kind VARCHAR(30) NOT NULL CHECK (hold_kind IN ('NEW_HOLD', 'EXISTING_CONFIRMED')),
    status VARCHAR(20) NOT NULL DEFAULT 'HELD' CHECK (status IN ('HELD', 'RELEASED', 'APPLIED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    PRIMARY KEY (request_id, stay_date)
);

CREATE INDEX reservation_change_hold_expiry_idx
    ON reservation_change_hold_day (expires_at, request_id)
    WHERE status = 'HELD';

CREATE TABLE payment_adjustment_attempt (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES reservation_change_request(id),
    original_payment_transaction_id UUID REFERENCES payment_transaction(id),
    adjustment_type VARCHAR(30) NOT NULL CHECK (adjustment_type IN (
        'CREATE_CHECKOUT', 'REFUND_ORIGINAL', 'REFUND_ADJUSTMENT'
    )),
    provider VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    amount_krw BIGINT NOT NULL CHECK (amount_krw >= 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN (
        'NEW', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'
    )),
    public_token_hash CHAR(64),
    gateway_transaction_id VARCHAR(160),
    provider_event_id VARCHAR(160),
    checkout_url VARCHAR(1000),
    checkout_started_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (request_id, idempotency_key)
);

CREATE UNIQUE INDEX payment_adjustment_attempt_token_idx
    ON payment_adjustment_attempt (public_token_hash)
    WHERE public_token_hash IS NOT NULL;

CREATE UNIQUE INDEX payment_adjustment_attempt_event_idx
    ON payment_adjustment_attempt (provider, provider_event_id)
    WHERE provider_event_id IS NOT NULL;

CREATE TABLE reservation_change_outbox (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES reservation_change_request(id),
    attempt_id UUID NOT NULL REFERENCES payment_adjustment_attempt(id),
    command_type VARCHAR(40) NOT NULL CHECK (command_type IN ('CREATE_CHECKOUT', 'REFUND', 'QUERY')),
    dedupe_key VARCHAR(160) NOT NULL UNIQUE,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    claim_token UUID,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_expires_at TIMESTAMPTZ,
    last_error VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX reservation_change_outbox_claim_idx
    ON reservation_change_outbox (status, next_attempt_at, created_at);

CREATE TABLE reservation_change_customer_session (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES reservation_change_request(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE reservation_change_request
    ADD COLUMN settlement_expires_at TIMESTAMPTZ,
    ADD COLUMN apply_claim_token UUID,
    ADD COLUMN apply_lease_expires_at TIMESTAMPTZ,
    ADD COLUMN apply_attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (apply_attempt_count >= 0);

ALTER TABLE reservation_stay_change
    ADD COLUMN change_request_id UUID UNIQUE REFERENCES reservation_change_request(id);
