# 정산·대사를 토스 test 환경까지 허용

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- 이전 세션에서 정산 스택을 `toss-live` 전용에서 토스 결제 환경 전용(`fake` 이외)으로 확장하다가 중단됐다. 작업 트리에 남은 변경은 5개 서버 파일이었으나 `TossPaymentEnvironment` import가 빠져 `mvnw compile`·`test-compile`이 모두 실패하는 상태였고, 테스트는 여전히 3-arg 생성자를 호출했다.
- 로컬 개발 환경은 루트 `.env`가 `PAYMENT_PROVIDER=toss-test`다. 정산이 `toss-live`에서만 로드되면 본사가 개발·테스트 거래의 정산·대사 결과를 전혀 볼 수 없었고, `docs/changes/2026-09-19-hq-settlement-reconciliation-view.md`가 404로 기록한 현상도 이것이 원인이었다.
- 대사 SQL은 `provider='TOSS_LIVE'`를 상수로 박아 test 환경의 `TOSS_TEST` 거래와 일치하지 않았고, `toss_settlement_run.provider` CHECK는 `TOSS_LIVE` 단일 값이라 `TossSettlementRunService`가 `TOSS_TEST`로 insert할 수 없었다.

## 구현 범위

### 서버

- `TossSettlementReconciliationService`·`TossSettlementRunService`에 빠진 `team.hotelchain.payment.TossPaymentEnvironment` import를 추가해 compile 실패를 복구했다.
- `TossSettlementWorker`·`TossSettlementReconciliationService`·`TossSettlementRunService`의 로드 조건을 `'${payment.provider:fake}' == 'toss-live'`에서 `!= 'fake'`로 바꿨다. 토스 환경이면 정산이 동작하고 `fake`·`disabled`에서는 로드되지 않는다.
- `TossSettlementController`·`TossSettlementQueryService`도 같은 조건으로 따라간다. 본사 읽기 전용 조회가 worker와 다른 환경에서 열리지 않도록 한다.
- `TossSettlementRunService`·`TossSettlementReconciliationService`가 `${payment.provider}`로 `TossPaymentEnvironment.from(mode).providerCode()`를 계산한다. run insert의 provider 칼럼과 대사 SQL의 provider 비교값 모두 상수 대신 이 값을 바인딩한다. 대사 쿼리 6곳의 `pa.provider`·`a.provider`·`pt.provider` 파라미터가 해당한다.
- `TossPaymentsConfiguration`의 executor·client 빈 조건을 `toss-test`·`toss-live` 동시 허용으로 맞추고, `TossSettlementClient` 생성에 `TossPaymentEnvironment` 빈을 주입해 선택한 환경의 키 접두어·상점·origin을 검증한다. `TossSettlementHttpClient`는 선택한 환경을 다시 노출하는 `environment()`를 제공한다.
- V45 migration으로 `toss_settlement_run.provider` CHECK를 `TOSS_LIVE` 단일 값에서 `IN ('TOSS_TEST','TOSS_LIVE')` 화이트리스트로 바꿨다. 기존 V44를 직접 고치면 Flyway checksum이 어긋나므로 additive migration만 추가했다.
- `compose.yaml`의 API 서비스에 `TOSS_SETTLEMENT_ENABLED`·`TOSS_SETTLEMENT_PAGE_SIZE`·`TOSS_SETTLEMENT_DELAY_DAYS`를 전달한다. 이전에는 루트 `.env`에 설정해도 컨테이너로 넘어가지 않았다.

### 테스트

- `TossSettlementHttpClientTest`의 3-arg 생성자 호출 3곳을 4-arg로 고쳐 test-compile을 복구했다.
- 같은 클래스에 test 환경 설정 수용과 `environment()` 반환, 선택한 환경과 키 불일치 거부 검증 2건을 추가했다.
- `TossSettlementModeIntegrationTest`를 추가했다. `payment.provider=toss-test`에서 정산 controller·query·worker·run service가 모두 로드되고 run의 provider가 `TOSS_TEST`로 저장되는지 확인한다.
- `TossPaymentsConfigurationTest`·`TossPaymentsPropertiesTest`·`TossPaymentsHttpClientTest`의 기존 라이브 환경 검증은 변경하지 않았다.

## 자동 검증

- API: `mvnw compile`·`mvnw test-compile` 종료 코드 0. 작업 시작 시점의 compile 실패가 복구됐다.
- API: 정산·결제 집중 영역 77건이 종료 코드 0이다. `TossSettlementModeIntegrationTest` 2·`TossSettlementHttpClientTest` 6·`TossSettlementReconciliationServiceTest` 1·`TossSettlementQueryIntegrationTest` 7·`TossSettlementReconciliationIntegrationTest` 2·`TossSettlementIntegrationTest` 2·`TossPaymentsConfigurationTest` 2·`TossPaymentsPropertiesTest` 5·`TossPaymentsHttpClientTest` 44·`TossReservationPaymentIntegrationTest` 26·`CustomerSelfServiceReservationChangeIntegrationTest` 6·`ReservationChangeSettlementIntegrationTest` 10·`TossPaymentAdjustmentIntegrationTest` 22.
- DB: test DB(55433)·개발 DB(55432) 모두 Flyway V45 `success=t` 적용을 확인했다. 제약 이름 `toss_settlement_run_provider_check`는 개발 DB의 `pg_constraint`에서 직접 확인했다.
- 런타임: API 재빌드 뒤 health `UP`. 컨테이너의 `PAYMENT_PROVIDER=toss-test`를 확인했다.
- `TossSettlementIntegrationTest` 1건이 조합 실행에서만 실패했다. `processNext()`의 반환값 단정이고 단독 실행과 재조합 실행에서 모두 종료 코드 0이라 타이밍 스큐로 판단해 추가 변경 없이 통과로 기록했다.

## 라이브 확인

- 루트 `.env`에 `TOSS_SETTLEMENT_ENABLED=true`를 추가하고 `docker compose up -d api`로 API를 다시 띄웠다. `printenv`가 `PAYMENT_PROVIDER=toss-test`·`TOSS_SETTLEMENT_ENABLED=true`·`TOSS_SETTLEMENT_PAGE_SIZE=500`·`TOSS_SETTLEMENT_DELAY_DAYS=2`를 반환한다.
- API 로그가 Flyway `Successfully validated 51 migrations`, `Current version of schema "public": 45`, `No migration necessary`를 출력하고 `Started HotelApplication`로 끝난다. health `UP` 9초 안에 도달한다.
- 활성화 전에는 `GET /api/staff/settlements/runs`가 404다. 활성화 뒤에는 가짜 세션이 401로 바뀌어 엔드포인트가 로드됐음을 확인했다.
- `POST /api/staff/sessions`로 본사 계정(`hq@hotel-chain.local`) 로그인에 성공해 세션을 받았다. README의 `hq@stayhaneul.test`는 실제 계정이 아니므로 사용하지 않는다.
- 본사 세션으로 `GET /api/staff/settlements/runs`·`/runs/{id}`를 호출해 200과 실행 상세를 확인했다. provider는 `TOSS_TEST`로 내려온다. 지점 직원 세션은 403 `STAFF_HOTEL_ACCESS_DENIED`로 거부됐다.
- 빈 목록 상태를 보기 위해 `TOSS_TEST`·`tgen_docs`·2026-09-10~2026-09-17 기간의 실행 1건을 직접 넣었다. 목록 API가 카드 1건을 반환했고 관리자 4001 브라우저에서 `9월 10일–9월 17일 · tgen_docs` 카드와 `snapshot 2 · 일치 1 · 불일치 1 · 정산 대기 0` 지표, `완료` 배지가 표시됐다. 확인 뒤 해당 실행과 세션을 삭제했다.
- 브라우저: 관리자 4001에서 본사 계정 로그인 → `/dashboard/settlements` 카드 표시 → `대사 내역 보기` 상세 → 상태 필터 `MATCHED` 적용 → 지점 직원은 본사 전용 안내만 표시하고 `tgen_docs`가 0건. 3개 Playwright 시나리오 종료 코드 0.
- 브라우저 검증은 일회성 스크립트(`e2e/settlement-live.spec.ts`)로 수행하고 마친 뒤 삭제했다. `addInitScript`·`page.evaluate`로 localStorage를 주입하면 첫 로드에서 `DashboardAccessGate`가 `/api/staff/me`를 검증하기 전에 `/login`으로 보내지는 경쟁이 있어서 폼 로그인 방식을 사용했다. 비밀번호는 환경 변수로 받고 스크립트에 두지 않았다.

## 미검증 항목

- 실제 토스 test 정산 API 응답으로 채워진 run의 대사 결과. worker가 외부 정산 API를 호출한 적이 없다. 화면 지표는 직접 넣은 실행 1건 기준이다.
- 전체 backend suite 실행. 정산·결제·변경 정산 집중 영역만 실행했다.
- Playwright 브라우저 회귀의 영구 suite. 이번 라이브 확인은 일회성 스크립트로만 수행했다.

