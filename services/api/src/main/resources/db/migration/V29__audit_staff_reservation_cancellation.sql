ALTER TABLE cancellation_attempt
    ADD COLUMN actor_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN staff_id UUID REFERENCES staff_member(id);

ALTER TABLE cancellation_attempt
    ADD CONSTRAINT cancellation_attempt_actor_check CHECK (
        (actor_type = 'CUSTOMER' AND staff_id IS NULL)
        OR (actor_type = 'STAFF' AND staff_id IS NOT NULL)
    );
