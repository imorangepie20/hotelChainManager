CREATE TABLE staff_member (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('HQ_ADMIN', 'BRANCH_STAFF')),
    hotel_id UUID REFERENCES hotel(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((role = 'HQ_ADMIN' AND hotel_id IS NULL) OR (role = 'BRANCH_STAFF' AND hotel_id IS NOT NULL))
);

CREATE TABLE staff_session (
    token_hash CHAR(64) PRIMARY KEY,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX staff_session_staff_id_idx ON staff_session (staff_id);
