-- 본사가 예약 변경 승인 TTL(본사 승인을 기다릴 수 있는 최대 시간)을 변경할 때 쓰는 값 컬럼.
-- 취소 정책·변경 승인 한도 행은 NULL이고 승인 TTL 행만 값을 가진다.
-- 기존 행과 제약을 변경하지 않는 additive 마이그레이션이다.
ALTER TABLE policy_revision
    ADD COLUMN value_seconds BIGINT CHECK (value_seconds IS NULL OR value_seconds >= 0);

CREATE INDEX policy_revision_approval_ttl_latest_idx
    ON policy_revision (created_at DESC)
    WHERE key = 'change-approval-ttl';
