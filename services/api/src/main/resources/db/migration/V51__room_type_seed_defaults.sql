-- 본사가 만든 객실 유형의 시드 완료 시각. V2가 만든 3종 시드는 NULL로 둔다.
-- 이미 가격·재고가 있으므로 다시 시드하지 않는다.
ALTER TABLE room_type ADD COLUMN seed_completed_at TIMESTAMPTZ;
