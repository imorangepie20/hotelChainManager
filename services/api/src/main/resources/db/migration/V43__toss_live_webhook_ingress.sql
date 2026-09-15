ALTER TABLE toss_webhook_lookup
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'TOSS_TEST';

ALTER TABLE toss_webhook_lookup DROP CONSTRAINT toss_webhook_lookup_pkey;
ALTER TABLE toss_webhook_lookup
    ADD CONSTRAINT toss_webhook_lookup_pkey PRIMARY KEY (provider, order_id);

CREATE TABLE toss_webhook_delivery (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    transmission_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, transmission_id)
);

CREATE INDEX toss_webhook_delivery_order_idx
    ON toss_webhook_delivery (provider, order_id, received_at DESC);
