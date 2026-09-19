-- 본사가 예약 변경 승인 한도를 변경할 때 쓰는 값 컬럼.
-- 취소 정책 행은 NULL이고 변경 승인 한도 행만 값을 가진다.
-- 기존 행과 제약을 변경하지 않는 additive 마이그레이션이다.
ALTER TABLE policy_revision
    ADD COLUMN value_krw BIGINT CHECK (value_krw IS NULL OR value_krw >= 0);

CREATE INDEX policy_revision_change_limit_latest_idx
    ON policy_revision (created_at DESC)
    WHERE key = 'change-approval';
