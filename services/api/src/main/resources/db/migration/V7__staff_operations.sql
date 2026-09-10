CREATE TABLE physical_room (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    room_number VARCHAR(30) NOT NULL,
    housekeeping_status VARCHAR(30) NOT NULL DEFAULT 'CLEAN' CHECK (housekeeping_status IN ('CLEAN', 'NEEDS_CLEANING', 'OUT_OF_SERVICE')),
    UNIQUE (hotel_id, room_number)
);

CREATE TABLE reservation_room_assignment (
    reservation_id UUID NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reservation_id, physical_room_id)
);

CREATE INDEX reservation_room_assignment_room_idx ON reservation_room_assignment (physical_room_id);
