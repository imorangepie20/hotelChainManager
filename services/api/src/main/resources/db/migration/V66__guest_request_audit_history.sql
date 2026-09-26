-- 고객 요청의 담당자·우선순위 변경 당시 값을 보존한다.
ALTER TABLE guest_request_event
    ADD COLUMN from_assigned_to UUID REFERENCES staff_member(id),
    ADD COLUMN to_assigned_to UUID REFERENCES staff_member(id),
    ADD COLUMN from_priority VARCHAR(10),
    ADD COLUMN to_priority VARCHAR(10);

ALTER TABLE guest_request_event
    DROP CONSTRAINT IF EXISTS guest_request_event_event_type_check;

ALTER TABLE guest_request_event
    ADD CONSTRAINT guest_request_event_event_type_check
        CHECK (event_type IN (
            'CREATED', 'ASSIGNED', 'PRIORITY_CHANGED', 'STATUS_CHANGED', 'NOTE_ADDED', 'CLOSED'
        ));

ALTER TABLE guest_request_event
    ADD CONSTRAINT guest_request_event_from_priority_check
        CHECK (from_priority IS NULL OR from_priority IN ('LOW', 'NORMAL', 'HIGH')),
    ADD CONSTRAINT guest_request_event_to_priority_check
        CHECK (to_priority IS NULL OR to_priority IN ('LOW', 'NORMAL', 'HIGH'));

-- 직원 변경 명령의 멱등 응답과 같은 키의 다른 본문 충돌을 구분한다.
CREATE TABLE guest_request_transition_command (
    request_id UUID NOT NULL REFERENCES guest_request(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, idempotency_key)
);

-- 전사 감사 조회는 최신 시각부터 원본 이벤트 UUID로 안정 정렬한다.
CREATE INDEX guest_request_event_audit_created_idx
    ON guest_request_event (created_at DESC, id DESC)
    INCLUDE (request_id, event_type, actor_staff_id);
