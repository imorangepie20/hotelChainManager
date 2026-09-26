-- 취소·환불 정책을 체인과 지점 scope별 append-only revision으로 보존한다.
-- 기존 policy_revision과 reservation.policy_snapshot은 변경하지 않는다.
CREATE TABLE cancellation_policy_revision (
    id UUID PRIMARY KEY,
    hotel_id UUID REFERENCES hotel(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('SET', 'INHERIT')),
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    legacy_revision_id UUID REFERENCES policy_revision(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (action = 'SET' OR hotel_id IS NOT NULL)
);

CREATE UNIQUE INDEX cancellation_policy_revision_scope_number_idx
    ON cancellation_policy_revision (
        COALESCE(hotel_id, '00000000-0000-0000-0000-000000000000'::uuid), revision_number);

CREATE UNIQUE INDEX cancellation_policy_revision_scope_idempotency_idx
    ON cancellation_policy_revision (
        COALESCE(hotel_id, '00000000-0000-0000-0000-000000000000'::uuid), idempotency_key);

CREATE INDEX cancellation_policy_revision_scope_latest_idx
    ON cancellation_policy_revision (
        COALESCE(hotel_id, '00000000-0000-0000-0000-000000000000'::uuid), created_at DESC, id DESC);

CREATE TABLE cancellation_refund_rule (
    revision_id UUID NOT NULL REFERENCES cancellation_policy_revision(id) ON DELETE CASCADE,
    rule_order INTEGER NOT NULL CHECK (rule_order BETWEEN 0 AND 9),
    days_before INTEGER NOT NULL CHECK (days_before BETWEEN 0 AND 30),
    cutoff_local_time VARCHAR(5) NOT NULL,
    refund_percent INTEGER NOT NULL CHECK (refund_percent BETWEEN 0 AND 100),
    PRIMARY KEY (revision_id, rule_order),
    UNIQUE (revision_id, days_before, cutoff_local_time)
);

-- 현재 정책과 같은 본문이라 revision을 만들지 않은 요청도 멱원 키의 의미를
-- 고정한다. 이후 정책이 바뀐 뒤 동일 키가 재전송돼 과거 정책으로 되돌아가는
-- 일을 막기 위한 command receipt다.
CREATE TABLE cancellation_policy_noop_command (
    hotel_id UUID REFERENCES hotel(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    revision_id UUID,
    revision_number INTEGER NOT NULL CHECK (revision_number >= 0),
    action VARCHAR(16) NOT NULL CHECK (action IN ('SET', 'INHERIT')),
    rules_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX cancellation_policy_noop_command_scope_key_idx
    ON cancellation_policy_noop_command (
        COALESCE(hotel_id, '00000000-0000-0000-0000-000000000000'::uuid), idempotency_key);
