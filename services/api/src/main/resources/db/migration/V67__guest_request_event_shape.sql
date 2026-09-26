-- 변경 이벤트는 행위자와 실제 전후 차이를 반드시 가져야 한다.
-- 기존 CREATED·NOTE_ADDED·CLOSED 행의 형태는 바꾸지 않는다.
ALTER TABLE guest_request_event
    ADD CONSTRAINT guest_request_event_change_shape_check
        CHECK (
            (event_type = 'ASSIGNED'
                AND actor_staff_id IS NOT NULL
                AND to_assigned_to IS NOT NULL
                AND from_assigned_to IS DISTINCT FROM to_assigned_to)
            OR (event_type = 'PRIORITY_CHANGED'
                AND actor_staff_id IS NOT NULL
                AND from_priority IS NOT NULL
                AND to_priority IS NOT NULL
                AND from_priority IS DISTINCT FROM to_priority)
            OR (event_type = 'STATUS_CHANGED'
                AND actor_staff_id IS NOT NULL
                AND from_status IS NOT NULL
                AND to_status IS NOT NULL
                AND from_status IS DISTINCT FROM to_status)
            OR event_type NOT IN ('ASSIGNED', 'PRIORITY_CHANGED', 'STATUS_CHANGED')
        );
