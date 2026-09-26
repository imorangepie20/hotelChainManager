-- 본사가 일자 재고를 조정할 때 멱원 키와 요청 지문을 보관한다.
-- 응답 유실 뒤 같은 키로 재호출해도 재고가 두 번 바뀌지 않게 한다.
-- additive하다. 기존 표를 변경하지 않는다.
CREATE TABLE IF NOT EXISTS inventory_command (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS inventory_command_request_hash_idx
    ON inventory_command (hotel_id, staff_id, request_hash);

CREATE INDEX IF NOT EXISTS inventory_command_room_type_idx
    ON inventory_command (room_type_id);

-- 동일한 동작의 동일한 멱원 키 재호출을 한 건으로 식별한다.
CREATE UNIQUE INDEX IF NOT EXISTS inventory_command_idempotency_idx
    ON inventory_command (hotel_id, staff_id, idempotency_key);
