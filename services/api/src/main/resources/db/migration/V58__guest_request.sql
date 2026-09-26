-- 고객이 예약과 관련해 호텔에 보내는 요청·문의를 저장한다.
-- 예약 변경·취소는 reservation_change_request·cancellation_attempt가 이미 처리하므로
-- 이 표는 그 외의 요청(객실 요청, 일반 문의, 환불 문의, 기타)을 받는다.
-- additive하다. 기존 표를 변경하지 않는다.
CREATE TABLE guest_request (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    -- 요청이 예약에 묶여 있으면 채운다. 일반 문의는 비워둔다.
    reservation_id UUID REFERENCES reservation(id),
    request_type VARCHAR(30) NOT NULL CHECK (request_type IN (
        'ROOM_REQUEST', 'AMENITY_REQUEST', 'REFUND_INQUIRY', 'GENERAL_INQUIRY', 'OTHER'
    )),
    subject VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    -- 고객이 남긴 연락처. 예약이 없는 일반 문의는 여기로 답변한다.
    guest_name VARCHAR(100) NOT NULL,
    guest_email VARCHAR(254) NOT NULL,
    guest_phone VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'
    )),
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    assigned_to UUID REFERENCES staff_member(id),
    resolution_note TEXT,
    -- 같은 요청이 두 번 접수되지 않게 한다.
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX guest_request_idempotency_idx
    ON guest_request (idempotency_key, request_hash);

CREATE INDEX guest_request_hotel_status_idx
    ON guest_request (hotel_id, status, created_at DESC);

CREATE INDEX guest_request_reservation_idx
    ON guest_request (reservation_id);

-- 요청에 직원이 남긴 답변·상태 전환 이력을 저장한다.
CREATE TABLE guest_request_event (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES guest_request(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN (
        'CREATED', 'ASSIGNED', 'STATUS_CHANGED', 'NOTE_ADDED', 'CLOSED'
    )),
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    actor_staff_id UUID REFERENCES staff_member(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX guest_request_event_request_idx
    ON guest_request_event (request_id, created_at, id);

-- 직원이 읽는 요청에 지점 이름과 담당자 이름을 함께 보여주기 위한 뷰다.
-- 조인을 매번 서비스에서 짜지 않고 한 곳에서 정의한다.
CREATE VIEW guest_request_detail AS
select r.id, r.hotel_id, h.name as hotel_name, r.reservation_id, r.request_type,
       r.subject, r.body, r.guest_name, r.guest_email, r.guest_phone,
       r.status, r.priority, r.assigned_to,
       s.display_name as assigned_display_name, r.created_at, r.updated_at
  from guest_request r
  join hotel h on h.id = r.hotel_id
  left join staff_member s on s.id = r.assigned_to;
