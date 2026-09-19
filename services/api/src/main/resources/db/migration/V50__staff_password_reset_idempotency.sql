-- 본사가 직원 비밀번호를 재발급할 때 같은 요청의 중복 처리를 막는다.
-- staff_account_command를 생성뿐 아니라 재발급에도 쓰므로 kind로 동작을 구분한다.
-- 기존 행은 모두 kind가 없는 생성 요청이므로 'CREATE'로 채운다. additive하다.
ALTER TABLE staff_account_command
    ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'CREATE',
    ADD COLUMN IF NOT EXISTS password_reset_at TIMESTAMPTZ;

UPDATE staff_account_command
   SET kind = 'CREATE'
 WHERE kind IS NULL OR kind = '';

-- (staff_id, kind, idempotency_key) 단위로 같은 요청을 다시 식별한다.
CREATE UNIQUE INDEX IF NOT EXISTS staff_account_command_kind_idempotency_idx
    ON staff_account_command (staff_id, kind, idempotency_key);

-- 같은 직원의 가장 최근 재발급 시각을 빠르게 찾는다.
CREATE INDEX IF NOT EXISTS staff_account_command_password_reset_idx
    ON staff_account_command (staff_id, password_reset_at DESC)
    WHERE kind = 'RESET_PASSWORD';
