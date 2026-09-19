-- 본사가 객실 유형을 추가할 때 같은 요청의 중복 생성을 막는 멱원 테이블.
-- idempotency_key는 같은 키의 재호출을, request_hash는 staff_id·hotel_id·본문이 같은
-- 재시도를 식별한다. 쓰기는 지점 행 잠금으로 직렬화하므로 두 값 모두 직원·지점 범위에서 조회한다.
CREATE TABLE room_type_command (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (hotel_id, staff_id, idempotency_key)
);

CREATE INDEX room_type_command_request_hash_idx
    ON room_type_command (hotel_id, staff_id, request_hash);

CREATE INDEX room_type_command_room_type_idx
    ON room_type_command (room_type_id);
