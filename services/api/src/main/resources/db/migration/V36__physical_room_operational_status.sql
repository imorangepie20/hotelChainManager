ALTER TABLE physical_room
    ADD COLUMN operational_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN operational_reason VARCHAR(500),
    ADD COLUMN expected_recovery_at TIMESTAMPTZ,
    ADD COLUMN operational_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN operational_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE physical_room
SET operational_status = 'OUT_OF_SERVICE',
    operational_reason = '기존 판매 중지 상태 이관',
    housekeeping_status = 'NEEDS_CLEANING'
WHERE housekeeping_status = 'OUT_OF_SERVICE';

ALTER TABLE physical_room DROP CONSTRAINT physical_room_housekeeping_status_check;
ALTER TABLE physical_room ADD CONSTRAINT physical_room_housekeeping_status_check
    CHECK (housekeeping_status IN ('CLEAN', 'NEEDS_CLEANING'));
ALTER TABLE physical_room ADD CONSTRAINT physical_room_operational_status_check
    CHECK (operational_status IN ('AVAILABLE', 'INSPECTION_REQUIRED', 'OUT_OF_SERVICE'));
ALTER TABLE physical_room ADD CONSTRAINT physical_room_operational_reason_check CHECK (
    (operational_status = 'AVAILABLE' AND operational_reason IS NULL AND expected_recovery_at IS NULL)
    OR (operational_status <> 'AVAILABLE' AND NULLIF(BTRIM(operational_reason), '') IS NOT NULL)
);

CREATE TABLE physical_room_operational_event (
    id UUID PRIMARY KEY,
    physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    previous_status VARCHAR(30) NOT NULL CHECK (previous_status IN ('AVAILABLE', 'INSPECTION_REQUIRED', 'OUT_OF_SERVICE')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('AVAILABLE', 'INSPECTION_REQUIRED', 'OUT_OF_SERVICE')),
    previous_reason VARCHAR(500),
    reason VARCHAR(500) NOT NULL CHECK (NULLIF(BTRIM(reason), '') IS NOT NULL),
    previous_expected_recovery_at TIMESTAMPTZ,
    expected_recovery_at TIMESTAMPTZ,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (physical_room_id, idempotency_key)
);

CREATE INDEX physical_room_operational_event_room_created_idx
    ON physical_room_operational_event (physical_room_id, created_at DESC);

CREATE TABLE checked_in_room_move (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    previous_physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    previous_room_number VARCHAR(30) NOT NULL,
    physical_room_id UUID NOT NULL REFERENCES physical_room(id),
    room_number VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (NULLIF(BTRIM(reason), '') IS NOT NULL),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key)
);

CREATE INDEX checked_in_room_move_reservation_created_idx
    ON checked_in_room_move (reservation_id, created_at DESC);
