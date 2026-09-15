CREATE TABLE toss_settlement_run (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL CHECK (provider = 'TOSS_LIVE'),
    merchant_account VARCHAR(80) NOT NULL,
    sold_date_from DATE NOT NULL,
    sold_date_to DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED')),
    current_sold_date DATE NOT NULL,
    current_page INTEGER NOT NULL DEFAULT 1 CHECK (current_page >= 1),
    page_size INTEGER NOT NULL DEFAULT 500 CHECK (page_size BETWEEN 1 AND 5000),
    snapshot_count INTEGER NOT NULL DEFAULT 0 CHECK (snapshot_count >= 0),
    matched_count INTEGER NOT NULL DEFAULT 0 CHECK (matched_count >= 0),
    mismatch_count INTEGER NOT NULL DEFAULT 0 CHECK (mismatch_count >= 0),
    pending_count INTEGER NOT NULL DEFAULT 0 CHECK (pending_count >= 0),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_token UUID,
    lease_expires_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    requested_by UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CHECK (sold_date_from <= sold_date_to),
    CHECK (current_sold_date BETWEEN sold_date_from AND sold_date_to),
    UNIQUE (requested_by, idempotency_key)
);

CREATE INDEX toss_settlement_run_queue_idx
    ON toss_settlement_run (status, next_attempt_at, created_at);

CREATE TABLE toss_settlement_snapshot (
    id UUID PRIMARY KEY,
    first_run_id UUID NOT NULL REFERENCES toss_settlement_run(id),
    merchant_account VARCHAR(80) NOT NULL,
    payment_key VARCHAR(200) NOT NULL,
    transaction_key VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency = 'KRW'),
    method VARCHAR(30) NOT NULL CHECK (method IN ('카드','간편결제')),
    amount_krw BIGINT NOT NULL,
    fee_krw BIGINT NOT NULL CHECK (fee_krw >= 0),
    fee_supply_krw BIGINT NOT NULL CHECK (fee_supply_krw >= 0),
    fee_vat_krw BIGINT NOT NULL CHECK (fee_vat_krw >= 0),
    payout_krw BIGINT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    sold_date DATE NOT NULL,
    paid_out_date DATE NOT NULL,
    cancellation BOOLEAN NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_account, payment_key, transaction_key, sold_date)
);

CREATE INDEX toss_settlement_snapshot_range_idx
    ON toss_settlement_snapshot (merchant_account, sold_date, transaction_key);

CREATE TABLE toss_settlement_reconciliation (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES toss_settlement_run(id) ON DELETE CASCADE,
    reconciliation_key VARCHAR(300) NOT NULL,
    snapshot_id UUID REFERENCES toss_settlement_snapshot(id),
    payment_transaction_id UUID REFERENCES payment_transaction(id),
    refund_command_id UUID REFERENCES toss_refund_command(id),
    status VARCHAR(30) NOT NULL CHECK (status IN
        ('MATCHED','AMOUNT_MISMATCH','FEE_MISMATCH','MISSING_INTERNAL','MISSING_PROVIDER','PENDING')),
    expected_amount_krw BIGINT,
    provider_amount_krw BIGINT,
    fee_krw BIGINT,
    fee_supply_krw BIGINT,
    fee_vat_krw BIGINT,
    payout_krw BIGINT,
    detail_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (run_id, reconciliation_key)
);

CREATE INDEX toss_settlement_reconciliation_list_idx
    ON toss_settlement_reconciliation (run_id, status, created_at, id);
