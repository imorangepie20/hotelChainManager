CREATE TABLE reservation_party_change (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    previous_adults INTEGER NOT NULL CHECK (previous_adults > 0),
    previous_children INTEGER NOT NULL CHECK (previous_children >= 0),
    adults INTEGER NOT NULL CHECK (adults > 0),
    children INTEGER NOT NULL CHECK (children >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key)
);
