-- 본사가 지점의 판매를 중지·재개한다.
-- 중지한 지점은 고객 검색과 지점 목록에서 빠진다. 이미 확정된 예약은 그대로 둔다.
-- additive하다. 기존 행은 모두 활성이므로 기존 동작을 변경하지 않는다.
ALTER TABLE hotel
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
