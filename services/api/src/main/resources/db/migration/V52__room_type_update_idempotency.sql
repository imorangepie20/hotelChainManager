-- 본사가 객실 유형을 수정할 때도 room_type_command를 쓴다.
-- 생성과 수정을 kind로 구분하고, (hotel_id, staff_id, kind, idempotency_key) 단위로
-- 같은 요청을 다시 식별한다. 기존 행은 모두 kind가 없는 생성 요청이므로 'CREATE'로 채운다.
-- additive하다.
ALTER TABLE room_type_command
    ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'CREATE',
    ADD COLUMN IF NOT EXISTS previous_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS previous_max_occupancy INTEGER;

UPDATE room_type_command
   SET kind = 'CREATE'
 WHERE kind IS NULL OR kind = '';

-- 동일한 동작의 동일한 멱원 키 재호출을 한 건으로 식별한다.
CREATE UNIQUE INDEX IF NOT EXISTS room_type_command_kind_idempotency_idx
    ON room_type_command (hotel_id, staff_id, kind, idempotency_key);
