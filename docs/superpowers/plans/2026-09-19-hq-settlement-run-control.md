# 본사 정산 실행 생성·재시도

> 상태: 완료. `docs/changes/2026-09-19-hq-settlement-run-control.md`에 검증 결과를 옮겼다. 최종 갱신 2026-09-19.

## 배경

- `TossSettlementRunService.create`는 이미 존재하지만 호출하는 곳이 없다. 실행은 worker의 스케줄이 임의로 만들어야 동작하므로 본사가 원하는 기간의 정산을 직접 조회할 수 없었다.
- `TossSettlementWorker.processNext`는 `PENDING`·`PROCESSING`(lease 만료) run만 집는다. `FAILED`로 끝난 실행은 다시 `PENDING`으로 돌려야 재시도가 가능하다.
- 읽기 전용 본사 조회(`a602ef7`) 다음 단계로, 쓰기 동작 중에서도 정산·결제·재고 상태를 직접 바꾸지 않는 실행 제어만 먼저 붙인다.

## 완료 기준

1. `POST /api/staff/settlements/runs`가 본사 세션으로 기간(`from`·`to`)을 받아 `PENDING` run을 만든다. 응답은 생성된 run ID.
2. `POST /api/staff/settlements/runs/{runId}/retry`가 `FAILED` run을 `PENDING`으로 되돌려 worker가 다시 집게 한다.
3. 지점 직원·잘못된 세션은 각각 403·401. 기간 검증은 서비스의 기존 규칙(과거 31일 이내)을 그대로 쓰고 위반은 400.
4. `FAILED`가 아닌 run의 재시도는 409. 없는 run은 404.
5. 관리자 4001에 실행 생성 폼과 `FAILED` 카드의 재시도 버튼이 표시된다.
6. 서버 통합 테스트와 관리자 TypeScript·ESLint가 종료 코드 0이다.

## 구현 범위

### 서버

- `TossSettlementController`에 POST 2개를 추가한다. 기존 GET과 같은 `@ConditionalOnExpression` 조건을 유지해 정산 worker가 비활성화된 환경에서는 노출되지 않는다.
- `TossSettlementRunService.create`는 기존 검증(과거 31일 이내, from<=to)을 그대로 쓰고 `requested_by`에 본사 세션의 staff id를 바인딩한다.
- `TossSettlementRunService.retry(UUID runId)`를 추가한다. `FAILED` 행을 `status='PENDING'`, `attempt_count=0`, `error_code=null`, `next_attempt_at=now()`로 되돌린다. `claim_token`·`lease_expires_at`은 `FAILED` 상태에서 이미 비어 있어야 한다.
- 공급자 코드·상점 계정·페이지 크기는 run 생성 시점 값으로 고정하고 재시도로 바꾸지 않는다.

### 관리자

- `staff-api.ts`에 `createSettlementRun`·`retrySettlementRun`과 `CreateRunRequest` 타입을 추가한다. 기존 `contentRequest`와 같은 에러 패턴을 따른다.
- `settlement-reconciliation.tsx`에 기간 입력 폼(시작일·종료일)과 `FAILED` 카드의 재시도 버튼을 추가한다. 성공하면 목록을 새로고침한다.
- 빈 목록 카드의 안내 문구를 본사가 직접 실행을 만들 수 있다는 존재로 수정한다.

### 테스트

- `TossSettlementCommandIntegrationTest`를 새로 작성한다. 실행 생성(본사 201·지점 403·잘못된 세션 401), 기간 검증 400, 재시도 성공·상태 충돌 409·없는 run 404를 검증한다.
- 기존 `TossSettlementQueryIntegrationTest`의 읽기 전용 동작은 그대로 유지된다.

## 미구현 항목

- worker 스케줄 주기· 페이지 크기 등 실행 파라미터의 동적 지정. 환경 변수 기본값을 그대로 쓴다.
- 동일 기간 중복 실행 방지. 같은 기간의 run을 여러 번 만들 수 있지만 snapshot은 `ON CONFLICT DO NOTHING`으로 중복 저장되지 않는다.
- 정산 실행의 강제 중단·삭제. `FAILED` 전환은 worker의 오류 처리에만 있다.
- 실제 토스 정산 API 응답으로 채워진 대사 결과. worker가 외부 API를 호출한 적이 없다.
