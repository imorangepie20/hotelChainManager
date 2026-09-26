-- 본사가 일자별 요금을 바꿀 때 멱원 키와 요청 지문을 보관한다.
-- 같은 키 재호출과 같은 내용의 다른 키 재시도를 모두 잡는다.
-- additive다. 기존 표를 변경하지 않는다.
CREATE TABLE IF NOT EXISTS rate_command (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS rate_command_request_hash_idx
    ON rate_command (hotel_id, staff_id, request_hash);

CREATE INDEX IF NOT EXISTS rate_command_room_type_idx
    ON rate_command (room_type_id);

-- 같은 직원의 같은 멱원 키 재호출을 한 건으로 식별한다.
CREATE UNIQUE INDEX IF NOT EXISTS rate_command_idempotency_idx
    ON rate_command (hotel_id, staff_id, idempotency_key);
