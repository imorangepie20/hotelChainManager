-- 본사가 변경하는 체인 공통 정책의 revision 이력.
-- 현재값은 항상 가장 최근 revision에서 읽는다. 행이 없으면 코드 기본값을 쓴다.
-- reservation.policy_snapshot을 변경하지 않는 additive 마이그레이션이다.
CREATE TABLE policy_revision (
    id UUID PRIMARY KEY,
    key VARCHAR(64) NOT NULL,
    refund_cutoff_days_before INTEGER NOT NULL,
    refund_cutoff_local_time VARCHAR(5) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 같은 멱원 키의 재호출은 저장된 revision을 돌려준다.
CREATE UNIQUE INDEX policy_revision_key_idempotency_idx
    ON policy_revision (key, idempotency_key);

-- 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 revision을 돌려준다.
CREATE INDEX policy_revision_key_hash_idx
    ON policy_revision (key, request_hash);

CREATE INDEX policy_revision_latest_idx
    ON policy_revision (key, created_at DESC);
