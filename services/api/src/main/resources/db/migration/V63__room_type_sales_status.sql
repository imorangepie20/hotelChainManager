-- 본사가 객실 유형의 날짜 구간 판매를 중지·재개한다.
-- 총량(inventory_day.capacity)과 별개다. 중지는 신규 판매만 막고
-- 기존 예약은 그대로 둬서 재개하면 중지 전과 같은 재고가 돌아온다.
-- additive다. 기존 표를 변경하지 않는다.

CREATE TABLE IF NOT EXISTS room_type_sales_status (
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    stay_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'STOPPED')),
    PRIMARY KEY (room_type_id, stay_date)
);

CREATE INDEX IF NOT EXISTS room_type_sales_status_room_type_idx
    ON room_type_sales_status (room_type_id, status);

CREATE TABLE IF NOT EXISTS room_type_sales_status_command (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    staff_id UUID NOT NULL REFERENCES staff_member(id),
    room_type_id UUID NOT NULL REFERENCES room_type(id),
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- 같은 직원의 같은 멱원 키 재호출을 한 건으로 취급한다.
CREATE UNIQUE INDEX IF NOT EXISTS room_type_sales_status_command_idempotency_idx
    ON room_type_sales_status_command (hotel_id, staff_id, idempotency_key);

-- 응답 유실 뒤 같은 내용을 새 멱원 키로 보내면 같은 결과를 돌려준다.
CREATE UNIQUE INDEX IF NOT EXISTS room_type_sales_status_command_request_hash_idx
    ON room_type_sales_status_command (hotel_id, staff_id, request_hash);

CREATE INDEX IF NOT EXISTS room_type_sales_status_command_room_type_idx
    ON room_type_sales_status_command (room_type_id);
