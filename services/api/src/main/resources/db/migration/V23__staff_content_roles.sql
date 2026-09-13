ALTER TABLE staff_member DROP CONSTRAINT IF EXISTS staff_member_role_check;
ALTER TABLE staff_member DROP CONSTRAINT IF EXISTS staff_member_check;

ALTER TABLE staff_member
    ADD CONSTRAINT staff_member_role_check
        CHECK (role IN ('HQ_ADMIN', 'HQ_EDITOR', 'HQ_PUBLISHER', 'BRANCH_STAFF')),
    ADD CONSTRAINT staff_member_hotel_scope_check
        CHECK (
            (role IN ('HQ_ADMIN', 'HQ_EDITOR', 'HQ_PUBLISHER') AND hotel_id IS NULL)
            OR (role = 'BRANCH_STAFF' AND hotel_id IS NOT NULL)
        );
