-- 본사가 객실 유형을 지울 때도 room_type_command를 쓴다.
-- room_type_command.room_type_id가 room_type을 참조하므로 유형을 지운 뒤에는
-- 멱원 행을 room_type_id로 유지할 수 없다. 삭제 기록은 room_type_id를 비우고
-- deleted_room_type_name으로 무엇을 지웠는지 보관한다.
-- additive다. 기존 행은 모두 room_type_id가 있으므로 값의 변경이 없다.
ALTER TABLE room_type_command
    ADD COLUMN IF NOT EXISTS deleted_room_type_name VARCHAR(100);

-- V46이 room_type_id를 NOT NULL로 만들었다. 삭제 기록은 유형 행이 없으므로
-- NOT NULL을 빼야 한다. 기존 CREATE·UPDATE 기록은 값을 가지고 있어
-- 이 변경이 빈 값을 만들지 않는다.
ALTER TABLE room_type_command
    ALTER COLUMN room_type_id DROP NOT NULL;

-- 삭제 기록은 room_type_id가 비어 있다. 기존 CREATE·UPDATE 기록과 섞이지 않게
-- 부분 유일 인덱스로 (hotel_id, staff_id, kind, idempotency_key)를 유지한다.
CREATE UNIQUE INDEX IF NOT EXISTS room_type_command_deleted_idempotency_idx
    ON room_type_command (hotel_id, staff_id, kind, idempotency_key)
    WHERE room_type_id IS NULL;

-- 종료된 예약은 과거 운영 기록이므로 삭제 후에도 보존한다.
-- 예약이 rate_plan_id·room_type_id를 NOT NULL로 참조하므로 유형을 지울 때
-- 요금제 행은 지우지 않고 room_type_id 연결만 끊는다.
-- 요금·재고와 달리 요금제·객실 유형은 예약 스냅샷의 근거이므로 남겨야 한다.
ALTER TABLE rate_plan
    ALTER COLUMN room_type_id DROP NOT NULL;

ALTER TABLE reservation
    ALTER COLUMN room_type_id DROP NOT NULL;
