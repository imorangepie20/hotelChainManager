CREATE TABLE hotel (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    region VARCHAR(50) NOT NULL,
    timezone VARCHAR(50) NOT NULL
);

CREATE TABLE room_type (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    name VARCHAR(100) NOT NULL,
    max_occupancy INTEGER NOT NULL CHECK (max_occupancy > 0)
);

CREATE TABLE rate_plan (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    name VARCHAR(100) NOT NULL,
    breakfast_included BOOLEAN NOT NULL,
    policy_version VARCHAR(50) NOT NULL
);

CREATE TABLE rate_day (
    rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    stay_date DATE NOT NULL,
    amount_krw INTEGER NOT NULL CHECK (amount_krw >= 0),
    PRIMARY KEY (rate_plan_id, stay_date)
);

CREATE TABLE inventory_day (
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    stay_date DATE NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity >= 0),
    held INTEGER NOT NULL DEFAULT 0 CHECK (held >= 0),
    confirmed INTEGER NOT NULL DEFAULT 0 CHECK (confirmed >= 0),
    PRIMARY KEY (room_type_id, stay_date),
    CHECK (held + confirmed <= capacity)
);

