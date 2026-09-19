# 본사 정산 실행 생성·재시도

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- 정산 실행은 `TossSettlementRunService.create`로 만들 수 있었지만 호출하는 곳이 없었다. worker의 스케줄이 실행을 만들지 않으면 본사가 원하는 기간의 정산을 조회할 방법이 없었고, 실패한 실행도 다시 실행할 수 없었다.
- 읽기 전용 본사 조회 다음 단계로, 쓰기 동작 중에서도 정산·결제·재고 상태를 직접 바꾸지 않고 worker가 처리할 실행만 준비하는 실행 제어를 먼저 붙였다.

## 구현 범위

### 서버

- `TossSettlementController`에 POST 2개를 추가했다. `POST /api/staff/settlements/runs`가 기간(`from`·`to`)을 받아 `PENDING` run을 만들고 `POST /api/staff/settlements/runs/{runId}/retry`가 실패한 실행을 다시 대기 상태로 옮긴다. 두 동작 모두 `requireHeadquarters`로 본사 세션을 검증하고 기존 GET과 같은 `@ConditionalOnExpression` 조건으로 로드된다.
- `TossSettlementRunService.create`는 기존 검증(과거 31일 이내, `from`≤`to`, 미래 거부)을 그대로 쓰고 `requested_by`에 본사 세션의 staff id를 바인딩한다.
- `TossSettlementRunService.retry(UUID runId)`를 추가했다. 실행 존재 여부를 먼저 확인하고 `FAILED`일 때만 `status='PENDING'`, `attempt_count=0`, `error_code=null`, `claim_token=null`, `lease_expires_at=null`, `next_attempt_at=now()`로 되돌린다. 기존 snapshot·대사 결과는 유지한다.
- `SettlementRunNotFoundException`(404)·`SettlementNotRetryableException`(409)을 추가하고 `ApiExceptionHandler`에 매핑했다. 기간 검증 위반은 기존 `IllegalArgumentException` → 400 경로를 그대로 탄다.
- 공급자 코드·상점 계정·페이지 크기는 run 생성 시점 값으로 고정하고 재시도로 바꾸지 않는다.

### 관리자

- `staff-api.ts`에 `createSettlementRun`·`retrySettlementRun`과 `CreateSettlementRunInput`·`CreatedSettlementRun` 타입을 추가했다. 기존 `contentRequest`는 404와 조회 실패를 구분하지 않아서 정산 전용 `settlementFailureMessage`를 쓰는 전용 fetch로 만들었다.
- `settlement-reconciliation.tsx`에 기간 입력 폼(시작일·종료일, `max`로 오늘까지만 허용)을 추가했다. 제출하면 worker가 순차적으로 처리한다는 안내를 `role="status"`로 표시하고 목록을 새로고침한다.
- `FAILED` 카드에 `재실행` 버튼을 추가했다. 성공하면 안내와 함께 목록을 새로고침한다.
- 빈 목록 카드의 안내 문구를 실행 요청 폼으로 바뀌었다.

### 테스트

- `TossSettlementCommandIntegrationTest` 5건을 새로 작성했다. 실행 생성(본사·지점 403·잘못된 세션 401), 기간 검증 3종 400, `FAILED` 재시도 성공·`SUCCEEDED` 충돌 409·없는 run 404를 검증한다.
- `TossPaymentsConfigurationTest`·`TossSettlementQueryIntegrationTest` 등 기존 읽기 전용 검증은 변경하지 않았다.

## 자동 검증

- API: `mvnw compile`·`test-compile` 종료 코드 0.
- API: 정산·변경 정산 집중 영역 42건이 종료 코드 0이다. `TossSettlementCommandIntegrationTest` 5·`TossSettlementModeIntegrationTest` 2·`TossSettlementHttpClientTest` 6·`TossSettlementQueryIntegrationTest` 7·`TossSettlementReconciliationIntegrationTest` 2·`TossSettlementIntegrationTest` 2·`TossPaymentsConfigurationTest` 2·`TossSettlementReconciliationServiceTest` 1·`CustomerSelfServiceReservationChangeIntegrationTest` 6·`ReservationChangeSettlementIntegrationTest` 10.
- 관리자: `tsc --noEmit` 종료 코드 0. `eslint`는 변경 파일 2개에서 에러 0·경고 3건(`react-hooks/set-state-in-effect`)이며 기존 패턴과 같다.
- DB: API 재빌드에서 Flyway `No migration necessary`, health `UP` 10초 도달.
- 브라우저: 관리자 4001에서 본사 로그인 → `/dashboard/settlements`의 실행 요청 폼 표시 → `iso(3)`~`iso(1)` 기간 입력 후 실행 생성 → 목록에 `PENDING` 카드 표시 → 해당 run의 재시도 409(`SETTLEMENT_RUN_NOT_RETRYABLE`)·없는 run 404까지 1개 Playwright 시나리오 종료 코드 0.

## 라이브 확인 메모

- 브라우저 검증은 일회성 스크립트(`e2e/settlement-command-live.spec.ts`)로 수행하고 마친 뒤 삭제했다. 검증 중 만든 실행 2건과 세션도 DB에서 삭제했다.
- `DashboardAccessGate`가 `/api/staff/me`로 세션을 검증하므로 localStorage 주입이 아닌 폼 로그인 방식을 사용했다.

## 미검증 항목

- worker가 만들어진 `PENDING` 실행을 실제로 집어 외부 정산 API를 호출하는 동선. worker가 외부 API를 호출한 적이 없다.
- `FAILED` 실행의 실제 재시도가 성공하는 동선. 실패 상태는 서버 테스트와 API 응답 코드로만 확인했다.
- 전체 backend suite 실행. 정산·결제·변경 정산 집중 영역만 실행했다.
- Playwright 브라우저 회귀의 영구 suite. 이번 라이브 확인은 일회성 스크립트로만 수행했다.
- 동일 기간 중복 실행 방지. 같은 기간의 run을 여러 번 만들 수 있지만 snapshot은 `ON CONFLICT DO NOTHING`으로 중복 저장되지 않는다.
- 정산 실행의 강제 중단·삭제. `FAILED` 전환은 worker의 오류 처리에만 있다.
