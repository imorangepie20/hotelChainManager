INSERT INTO hotel (id, name, region, timezone) VALUES
    ('11000000-0000-0000-0000-000000000001', '속초 오션 호텔', '속초', 'Asia/Seoul'),
    ('11000000-0000-0000-0000-000000000002', '설악 마운틴 호텔', '설악산', 'Asia/Seoul'),
    ('11000000-0000-0000-0000-000000000003', '제주 아일랜드 호텔', '제주도', 'Asia/Seoul')
ON CONFLICT (id) DO NOTHING;

INSERT INTO room_type (id, hotel_id, name, max_occupancy) VALUES
    ('21000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', '스탠다드 시티', 2),
    ('21000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', '디럭스 오션', 3),
    ('21000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000001', '패밀리 스위트', 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO rate_plan (id, room_type_id, name, breakfast_included, policy_version) VALUES
    ('31000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', '룸 온리', false, 'FLEX-2026-01'),
    ('31000000-0000-0000-0000-000000000002', '21000000-0000-0000-0000-000000000002', '조식 포함', true, 'FLEX-2026-01'),
    ('31000000-0000-0000-0000-000000000003', '21000000-0000-0000-0000-000000000003', '패밀리 조식', true, 'FLEX-2026-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO rate_day (rate_plan_id, stay_date, amount_krw)
SELECT plan_id, stay_date,
       base_amount + CASE WHEN EXTRACT(ISODOW FROM stay_date) IN (6, 7) THEN 40000 ELSE 0 END
FROM (VALUES
    ('31000000-0000-0000-0000-000000000001'::uuid, 120000),
    ('31000000-0000-0000-0000-000000000002'::uuid, 170000),
    ('31000000-0000-0000-0000-000000000003'::uuid, 250000)
) AS plans(plan_id, base_amount)
CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 89, INTERVAL '1 day') AS dates(stay_date)
ON CONFLICT (rate_plan_id, stay_date) DO NOTHING;

INSERT INTO inventory_day (room_type_id, stay_date, capacity)
SELECT room_type_id, stay_date, capacity
FROM (VALUES
    ('21000000-0000-0000-0000-000000000001'::uuid, 12),
    ('21000000-0000-0000-0000-000000000002'::uuid, 8),
    ('21000000-0000-0000-0000-000000000003'::uuid, 4)
) AS rooms(room_type_id, capacity)
CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 89, INTERVAL '1 day') AS dates(stay_date)
ON CONFLICT (room_type_id, stay_date) DO NOTHING;

