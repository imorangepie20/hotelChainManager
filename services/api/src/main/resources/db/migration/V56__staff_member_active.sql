-- 직원의 활성 상태를 보관한다. 비활성 직원은 로그인과 세션 사용이 거부된다.
-- additive다. 기존 행은 모두 활성(true)이므로 기존 동작을 변경하지 않는다.
ALTER TABLE staff_member ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- 비활성 직원이 남겨둔 세션을 즉시 끊기 위한 인덱스다.
CREATE INDEX IF NOT EXISTS staff_session_staff_id_idx ON staff_session (staff_id);
