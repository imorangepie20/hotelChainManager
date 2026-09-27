-- 공개 고객 요청 접수의 멱등 키를 본문 지문과 독립적으로 선점한다.
-- 기존 요청은 보존하고, 같은 키가 이미 여러 번 사용된 경우 가장 먼저 생성된 요청을
-- 이후 재생 기준으로 삼는다.
CREATE TABLE guest_request_submission_command (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO guest_request_submission_command
    (idempotency_key, request_hash, request_id, created_at)
SELECT DISTINCT ON (idempotency_key)
       idempotency_key, request_hash, id, created_at
  FROM guest_request
 ORDER BY idempotency_key, created_at, id;

ALTER TABLE guest_request_submission_command
    ADD CONSTRAINT guest_request_submission_command_request_fk
    FOREIGN KEY (request_id) REFERENCES guest_request(id) ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED;
