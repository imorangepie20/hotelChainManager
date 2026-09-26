\set ON_ERROR_STOP on

\if :{?hcm_local_sample}
\else
\echo '로컬 개발 DB 전용입니다. psql -v hcm_local_sample=1 로 명시적으로 실행하세요.'
\quit 3
\endif

BEGIN;

-- 운영 통계 화면용 로컬 개발 샘플이다. 고정 키를 사용하므로 다시 실행해도 중복되지 않고,
-- 실행 시점의 서울 오늘을 기준으로 현재 7일과 이전 7일이 다시 정렬된다.
WITH hotel_seed(code, hotel_id, room_type_id, rate_plan_id, base_amount, current_daily, previous_daily) AS (
    VALUES
        ('sokcho',
         '11000000-0000-0000-0000-000000000001'::uuid,
         '21000000-0000-0000-0000-000000000001'::uuid,
         '31000000-0000-0000-0000-000000000001'::uuid,
         120000::bigint, 3, 2),
        ('seoraksan',
         '11000000-0000-0000-0000-000000000002'::uuid,
         '21000000-0000-0000-0000-000000000011'::uuid,
         '31000000-0000-0000-0000-000000000011'::uuid,
         130000::bigint, 2, 1),
        ('jeju',
         '11000000-0000-0000-0000-000000000003'::uuid,
         '21000000-0000-0000-0000-000000000021'::uuid,
         '31000000-0000-0000-0000-000000000021'::uuid,
         140000::bigint, 4, 2)
), reservation_seed AS (
    SELECT hotel.code,
           hotel.room_type_id,
           hotel.rate_plan_id,
           hotel.base_amount,
           period.day_offset,
           guest_no,
           ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - period.day_offset)::date AS created_date
      FROM hotel_seed hotel
      CROSS JOIN LATERAL (
          SELECT current_offset AS day_offset, hotel.current_daily AS daily_count
            FROM generate_series(0, 6) AS current_offset
          UNION ALL
          SELECT previous_offset AS day_offset, hotel.previous_daily AS daily_count
            FROM generate_series(7, 13) AS previous_offset
      ) period
      CROSS JOIN LATERAL generate_series(1, period.daily_count) AS guest_no
)
INSERT INTO reservation (
    id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
    status, total_krw, currency, expires_at, guest_name, guest_email, guest_phone,
    management_token_hash, policy_snapshot, created_at, operation_revision
)
SELECT md5('hcm-report-sample-reservation-' || code || '-' || day_offset || '-' || guest_no)::uuid,
       room_type_id,
       rate_plan_id,
       CASE
           WHEN day_offset = 0 AND guest_no = 1 THEN created_date
           WHEN day_offset = 0 AND guest_no = 2 THEN created_date - 2
           ELSE created_date + 15 + guest_no
       END,
       CASE
           WHEN day_offset = 0 AND guest_no = 1 THEN created_date + 2
           WHEN day_offset = 0 AND guest_no = 2 THEN created_date
           ELSE created_date + 17 + guest_no
       END,
       2,
       CASE WHEN guest_no % 3 = 0 THEN 1 ELSE 0 END,
       1,
       CASE
           WHEN day_offset = 0 AND guest_no = 2 THEN 'CHECKED_IN'
           WHEN guest_no = 2 AND day_offset % 4 = 0 THEN 'CANCELLED'
           WHEN guest_no = 2 AND day_offset % 4 = 1 THEN 'NO_SHOW'
           WHEN guest_no = 3 AND day_offset % 3 = 0 THEN 'EXPIRED'
           ELSE 'CONFIRMED'
       END,
       base_amount + guest_no * 10000 + day_offset * 1000,
       'KRW',
       ((created_date::timestamp + time '13:00') AT TIME ZONE 'Asia/Seoul') + interval '1 hour',
       '통계 샘플 ' || code || ' ' || day_offset || '-' || guest_no,
       'sample-report-' || code || '-' || day_offset || '-' || guest_no || '@example.invalid',
       '010-0000-' || lpad((day_offset * 10 + guest_no)::text, 4, '0'),
       md5('hcm-report-sample-token-' || code || '-' || day_offset || '-' || guest_no)
           || md5('hcm-report-sample-token-extra-' || code || '-' || day_offset || '-' || guest_no),
       '{}'::jsonb,
       (created_date::timestamp + time '12:00') AT TIME ZONE 'Asia/Seoul',
       0
  FROM reservation_seed
ON CONFLICT (id) DO UPDATE SET
    room_type_id = EXCLUDED.room_type_id,
    rate_plan_id = EXCLUDED.rate_plan_id,
    check_in = EXCLUDED.check_in,
    check_out = EXCLUDED.check_out,
    status = EXCLUDED.status,
    total_krw = EXCLUDED.total_krw,
    expires_at = EXCLUDED.expires_at,
    guest_name = EXCLUDED.guest_name,
    guest_email = EXCLUDED.guest_email,
    guest_phone = EXCLUDED.guest_phone,
    created_at = EXCLUDED.created_at;

-- 운영 대시보드가 각 지점의 오늘 도착·출발·배정 필요·청소 필요를 실제 API로 보여주도록
-- 테스트 객실 두 개와 오늘 출발 예약의 배정을 만든다.
WITH room_seed(hotel_code, hotel_id, room_type_id, occupied_room_id, cleaning_room_id,
               occupied_room_number, cleaning_room_number) AS (
    VALUES
        ('sokcho',
         '11000000-0000-0000-0000-000000000001'::uuid,
         '21000000-0000-0000-0000-000000000001'::uuid,
         '41000000-0000-0000-0000-000000000001'::uuid,
         '41000000-0000-0000-0000-000000000002'::uuid,
         'T901', 'T902'),
        ('seoraksan',
         '11000000-0000-0000-0000-000000000002'::uuid,
         '21000000-0000-0000-0000-000000000011'::uuid,
         '41000000-0000-0000-0000-000000000011'::uuid,
         '41000000-0000-0000-0000-000000000012'::uuid,
         'T801', 'T802'),
        ('jeju',
         '11000000-0000-0000-0000-000000000003'::uuid,
         '21000000-0000-0000-0000-000000000021'::uuid,
         '41000000-0000-0000-0000-000000000021'::uuid,
         '41000000-0000-0000-0000-000000000022'::uuid,
         'T701', 'T702')
), physical_room_seed AS (
    SELECT hotel_id, room_type_id, occupied_room_id AS room_id,
           occupied_room_number AS room_number, 'CLEAN' AS housekeeping_status
      FROM room_seed
    UNION ALL
    SELECT hotel_id, room_type_id, cleaning_room_id AS room_id,
           cleaning_room_number AS room_number, 'NEEDS_CLEANING' AS housekeeping_status
      FROM room_seed
)
INSERT INTO physical_room (id, hotel_id, room_type_id, room_number, housekeeping_status)
SELECT room_id, hotel_id, room_type_id, room_number, housekeeping_status
  FROM physical_room_seed
ON CONFLICT (id) DO UPDATE SET
    hotel_id = EXCLUDED.hotel_id,
    room_type_id = EXCLUDED.room_type_id,
    room_number = EXCLUDED.room_number,
    housekeeping_status = EXCLUDED.housekeeping_status;

-- 같은 샘플을 UI에서 배정·이동한 뒤 다시 실행해도 오늘 도착은 미배정,
-- 오늘 출발은 아래 고정 객실 한 개만 배정된 상태로 되돌린다.
WITH hotel_seed(hotel_code) AS (
    VALUES ('sokcho'), ('seoraksan'), ('jeju')
)
DELETE FROM reservation_room_assignment assignment
 WHERE assignment.reservation_id IN (
     SELECT md5('hcm-report-sample-reservation-' || hotel_code || '-0-' || guest_no)::uuid
       FROM hotel_seed
       CROSS JOIN (VALUES (1), (2)) AS guest(guest_no)
 );

WITH assignment_seed(hotel_code, physical_room_id) AS (
    VALUES
        ('sokcho', '41000000-0000-0000-0000-000000000001'::uuid),
        ('seoraksan', '41000000-0000-0000-0000-000000000011'::uuid),
        ('jeju', '41000000-0000-0000-0000-000000000021'::uuid)
)
INSERT INTO reservation_room_assignment (reservation_id, physical_room_id)
SELECT md5('hcm-report-sample-reservation-' || hotel_code || '-0-2')::uuid,
       physical_room_id
  FROM assignment_seed
ON CONFLICT (reservation_id, physical_room_id) DO NOTHING;

-- 점유율 차트를 위해 세 지점의 지난 14일 재고에 확정 객실 수를 넣는다.
WITH room_seed(room_type_id, capacity, confirmed) AS (
    VALUES
        ('21000000-0000-0000-0000-000000000001'::uuid, 12, 6),
        ('21000000-0000-0000-0000-000000000002'::uuid, 8, 4),
        ('21000000-0000-0000-0000-000000000003'::uuid, 4, 2),
        ('21000000-0000-0000-0000-000000000011'::uuid, 10, 7),
        ('21000000-0000-0000-0000-000000000012'::uuid, 6, 4),
        ('21000000-0000-0000-0000-000000000013'::uuid, 4, 2),
        ('21000000-0000-0000-0000-000000000021'::uuid, 14, 11),
        ('21000000-0000-0000-0000-000000000022'::uuid, 8, 6),
        ('21000000-0000-0000-0000-000000000023'::uuid, 4, 3)
)
INSERT INTO inventory_day (room_type_id, stay_date, capacity, held, confirmed)
SELECT room.room_type_id,
       ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - day_offset)::date,
       room.capacity,
       0,
       room.confirmed
  FROM room_seed room
  CROSS JOIN generate_series(0, 13) AS day_offset
ON CONFLICT (room_type_id, stay_date) DO UPDATE SET
    capacity = greatest(
        inventory_day.capacity,
        EXCLUDED.capacity,
        inventory_day.held + EXCLUDED.confirmed
    ),
    confirmed = EXCLUDED.confirmed;

-- 현재/이전 기간에 지점별 대기·완료 변경 요청을 한 건씩 만든다.
WITH hotel_seed(code, hotel_id, room_type_id, rate_plan_id) AS (
    VALUES
        ('sokcho',
         '11000000-0000-0000-0000-000000000001'::uuid,
         '21000000-0000-0000-0000-000000000001'::uuid,
         '31000000-0000-0000-0000-000000000001'::uuid),
        ('seoraksan',
         '11000000-0000-0000-0000-000000000002'::uuid,
         '21000000-0000-0000-0000-000000000011'::uuid,
         '31000000-0000-0000-0000-000000000011'::uuid),
        ('jeju',
         '11000000-0000-0000-0000-000000000003'::uuid,
         '21000000-0000-0000-0000-000000000021'::uuid,
         '31000000-0000-0000-0000-000000000021'::uuid)
), request_seed(day_offset, status) AS (
    VALUES
        (0, 'PENDING_APPROVAL'),
        (1, 'COMPLETED'),
        (7, 'PENDING_APPROVAL'),
        (8, 'COMPLETED')
)
INSERT INTO reservation_change_request (
    id, reservation_id, hotel_id, base_operation_revision, status,
    settlement_direction, requested_by, idempotency_key, request_hash,
    previous_check_in, previous_check_out, previous_room_type_id, previous_rate_plan_id,
    target_check_in, target_check_out, target_room_type_id, target_rate_plan_id,
    rooms, adults, children, approval_expires_at, version, created_at, updated_at,
    request_origin
)
SELECT md5('hcm-report-sample-change-' || hotel.code || '-' || request.day_offset)::uuid,
       reservation.id,
       hotel.hotel_id,
       reservation.operation_revision,
       request.status,
       'NONE',
       NULL,
       'report-sample-' || hotel.code || '-' || request.day_offset,
       md5('hcm-report-sample-change-hash-' || hotel.code || '-' || request.day_offset)
           || md5('hcm-report-sample-change-hash-extra-' || hotel.code || '-' || request.day_offset),
       reservation.check_in,
       reservation.check_out,
       hotel.room_type_id,
       hotel.rate_plan_id,
       reservation.check_in + 1,
       reservation.check_out + 1,
       hotel.room_type_id,
       hotel.rate_plan_id,
       reservation.rooms,
       reservation.adults,
       reservation.children,
       CURRENT_TIMESTAMP + interval '30 days',
       0,
       (((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - request.day_offset)::timestamp
           + time '14:00') AT TIME ZONE 'Asia/Seoul',
       (((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - request.day_offset)::timestamp
           + time '14:00') AT TIME ZONE 'Asia/Seoul',
       'CUSTOMER'
  FROM hotel_seed hotel
  CROSS JOIN request_seed request
  JOIN reservation
    ON reservation.id = md5(
        'hcm-report-sample-reservation-' || hotel.code || '-' || request.day_offset || '-1'
    )::uuid
ON CONFLICT (id) DO UPDATE SET
    reservation_id = EXCLUDED.reservation_id,
    hotel_id = EXCLUDED.hotel_id,
    status = EXCLUDED.status,
    previous_check_in = EXCLUDED.previous_check_in,
    previous_check_out = EXCLUDED.previous_check_out,
    target_check_in = EXCLUDED.target_check_in,
    target_check_out = EXCLUDED.target_check_out,
    approval_expires_at = EXCLUDED.approval_expires_at,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

-- 승인 대기 표가 검토 가능한 정상 계약을 받도록 샘플 변경 요청마다 현재 견적을 연결한다.
-- 날짜를 옮기되 샘플 차액은 0원으로 유지해 외부 결제·환불 흐름을 만들지 않는다.
INSERT INTO reservation_change_quote (
    id, request_id, revision, previous_total_krw, total_krw,
    difference_krw, currency, rooms, created_at
)
SELECT md5('hcm-report-sample-quote-' || request.id)::uuid,
       request.id,
       1,
       reservation.total_krw,
       reservation.total_krw,
       0,
       reservation.currency,
       request.rooms,
       request.created_at
  FROM reservation_change_request request
  JOIN reservation ON reservation.id = request.reservation_id
 WHERE request.idempotency_key LIKE 'report-sample-%'
ON CONFLICT (id) DO UPDATE SET
    request_id = EXCLUDED.request_id,
    revision = EXCLUDED.revision,
    previous_total_krw = EXCLUDED.previous_total_krw,
    total_krw = EXCLUDED.total_krw,
    difference_krw = EXCLUDED.difference_krw,
    currency = EXCLUDED.currency,
    rooms = EXCLUDED.rooms,
    created_at = EXCLUDED.created_at;

INSERT INTO reservation_change_quote_night (quote_id, stay_date, amount_krw)
SELECT quote.id,
       night.stay_date::date,
       (quote.total_krw / (request.target_check_out - request.target_check_in))
           + CASE
                 WHEN night.ordinality <= quote.total_krw % (request.target_check_out - request.target_check_in)
                 THEN 1
                 ELSE 0
             END
  FROM reservation_change_request request
  JOIN reservation_change_quote quote
    ON quote.request_id = request.id
   AND quote.id = md5('hcm-report-sample-quote-' || request.id)::uuid
 CROSS JOIN LATERAL generate_series(
     request.target_check_in,
     request.target_check_out - 1,
     interval '1 day'
 ) WITH ORDINALITY AS night(stay_date, ordinality)
 WHERE request.idempotency_key LIKE 'report-sample-%'
ON CONFLICT (quote_id, stay_date) DO UPDATE SET
    amount_krw = EXCLUDED.amount_krw;

UPDATE reservation_change_request request
   SET current_quote_id = quote.id
  FROM reservation_change_quote quote
 WHERE quote.request_id = request.id
   AND quote.id = md5('hcm-report-sample-quote-' || request.id)::uuid
   AND request.idempotency_key LIKE 'report-sample-%';

COMMIT;

SELECT h.name,
       count(DISTINCT r.id) FILTER (
           WHERE (r.created_at AT TIME ZONE 'Asia/Seoul')::date
                 BETWEEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - 13
                     AND (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
       ) AS sample_reservations_14d,
       count(DISTINCT req.id) FILTER (
           WHERE (req.created_at AT TIME ZONE 'Asia/Seoul')::date
                 BETWEEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - 13
                     AND (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
       ) AS sample_changes_14d
  FROM hotel h
  LEFT JOIN room_type rt ON rt.hotel_id = h.id
  LEFT JOIN reservation r
    ON r.room_type_id = rt.id
   AND r.guest_email LIKE 'sample-report-%@example.invalid'
  LEFT JOIN reservation_change_request req
    ON req.reservation_id = r.id
 WHERE h.id IN (
     '11000000-0000-0000-0000-000000000001'::uuid,
     '11000000-0000-0000-0000-000000000002'::uuid,
     '11000000-0000-0000-0000-000000000003'::uuid
 )
 GROUP BY h.id, h.name
 ORDER BY h.name;
