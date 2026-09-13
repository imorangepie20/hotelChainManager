CREATE TABLE reservation_guest_change (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    previous_guest_name VARCHAR(100) NOT NULL,
    previous_guest_email VARCHAR(254) NOT NULL,
    guest_name VARCHAR(100) NOT NULL,
    guest_email VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key)
);
