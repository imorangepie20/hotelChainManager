ALTER TABLE physical_room_operational_event
    ADD COLUMN resulting_housekeeping_status VARCHAR(30),
    ADD COLUMN resulting_version BIGINT;

ALTER TABLE physical_room_operational_event
    ADD CONSTRAINT physical_room_operational_event_resulting_housekeeping_check
    CHECK (resulting_housekeeping_status IS NULL
        OR resulting_housekeeping_status IN ('CLEAN', 'NEEDS_CLEANING'));

ALTER TABLE physical_room_operational_event
    ADD CONSTRAINT physical_room_operational_event_resulting_version_check
    CHECK (resulting_version IS NULL OR resulting_version > 0);
