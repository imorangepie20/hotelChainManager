CREATE TABLE reservation (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    adults INTEGER NOT NULL CHECK (adults > 0),
    children INTEGER NOT NULL CHECK (children >= 0),
    rooms INTEGER NOT NULL CHECK (rooms > 0),
    status VARCHAR(30) NOT NULL,
    total_krw BIGINT NOT NULL CHECK (total_krw >= 0),
    currency CHAR(3) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    guest_name VARCHAR(100) NOT NULL,
    guest_email VARCHAR(254) NOT NULL,
    management_token_hash CHAR(64) NOT NULL,
    policy_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (check_out > check_in)
);

CREATE TABLE reservation_night (
    reservation_id UUID NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    stay_date DATE NOT NULL,
    amount_krw INTEGER NOT NULL CHECK (amount_krw >= 0),
    PRIMARY KEY (reservation_id, stay_date)
);

CREATE TABLE reservation_idempotency (
    management_token_hash CHAR(64) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (management_token_hash, idempotency_key)
);

CREATE INDEX reservation_access_idx ON reservation (id, management_token_hash);
