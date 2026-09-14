ALTER TABLE reservation_change_request
    ADD COLUMN request_origin VARCHAR(20) NOT NULL DEFAULT 'STAFF'
        CHECK (request_origin IN ('STAFF', 'CUSTOMER')),
    ALTER COLUMN requested_by DROP NOT NULL;

ALTER TABLE reservation_change_request
    ADD CONSTRAINT reservation_change_request_actor_check CHECK (
        (request_origin = 'STAFF' AND requested_by IS NOT NULL)
        OR (request_origin = 'CUSTOMER' AND requested_by IS NULL)
    );

CREATE TABLE reservation_change_customer_quote (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    base_operation_revision BIGINT NOT NULL CHECK (base_operation_revision >= 0),
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    adults INTEGER NOT NULL CHECK (adults > 0),
    children INTEGER NOT NULL CHECK (children >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (check_out > check_in)
);

CREATE INDEX reservation_change_customer_quote_expiry_idx
    ON reservation_change_customer_quote (expires_at);

ALTER TABLE reservation_stay_change
    ALTER COLUMN staff_id DROP NOT NULL,
    ADD COLUMN actor_origin VARCHAR(20) NOT NULL DEFAULT 'STAFF'
        CHECK (actor_origin IN ('STAFF', 'CUSTOMER')),
    ADD COLUMN previous_adults INTEGER CHECK (previous_adults > 0),
    ADD COLUMN previous_children INTEGER CHECK (previous_children >= 0),
    ADD COLUMN adults INTEGER CHECK (adults > 0),
    ADD COLUMN children INTEGER CHECK (children >= 0);
