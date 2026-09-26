-- 본사가 객실 유형을 추가할 때 정한 조식 포함 여부와 기본 요금을
-- room_type 행에 보관한다. 시드가 지나가기 전에 본사가 입력한 값을
-- 읽을 수 있어야 입력값이 시드에 반영됐는지 확인할 수 있다.
-- additive하다. 기존 행은 NULL이고, 본사가 만든 유형만 값이 들어간다.
ALTER TABLE room_type
    ADD COLUMN IF NOT EXISTS seed_breakfast_included BOOLEAN,
    ADD COLUMN IF NOT EXISTS seed_default_rate_krw INTEGER;

-- 조식 포함 여부가 정해지지 않은 시드 이력은 false로 본다.
-- rate_plan.breakfast_included가 NOT NULL이므로 같은 제약을 유지한다.
ALTER TABLE room_type
    ADD CONSTRAINT room_type_seed_breakfast_included_not_null
    CHECK (seed_breakfast_included IS NULL OR seed_breakfast_included IN (TRUE, FALSE));

-- 기본 요금은 0원 이상이어야 한다. rate_day.amount_krw와 같은 제약이다.
ALTER TABLE room_type
    ADD CONSTRAINT room_type_seed_default_rate_krw_non_negative
    CHECK (seed_default_rate_krw IS NULL OR seed_default_rate_krw >= 0);
