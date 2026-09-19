-- 정산 실행 provider를 TOSS_TEST까지 허용한다.
-- toss-test 환경에서도 본사 정산·대사 조회와 worker가 동작해야 하므로
-- 단일 값 CHECK를 화이트리스트로 바꾼다.
ALTER TABLE toss_settlement_run DROP CONSTRAINT toss_settlement_run_provider_check;

ALTER TABLE toss_settlement_run
    ADD CONSTRAINT toss_settlement_run_provider_check CHECK (provider IN ('TOSS_TEST', 'TOSS_LIVE'));
