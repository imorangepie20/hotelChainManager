WITH initial_content(hotel_id, content) AS (
    VALUES
    ('11000000-0000-0000-0000-000000000001'::uuid, jsonb_build_object(
        'heroAssetId', '14000000-0000-0000-0000-000000000001',
        'heroImage', '/images/sokcho-coast-hero.png',
        'heroAlt', '동해와 설악산을 바라보는 가상의 속초 해안 호텔',
        'eyebrow', 'SOKCHO · EAST SEA',
        'title', E'파도와 설악의 사이에서\n가장 느린 하루를',
        'description', '동해의 수평선과 설악의 능선을 한눈에 담는 휴식.',
        'seo', jsonb_build_object('title', '속초 오션 호텔 | STAY HANEUL', 'description', '동해와 설악을 바라보는 속초 오션 호텔의 객실, 오퍼와 예약 정보를 확인하세요.'),
        'arrival', jsonb_build_object('address', '강원특별자치도 속초시, 가상 해안로 186', 'checkInOut', '15:00 / 11:00', 'highlight', '새벽의 바다와 저녁의 항구를 가까이에서 만나보세요.'),
        'experiences', jsonb_build_array(
            jsonb_build_object('category', 'ROOM', 'title', '수평선을 향한 객실', 'description', '창 너머로 바다가 이어지는 편안한 하루.'),
            jsonb_build_object('category', 'DINING', 'title', '동해의 제철 식탁', 'description', '지역의 계절을 담은 느긋한 아침과 저녁.'),
            jsonb_build_object('category', 'EXPERIENCE', 'title', '바다 곁의 산책', 'description', '해안 산책로와 항구의 시간을 따라 걷는 여행.')),
        'offers', jsonb_build_array(
            jsonb_build_object('title', '푸른 아침', 'detail', '조용한 바다를 바라보며 시작하는 휴식의 제안', 'bookingPeriod', '2026.09.01 ~ 2026.12.31', 'stayPeriod', '2026.09.15 ~ 2027.02.28'),
            jsonb_build_object('title', '느린 주말', 'detail', '하루를 더 머물며 속초의 계절을 깊게 만나는 여정', 'bookingPeriod', '2026.09.01 ~ 2026.12.31', 'stayPeriod', '2026.09.01 ~ 2027.03.31')))),
    ('11000000-0000-0000-0000-000000000002'::uuid, jsonb_build_object(
        'heroAssetId', '14000000-0000-0000-0000-000000000001',
        'heroImage', '/images/sokcho-coast-hero.png',
        'heroAlt', '산과 숲의 풍경을 담은 가상의 설악산 스테이',
        'eyebrow', 'SEORAK · FOREST',
        'title', E'숲의 결을 따라\n깊어지는 쉼',
        'description', '계절마다 다른 빛을 품은 설악의 능선에서 만나는 고요.',
        'seo', jsonb_build_object('title', '설악산 포레스트 호텔 | STAY HANEUL', 'description', '설악의 숲과 능선을 품은 포레스트 호텔의 객실, 오퍼와 예약 정보를 확인하세요.'),
        'arrival', jsonb_build_object('address', '강원특별자치도 설악산 일대, 가상 설악로 72', 'checkInOut', '15:00 / 11:00', 'highlight', '아침 안개와 숲의 향을 따라 천천히 하루를 시작하세요.'),
        'experiences', jsonb_build_array(
            jsonb_build_object('category', 'ROOM', 'title', '능선을 담은 객실', 'description', '나무와 돌의 온기를 닮은 차분한 공간.'),
            jsonb_build_object('category', 'DINING', 'title', '산의 계절 식탁', 'description', '강원도의 재료로 완성한 따뜻한 식사.'),
            jsonb_build_object('category', 'EXPERIENCE', 'title', '숲속의 한 걸음', 'description', '계곡과 산책길을 따라 자연의 호흡을 만나는 시간.')),
        'offers', jsonb_build_array(
            jsonb_build_object('title', '숲의 아침', 'detail', '산의 공기와 함께 시작하는 한적한 휴식', 'bookingPeriod', '2026.09.01 ~ 2026.12.31', 'stayPeriod', '2026.09.15 ~ 2027.02.28'),
            jsonb_build_object('title', '계절의 산책', 'detail', '머무는 날만큼 깊어지는 설악의 풍경', 'bookingPeriod', '2026.09.01 ~ 2026.12.31', 'stayPeriod', '2026.09.01 ~ 2027.03.31')))),
    ('11000000-0000-0000-0000-000000000003'::uuid, jsonb_build_object(
        'heroAssetId', '14000000-0000-0000-0000-000000000001',
        'heroImage', '/images/sokcho-coast-hero.png',
        'heroAlt', '검은 현무암과 바다의 풍경을 담은 가상의 제주 스테이',
        'eyebrow', 'JEJU · ISLAND',
        'title', E'바람이 머무는 섬에서\n나만의 리듬으로',
        'description', '현무암의 질감과 푸른 바다가 만나는 제주에서의 여정.',
        'seo', jsonb_build_object('title', '제주 아일랜드 호텔 | STAY HANEUL', 'description', '제주의 바람과 바다를 담은 아일랜드 호텔의 객실, 오퍼와 예약 정보를 확인하세요.'),
        'arrival', jsonb_build_object('address', '제주특별자치도 제주시, 가상 해안길 31', 'checkInOut', '15:00 / 11:00', 'highlight', '바람, 돌, 바다의 질감을 따라 섬의 시간을 만나보세요.'),
        'experiences', jsonb_build_array(
            jsonb_build_object('category', 'ROOM', 'title', '섬의 빛을 담은 객실', 'description', '낮은 빛과 바다의 색을 편안하게 들이는 공간.'),
            jsonb_build_object('category', 'DINING', 'title', '제주의 식탁', 'description', '섬의 식재료를 담백하게 풀어낸 한 끼.'),
            jsonb_build_object('category', 'EXPERIENCE', 'title', '바람의 길', 'description', '해안과 오름을 잇는 제주의 느린 산책.')),
        'offers', jsonb_build_array(
            jsonb_build_object('title', '섬의 오후', 'detail', '제주의 빛과 바람을 천천히 즐기는 휴식', 'bookingPeriod', '2026.09.01 ~ 2026.12.31', 'stayPeriod', '2026.09.15 ~ 2027.02.28'),
            jsonb_build_object('title', '제주의 리듬', 'detail', '머무는 동안 발견하는 섬의 작은 장면들', 'bookingPeriod', '2026.09.01 ~ 2026.12.31', 'stayPeriod', '2026.09.01 ~ 2027.03.31'))))
)
INSERT INTO hotel_web_content (hotel_id, draft_content, published_content)
SELECT hotel_id, content, content FROM initial_content
ON CONFLICT (hotel_id) DO UPDATE
SET draft_content = EXCLUDED.draft_content,
    published_content = EXCLUDED.published_content
WHERE hotel_web_content.draft_content = '{}'::jsonb
  AND hotel_web_content.published_content = '{}'::jsonb;
