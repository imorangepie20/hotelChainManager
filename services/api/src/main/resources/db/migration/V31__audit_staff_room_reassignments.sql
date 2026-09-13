CREATE TABLE reservation_room_assignment_change (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    previous_physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    previous_room_number VARCHAR(30) NOT NULL,
    physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    room_number VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key)
);
