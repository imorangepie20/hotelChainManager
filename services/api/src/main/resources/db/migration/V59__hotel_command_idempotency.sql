-- 본사가 지점을 추가·수정할 때 같은 요청의 중복 처리를 막는 멱원 테이블.
-- idempotency_key는 같은 키의 재호출을, request_hash는 staff_id·본문이 같은 재시도를 식별한다.
-- kind로 생성·수정을 구분하고 (staff_id, kind, idempotency_key) 단위로 같은 요청을 다시 찾는다.
-- additive하다. 기존 표를 변경하지 않는다.
CREATE TABLE hotel_command (
    id UUID PRIMARY KEY,
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    kind VARCHAR(32) NOT NULL DEFAULT 'CREATE',
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    previous_name VARCHAR(100),
    previous_region VARCHAR(50),
    previous_timezone VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (staff_id, kind, idempotency_key)
);

CREATE INDEX hotel_command_request_hash_idx
    ON hotel_command (staff_id, kind, request_hash);

CREATE INDEX hotel_command_hotel_idx
    ON hotel_command (hotel_id);
