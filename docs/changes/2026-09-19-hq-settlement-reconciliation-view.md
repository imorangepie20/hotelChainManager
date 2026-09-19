# 본사 읽기 전용 정산·대사 조회

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

`TossSettlementWorker`·`TossSettlementReconciliationService`는 토스 `GET /v1/settlements` snapshot을 저장하고 내부 거래와 대사하지만, 본사가 그 결과를 확인할 방법이 없었다. DB를 직접 읽어야 하므로 본사 관리자용 읽기 전용 화면이 필요했다. 정산·결제·재고 상태를 변경하는 동작은 기존 worker에 그대로 남기고 조회만 추가한다.

## 구현 범위

### 서버

- `TossSettlementQueryService`를 추가했다. `toss_settlement_run` 목록을 최신순으로, `toss_settlement_reconciliation` 행을 `toss_settlement_snapshot` left join으로 조회한다. SELECT만 사용하며 어떤 테이블도 변경하지 않는다.
- `TossSettlementController`가 `GET /api/staff/settlements/runs`·`GET /api/staff/settlements/runs/{runId}`를 노출한다. 두 엔드포인트 모두 `StaffAccessService.requireHeadquarters`로 본사 관리자 세션을 검증한다.
- 서비스·컨트롤러 모두 기존 settlement 클래스와 같은 `@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' == 'toss-live'")` 조건으로 로드된다. 정산 worker가 비활성화된 환경에서는 엔드포인트가 존재하지 않는다.
- `limit`은 runs 1~100, rows 1~500 범위로 고정한다. 범위 밖 요청은 가까운 경계로 잘라 거부하지 않는다.
- `status` 필터는 허용된 6개 대사 상태(`MATCHED`·`AMOUNT_MISMATCH`·`FEE_MISMATCH`·`MISSING_INTERNAL`·`MISSING_PROVIDER`·`PENDING`) 화이트리스에 없으면 전체 조회로 처리한다. SQL 조각이 아닌 바인딩 파라미터로 전달된다.
- 응답은 금액을 `Long`으로 그대로 제공하고 화면에서 표시만 한다. 금액을 재계산하거나 검증하지 않는다.
- 존재하지 않는 `runId`는 빈 detail(`run: null`, `rows: []`)을 반환한다. 본사가 실행 목록과 동기화되지 않은 링크를 열 때 오류 화면 대신 빈 상태를 보여준다.

### 관리자

- `getSettlementRuns`·`getSettlementRun`과 `SettlementRunsView`·`SettlementRunDetailView`·`ReconciliationRow` 타입을 `staff-api.ts`에 추가했다. 기존 `contentRequest` 헬퍼와 같은 `X-Staff-Session` 헤더 패턴을 쓰고 실패 시 `StaffApiError`를 던진다.
- `SettlementReconciliation` 컴포넌트를 추가했다. 실행 목록 카드(상태 Badge, snapshot·일치·불일치·정산 대기 건수), 실행 상세(요약 카드 + 상태 필터 + 대사 표), 새로고침, 뒤로 가기를 제공한다.
- 지점 직원이나 비로그인 상태에서는 API를 호출하지 않고 본사 전용 안내만 표시한다.
- `nav.ts`에 `financeGroup`("재무" → "정산·대사", `/dashboard/settlements`, `Wallet` 아이콘)을 추가하고 `HQ_ADMIN` 메뉴에 포함했다. `useStaffNavigation`이 역할별로 그대로 적용한다.
- `/dashboard/settlements` 페이지를 추가했다.

## 서버 권한과 안전 경계

- 모든 금액·수수료·지급액·상태·오류 코드는 서버가 저장한 값을 읽기 전용으로 전달한다. 화면이 대사 결과를 판단하거나 재계산하지 않는다.
- 정산 실행을 생성하거나 재시도하는 동작은 이번 범위가 아니다. worker의 `processNext` 스케줄과 `TossSettlementRunService.create` 호출을 그대로 둔다.
- 본사 외 역할이 정산 데이터를 보지 못하도록 서버에서 검증한다. 클라이언트의 역할 체크는 안내용이고 권한 판단은 `requireHeadquarters`에 있다.
- 라이브 정산 자료에 접근하는 동작이므로 `settlement-enabled`가 true이고 provider가 `toss-live`일 때만 노출된다. 개발 환경(`fake`)에서는 엔드포인트가 404다.

## 자동 검증

- API: `TossSettlementQueryIntegrationTest` 7건이 종료 코드 0이다. 실행 목록 정렬·테이블 비변경, snapshot join과 null 금액 유지, 상태 필터와 화이트리스 외 값 무시, limit 경계 고정, 없는 runId 빈 응답, 본사 전용 권한(지점 직원 거부·잘못된 세션 인증 오류), HTTP 계층 없는 서비스 동작을 검증한다.
- API: 같은 실행에서 `TossSettlementReconciliationIntegrationTest` 2건·`TossSettlementIntegrationTest` 2건·`CustomerSelfServiceReservationChangeIntegrationTest` 6건·`ReservationChangeSettlementIntegrationTest` 10건을 함께 실행해 종료 코드 0을 확인했다. 기존 settlement·변경 정산 영역에 회귀가 없다.
- API: `mvnw compile`·`mvnw test-compile` 종료 코드 0.
- 관리자: `tsc --noEmit` 종료 코드 0. `eslint`는 변경 파일 4개에서 에러 0·경고 3건(`react-hooks/set-state-in-effect`)이며 기존 `daily-operations.tsx` 등과 같은 패턴이다.
- 웹: `apps/web` `tsc -b` 종료 코드 0. 고객 웹은 이번 변경에 닿지 않는다.
- 실제 환경: 개발 API 4080 health `UP`, 본사 세션 로그인 성공, 관리자 4001의 `/dashboard/settlements` HTTP 200과 `settlement-reconciliation.tsx`·페이지 청크 정상 로드를 확인했다. 개발 API는 `PAYMENT_PROVIDER=fake`라 해당 프로파일에서 컨트롤러가 로드되지 않아 `/api/staff/settlements/runs`가 404다. 이는 설계된 조건 동작이다.

## 사용자 브라우저 검증

- 라이브 정산 환경(`PAYMENT_PROVIDER=toss-live`, `TOSS_SETTLEMENT_ENABLED=true`)에서 본사 계정으로 `/dashboard/settlements`를 열어 실행 목록·상세·상태 필터가 표시되는지 확인한다.
- 지점 직원 계정으로 메뉴에 "정산·대사"가 보이지 않고 URL 직접 접근 시 본사 전용 안내가 나오는지 확인한다.
- 정산 실행이 완료된 뒤 mismatch·pending 항목이 올바르게 표시되는지 확인한다.

## 미검증 항목

- 실제 토스 라이브 정산 API 호출과 snapshot 저장. worker가 라이브 키로 동작한 적이 없다.
- 전체 backend suite 실행. settlement·변경 정산 집중 영역만 실행했다.
- Playwright 브라우저·UI 회귀와 라이브 PostgreSQL 개발 DB.
- 정산 실행 생성·재시도 화면. 이번에는 읽기 전용 조회만 구현했다.
- 대사 결과의 장기 원장 정합성. 활성 환경에서 여러 정산 실행이 누적될 때 snapshot 중복 저장과 reconciliation 재생성 동작은 서버 테스트만 확인했다.
