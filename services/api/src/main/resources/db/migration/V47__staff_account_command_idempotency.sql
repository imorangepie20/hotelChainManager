-- 본사가 직원을 만들 때 같은 요청의 중복 생성을 막는 멱원 테이블.
-- staff_id와 idempotency_key로 같은 요청을 다시 식별한다.
CREATE TABLE staff_account_command (
    id UUID PRIMARY KEY,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    created_staff_id UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (staff_id, idempotency_key)
);

CREATE INDEX staff_account_command_created_staff_idx
    ON staff_account_command (created_staff_id);
