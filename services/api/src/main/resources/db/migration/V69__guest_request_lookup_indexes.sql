-- 접수 중복 확인과 본사 전체 OPEN 알림의 최신순 조회를 순차 스캔 없이 처리한다.
CREATE INDEX guest_request_request_hash_idx
    ON guest_request (request_hash);

CREATE INDEX guest_request_status_created_idx
    ON guest_request (status, created_at DESC, id DESC)
    INCLUDE (hotel_id, request_type);
