-- 본사가 두 번째 요금제를 만들고 이름을 바꿀 때 멱원 키와 요청 지문을 보관한다.
-- 같은 키 재호출과 같은 내용의 다른 키 재시도를 모두 잡는다.
-- rate_plan이 room_type을 참조하므로 rate_plan_id는 NOT NULL이다.
-- additive다. 기존 표의 데이터는 그대로 둔다.

-- 요금제가 여러 개일 때 어느 것이 "기본"인지 정해야 한다.
-- rp.id는 UUID라 삽입 순서를 알 수 없으므로 created_at으로 기본 요금제를
-- 가장 오래된 것으로 고정한다. 기존 행은 모두 이 마이그레이션 시각으로
-- 채워지므로 서로 순서가 바뀌지 않는다.
ALTER TABLE rate_plan
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE rate_plan
   SET created_at = now()
 WHERE created_at IS NULL;

CREATE TABLE IF NOT EXISTS rate_plan_command (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    kind VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- 같은 직원의 같은 멱원 키 재호출을 한 건으로 식별한다.
CREATE UNIQUE INDEX IF NOT EXISTS rate_plan_command_idempotency_idx
    ON rate_plan_command (hotel_id, staff_id, idempotency_key);

CREATE INDEX IF NOT EXISTS rate_plan_command_request_hash_idx
    ON rate_plan_command (hotel_id, staff_id, request_hash);
