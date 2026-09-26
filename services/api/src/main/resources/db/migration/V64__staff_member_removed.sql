-- 본사가 직원을 삭제할 때 쓴다. 삭제는 행을 지우는 것이 아니라
-- 식별자를 영구적으로 비우는 것이다. staff_member 행은 남겨서
-- 감사 이력·예약 변경 승인·콘텐츠 발행 이력의 외래키 참조가
-- 끊기지 않게 한다.
-- additive다. 기존 행은 모두 HQ_ADMIN·HQ_EDITOR·HQ_PUBLISHER·
-- BRANCH_STAFF 중 하나이므로 값이 바뀌지 않는다.
ALTER TABLE staff_member DROP CONSTRAINT IF EXISTS staff_member_role_check;

ALTER TABLE staff_member
    ADD CONSTRAINT staff_member_role_check
        CHECK (role IN ('HQ_ADMIN', 'HQ_EDITOR', 'HQ_PUBLISHER', 'BRANCH_STAFF', 'REMOVED'));

-- 삭제된 직원은 지점을 가질 필요가 없다. hotel_id를 비운 뒤에
-- staff_member_hotel_scope_check를 만족해야 한다.
ALTER TABLE staff_member DROP CONSTRAINT IF EXISTS staff_member_hotel_scope_check;

ALTER TABLE staff_member
    ADD CONSTRAINT staff_member_hotel_scope_check
        CHECK (
            (role IN ('HQ_ADMIN', 'HQ_EDITOR', 'HQ_PUBLISHER') AND hotel_id IS NULL)
            OR (role = 'BRANCH_STAFF' AND hotel_id IS NOT NULL)
            OR (role = 'REMOVED' AND hotel_id IS NULL)
        );
