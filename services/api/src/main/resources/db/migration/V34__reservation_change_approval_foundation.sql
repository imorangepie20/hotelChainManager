ALTER TABLE reservation
    ADD COLUMN operation_revision BIGINT NOT NULL DEFAULT 0 CHECK (operation_revision >= 0);

CREATE TABLE reservation_change_request (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    base_operation_revision BIGINT NOT NULL CHECK (base_operation_revision >= 0),
    status VARCHAR(40) NOT NULL CHECK (status IN (
        'PENDING_APPROVAL',
        'APPROVED',
        'AWAITING_PAYMENT',
        'REFUND_PENDING',
        'READY_TO_APPLY',
        'APPLYING',
        'COMPLETED',
        'REJECTED',
        'CANCELLED',
        'EXPIRED',
        'RECONCILIATION_REQUIRED'
    )),
    settlement_direction VARCHAR(20) NOT NULL CHECK (settlement_direction IN ('CHARGE', 'REFUND', 'NONE')),
    requested_by UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    previous_check_in DATE NOT NULL,
    previous_check_out DATE NOT NULL,
    previous_room_type_id UUID NOT NULL REFERENCES room_type(id),
    previous_rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    target_check_in DATE NOT NULL,
    target_check_out DATE NOT NULL,
    target_room_type_id UUID NOT NULL REFERENCES room_type(id),
    target_rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    rooms INTEGER NOT NULL CHECK (rooms > 0),
    adults INTEGER NOT NULL CHECK (adults > 0),
    children INTEGER NOT NULL CHECK (children >= 0),
    current_quote_id UUID,
    approval_limit_krw BIGINT CHECK (approval_limit_krw >= 0),
    approval_expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    error_code VARCHAR(80),
    error_summary VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key),
    CHECK (previous_check_out > previous_check_in),
    CHECK (target_check_out > target_check_in)
);

CREATE UNIQUE INDEX reservation_change_request_one_active_idx
    ON reservation_change_request (reservation_id)
    WHERE status NOT IN ('COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED');

CREATE INDEX reservation_change_request_hotel_status_idx
    ON reservation_change_request (hotel_id, status, created_at DESC);

CREATE TABLE reservation_change_quote (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES reservation_change_request(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision > 0),
    previous_total_krw BIGINT NOT NULL CHECK (previous_total_krw >= 0),
    total_krw BIGINT NOT NULL CHECK (total_krw >= 0),
    difference_krw BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    rooms INTEGER NOT NULL CHECK (rooms > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (request_id, revision),
    UNIQUE (id, request_id)
);

CREATE TABLE reservation_change_quote_night (
    quote_id UUID NOT NULL REFERENCES reservation_change_quote(id) ON DELETE CASCADE,
    stay_date DATE NOT NULL,
    amount_krw BIGINT NOT NULL CHECK (amount_krw >= 0),
    PRIMARY KEY (quote_id, stay_date)
);

ALTER TABLE reservation_change_request
    ADD CONSTRAINT reservation_change_request_current_quote_fk
    FOREIGN KEY (current_quote_id, id)
    REFERENCES reservation_change_quote(id, request_id);

CREATE TABLE reservation_change_approval (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES reservation_change_request(id) ON DELETE CASCADE,
    quote_id UUID NOT NULL,
    decision_type VARCHAR(30) NOT NULL CHECK (decision_type IN (
        'AUTO_APPROVED', 'HQ_APPROVED', 'REJECTED', 'INVALIDATED'
    )),
    decided_by UUID NOT NULL REFERENCES staff_member(id),
    decided_role VARCHAR(30) NOT NULL CHECK (decided_role IN ('HQ_ADMIN', 'BRANCH_STAFF')),
    settlement_direction VARCHAR(20) NOT NULL CHECK (settlement_direction IN ('CHARGE', 'REFUND', 'NONE')),
    max_abs_difference_krw BIGINT NOT NULL CHECK (max_abs_difference_krw >= 0),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (quote_id, request_id)
        REFERENCES reservation_change_quote(id, request_id)
);

CREATE INDEX reservation_change_approval_request_idx
    ON reservation_change_approval (request_id, created_at DESC);

CREATE TABLE reservation_change_event (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES reservation_change_request(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    from_status VARCHAR(40) CHECK (from_status IS NULL OR from_status IN (
        'PENDING_APPROVAL', 'APPROVED', 'AWAITING_PAYMENT', 'REFUND_PENDING',
        'READY_TO_APPLY', 'APPLYING', 'COMPLETED', 'REJECTED', 'CANCELLED',
        'EXPIRED', 'RECONCILIATION_REQUIRED'
    )),
    to_status VARCHAR(40) CHECK (to_status IS NULL OR to_status IN (
        'PENDING_APPROVAL', 'APPROVED', 'AWAITING_PAYMENT', 'REFUND_PENDING',
        'READY_TO_APPLY', 'APPLYING', 'COMPLETED', 'REJECTED', 'CANCELLED',
        'EXPIRED', 'RECONCILIATION_REQUIRED'
    )),
    actor_staff_id UUID REFERENCES staff_member(id),
    dedupe_key VARCHAR(160),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX reservation_change_event_request_idx
    ON reservation_change_event (request_id, created_at, id);

CREATE UNIQUE INDEX reservation_change_event_dedupe_idx
    ON reservation_change_event (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
