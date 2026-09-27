CREATE TABLE ai_telemetry_event (
    event_id UUID PRIMARY KEY,
    event_hash CHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    model VARCHAR(100),
    outcome VARCHAR(32),
    elapsed_ms NUMERIC(12, 2),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ai_telemetry_event_type
        CHECK (event_type IN ('LLM_CALL', 'POLICY_VIOLATION')),
    CONSTRAINT ck_ai_telemetry_event_hash
        CHECK (event_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_telemetry_event_shape CHECK (
        (event_type = 'LLM_CALL'
            AND model IS NOT NULL
            AND btrim(model) <> ''
            AND outcome IS NOT NULL
            AND outcome IN ('success', 'schema_rejected', 'unparsable', 'empty_response', 'api_error', 'no_key')
            AND elapsed_ms IS NOT NULL
            AND elapsed_ms >= 0
            AND elapsed_ms <= 600000)
        OR
        (event_type = 'POLICY_VIOLATION'
            AND model IS NULL
            AND outcome IS NULL
            AND elapsed_ms IS NULL)
    )
);

CREATE INDEX idx_ai_telemetry_event_occurred_at
    ON ai_telemetry_event (occurred_at DESC)
    INCLUDE (event_type, model, outcome, elapsed_ms);
