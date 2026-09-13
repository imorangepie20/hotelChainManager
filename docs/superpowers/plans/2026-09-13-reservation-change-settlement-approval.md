# 예약 변경 정산·승인 구현 계획

> **작업 에이전트:** 필수 하위 스킬인 `subagent-driven-development`(권장) 또는 `executing-plans`를 사용해 이 계획을 작업 단위로 실행한다. 진행 상태는 체크박스(`- [ ]`)로 기록한다.

**목표:** 체크인 전 예약 변경을 100,000원 직접 처리 한도, 본사 승인, fake gateway 정산, 일자 재고 hold와 복구 가능한 상태 머신으로 전환한다.

**아키텍처:** 기존 예약은 정산 완료 전까지 유지하고 `reservationchange` 패키지의 요청 aggregate가 견적·승인·재고 hold·결제 조정·최종 적용을 조율한다. DB outbox와 멱등 gateway 결과가 외부 작업을 트랜잭션에서 분리하며, 기존 숙박 변경 계산은 공통 견적·적용 서비스로 재사용한다. 실제 PG adapter는 이 계획에 포함하지 않고 fake gateway 계약과 sandbox 연결 지점까지만 만든다.

**기술 스택:** Java 21, Spring Boot 4.1.1, JDBC, PostgreSQL 16.15, Flyway, Next.js 16.2.7, React 19.2.4, TypeScript, Vite 8.2.2, Playwright 1.60.0

**설계 문서:** `docs/superpowers/specs/2026-09-13-reservation-change-settlement-approval-design.md`

## 전역 제약

- `BRANCH_STAFF`의 직접 처리 한도는 추가 결제·부분 환불 모두 절대 차액 100,000원이다. 100,001원부터 `HQ_ADMIN` 승인이 필요하다.
- 승인 대기 중에는 `inventory_day`를 변경하지 않는다.
- 승인 요청 TTL은 24시간 또는 지점 현지 체크인일 00:00 중 먼저 도달하는 시각이다.
- 결제 링크와 대상 재고 hold TTL은 15분이다.
- 동일 객실 유형에서 기존·신규 숙박일이 겹치면 기존 confirmed를 자기 재고로 인정하고 순증가 날짜만 held를 증가시킨다.
- 실제 PG, 이메일·문자 발송, 고객 직접 조건 편집, 체크인 당일 예외 승인은 구현하지 않는다.
- 운영 환경에서 원 결제 거래가 없는 예약은 실제 환불 가능 상태로 가장하지 않는다.
- 가격·재고·승인·상태 전이는 Spring Boot가 결정하며 관리자와 고객 웹은 서버 action만 표시한다.
- mutation은 직원·요청 내용을 포함한 `Idempotency-Key` 해시와 request version을 검증한다.
- 신규 migration은 additive하게 작성하고 이미 적용된 V33 파일을 수정하지 않는다.
- 관리자 코드를 변경하기 전에 `SDTPL_ADM/AGENTS.md`, `node_modules/next/dist/docs/01-app/01-getting-started/05-server-and-client-components.md`, `node_modules/next/dist/docs/01-app/02-guides/forms.md`, `node_modules/next/dist/docs/03-architecture/accessibility.md`를 다시 읽는다.
- 관련 직접 테스트와 변경 경로 회귀를 먼저 실행한다. 전체 suite는 변경 범위가 요구하는 마지막 checkpoint에서만 실행한다.
- 문서와 사용자 문구는 한국어로 작성하고 비밀값, raw 공개 token, 카드 정보와 PG secret을 로그·Git에 넣지 않는다.

## 파일 맵

### 백엔드 승인 기반

- Create `services/api/src/main/resources/db/migration/V34__reservation_change_approval_foundation.sql` — 요청·견적·승인·event와 예약 operation revision.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangePolicy.java` — 100,000원·24시간·15분 설정.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationStayQuoteService.java` — 기존 예약 snapshot과 서버 견적.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRequestService.java` — 요청 생성·목록·상세·재견적.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApprovalService.java` — 본사 승인·반려·승인 무효화.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeMutationGuard.java` — 기존 예약 mutation과 활성 정산 요청의 상호 배제.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java` — 직원 정책·요청·승인 API.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeViews.java` — package-private query records와 action 계산.
- Create public request/response records in `services/api/src/main/java/team/hotelchain/reservationchange/` — controller JSON 계약.
- Modify `services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeService.java` — 공통 견적 사용과 직접 적용 gate.
- Modify reservation·operations mutation services — operation revision과 request 무효화.

### 백엔드 정산

- Create `services/api/src/main/resources/db/migration/V35__reservation_change_settlement.sql` — hold·원 거래·조정 attempt·outbox·고객 session·V33 연결.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeHoldService.java` — 날짜별 hold 확보·반환.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/PaymentAdjustmentGateway.java` — provider 중립 계약.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/FakePaymentAdjustmentGateway.java` — 개발·테스트 adapter.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/FakePaymentAdjustmentController.java` — fake 전용 hosted checkout·callback simulator.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java` — 링크·환불 command와 gateway 결과 반영.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeReconciliationService.java` — 본사 수동 조회·적용·보상 환불 결정을 검증.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeReconciliationRequest.java` — 본사 조정 action·version·사유 계약.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/SettlementCommandClaim.java` — outbox lease claim 값.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyService.java` — 최종 재고·예약·V33 원자 적용.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyResult.java` — apply 결과 값.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeOutboxWorker.java` — outbox claim·gateway 호출.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyJob.java` — READY request apply claim·재시도.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeExpiryJob.java` — 승인·링크·hold 만료.
- Create `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangePaymentController.java` — fragment token 교환·현재 결제·checkout.
- Modify `services/api/src/main/java/team/hotelchain/payment/TestPaymentService.java` — 성공 테스트 결제의 fake 원 거래 기록.
- Modify `services/api/src/main/resources/application.yml`, `services/api/src/test/resources/application.yml`, `compose.yaml` — 기능 플래그·TTL·fake adapter.

### 프런트엔드와 테스트

- Modify `SDTPL_ADM/src/lib/staff-api.ts` — 정책·요청·승인·정산 DTO와 API.
- Create `SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx` — 예약 상세의 요청 생성·진행 상태.
- Create `SDTPL_ADM/src/components/hotel-admin/reservation-change-approval-queue.tsx` — 본사 승인 대기.
- Create `SDTPL_ADM/src/components/hotel-admin/reservation-change-timeline.tsx` — audit event 표시.
- Modify `SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx` — 새 컴포넌트 연결과 직접 변경 UI 제거.
- Create `SDTPL_ADM/e2e/staff-reservation-change-approval.spec.ts` and `SDTPL_ADM/e2e/staff-reservation-change-settlement.spec.ts`.
- Modify `apps/web/src/lib/api.ts`, `apps/web/src/lib/customer-route.ts`, `apps/web/src/App.tsx`, `apps/web/src/styles.css`.
- Create `apps/web/src/components/reservation-change-payment-page.tsx` and `apps/web/src/lib/reservation-change-payment-session.ts`.
- Create `apps/web/src/lib/reservation-change-payment-session.test.ts`, `SDTPL_ADM/e2e/customer-reservation-change-payment.spec.ts`, and `SDTPL_ADM/playwright.reservation-change-payment.config.ts`.
- Create `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApprovalIntegrationTest.java`.
- Create `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeMutationGuardIntegrationTest.java`.
- Create `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeHoldIntegrationTest.java`.
- Create `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java`.
- Create `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApplyIntegrationTest.java`.
- Create `docs/changes/2026-09-13-reservation-change-settlement-approval.md` and modify `docs/overview/current-development-context.md` after verification.

---

## 체크포인트 A — 영속 견적과 승인 기반

### 작업 1: 승인 기반 스키마 추가

**파일:**
- Create: `services/api/src/main/resources/db/migration/V34__reservation_change_approval_foundation.sql`
- Create/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApprovalIntegrationTest.java`

**계약:**
- Produces: `reservation.operation_revision`, `reservation_change_request`, `reservation_change_quote`, `reservation_change_quote_night`, `reservation_change_approval`, `reservation_change_event`.
- Consumes: existing `reservation`, `room_type`, `rate_plan`, `hotel`, `staff_member` tables.

- [ ] **Step 1: Write the failing migration contract test**

Add a `@SpringBootTest` test that queries `information_schema.columns` and PostgreSQL indexes. Assert `operation_revision = 0`, the five new tables exist, and two open requests for one reservation violate the active-request partial unique index.

```java
@Test
void createsApprovalFoundationAndAllowsOnlyOneActiveRequest() {
    assertThat(jdbc.queryForObject(
            "select operation_revision from reservation where id = ?",
            Long.class, RESERVATION_ID)).isZero();
    assertThatThrownBy(() -> insertSecondActiveRequest())
            .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeApprovalIntegrationTest test`

Expected: FAIL because V34 tables and `operation_revision` do not exist.

- [ ] **Step 3: Add V34 with exact database constraints**

Use `BIGINT` for KRW, `TIMESTAMPTZ` for instants, `DATE` for stay dates, and CHECK constraints for these status values:

```sql
ALTER TABLE reservation
    ADD COLUMN operation_revision BIGINT NOT NULL DEFAULT 0 CHECK (operation_revision >= 0);

CREATE TABLE reservation_change_request (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id),
    hotel_id UUID NOT NULL REFERENCES hotel(id),
    base_operation_revision BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN (
        'PENDING_APPROVAL','APPROVED','AWAITING_PAYMENT','REFUND_PENDING',
        'READY_TO_APPLY','APPLYING','COMPLETED','REJECTED','CANCELLED',
        'EXPIRED','RECONCILIATION_REQUIRED')),
    settlement_direction VARCHAR(20) NOT NULL CHECK (settlement_direction IN ('CHARGE','REFUND','NONE')),
    requested_by UUID NOT NULL REFERENCES staff_member(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    previous_check_in DATE NOT NULL,
    previous_check_out DATE NOT NULL,
    previous_room_type_id UUID NOT NULL REFERENCES room_type(id),
    previous_rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    target_check_in DATE NOT NULL,
    target_check_out DATE NOT NULL,
    target_room_type_id UUID NOT NULL REFERENCES room_type(id),
    target_rate_plan_id UUID NOT NULL REFERENCES rate_plan(id),
    rooms INTEGER NOT NULL CHECK (rooms > 0),
    adults INTEGER NOT NULL CHECK (adults > 0),
    children INTEGER NOT NULL CHECK (children >= 0),
    current_quote_id UUID,
    approval_limit_krw BIGINT CHECK (approval_limit_krw >= 0),
    approval_expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    error_code VARCHAR(80),
    error_summary VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, idempotency_key),
    CHECK (previous_check_out > previous_check_in),
    CHECK (target_check_out > target_check_in)
);
```

Create quote, quote-night, approval, and event FKs after both parent tables exist. Give `reservation_change_event` an optional `dedupe_key` with a partial unique index for external/worker event replay. Add a partial unique index on reservation ID for all non-terminal states including `RECONCILIATION_REQUIRED`.

- [ ] **Step 4: Re-run migration test**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeApprovalIntegrationTest test`

Expected: PASS for the schema contract and unique active request.

- [ ] **Step 5: Commit Task 1**

```powershell
git add -- services/api/src/main/resources/db/migration/V34__reservation_change_approval_foundation.sql services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApprovalIntegrationTest.java
git commit -m "feat(reservations): add change approval schema"
```

### 작업 2: 서버 소유 숙박 견적 경계 추출

**파일:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationStayQuoteService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationStayQuote.java`
- Create/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationStayQuoteIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeService.java`
- Test: `services/api/src/test/java/team/hotelchain/reservation/StaffReservationStayChangeIntegrationTest.java`

**계약:**
- Produces: `ReservationStayQuoteService.quote(UUID reservationId, LocalDate checkIn, LocalDate checkOut, boolean lockReservation)`.
- Produces: `ReservationStayQuote.selected(UUID roomTypeId, UUID ratePlanId)` returning a `SelectedStayOffer` with nightly prices, total, difference, capacity and remaining.
- Consumes: existing availability SQL, hotel timezone, own-inventory correction and `NightlyPrice`.

- [ ] **Step 1: Write quote seam tests before extraction**

Cover current room type overlap, target room type, incomplete rate days, capacity, missing inventory and lock mode. Assert the selected offer values, not private SQL structure.

```java
ReservationStayQuote quote = quotes.quote(RESERVATION, date("2026-09-15"), date("2026-09-18"), false);
SelectedStayOffer offer = quote.selected(DELUXE, BREAKFAST);
assertThat(offer.totalKrw()).isEqualTo(510_000L);
assertThat(offer.differenceKrw()).isEqualTo(60_000L);
assertThat(offer.nightlyPrices()).extracting(NightlyPrice::date)
        .containsExactly(date("2026-09-15"), date("2026-09-16"), date("2026-09-17"));
```

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationStayQuoteIntegrationTest test`

Expected: compilation FAIL because quote types do not exist.

- [ ] **Step 3: Move only quote and reservation snapshot logic**

Keep authorization and mutation in `StaffReservationStayChangeService`. Move date validation, reservation snapshot loading, offer grouping, capacity, full-night rate validation and own confirmed inventory correction into the new service. Do not change response JSON or error codes of the existing preview.

```java
public ReservationStayQuote quote(
        UUID reservationId, LocalDate checkIn, LocalDate checkOut, boolean lockReservation) {
    StayDates dates = requireDates(checkIn, checkOut);
    ReservationStaySnapshot reservation = loadReservation(reservationId, lockReservation);
    return new ReservationStayQuote(reservation, dates, findOffers(reservation, dates));
}
```

- [ ] **Step 4: Run direct and existing regression tests**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationStayQuoteIntegrationTest,StaffReservationStayChangeIntegrationTest' test`

Expected: all tests PASS and existing preview/update contracts remain unchanged.

- [ ] **Step 5: Commit Task 2**

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationStayQuoteService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationStayQuote.java services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeService.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationStayQuoteIntegrationTest.java services/api/src/test/java/team/hotelchain/reservation/StaffReservationStayChangeIntegrationTest.java
git commit -m "refactor(reservations): share stay quote calculation"
```

### 작업 3: 요청 생성·정책·상세·승인 API 구현

**파일:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangePolicy.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRequestService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApprovalService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeViews.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CreateReservationChangeRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRequestView.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApprovalRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRejectionRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeVersionRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangePolicyView.java`
- Modify/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApprovalIntegrationTest.java`
- Modify: `services/api/src/main/resources/application.yml`

**계약:**
- Produces: `POST /api/staff/reservations/{id}/change-requests`.
- Produces: `GET /api/staff/reservation-change-policy`, `GET /api/staff/reservation-change-requests?status=&hotelId=`, `GET /api/staff/reservation-change-requests/{id}`, and POST endpoints `/{id}/approve`, `/{id}/reject`, `/{id}/reprice`, `/{id}/cancel`.
- Produces: `ReservationChangeRequestView` with `version`, quote, approval, events and `Set<String> actions`.
- Consumes: `ReservationStayQuoteService`, `StaffAccessService`, `ReservationAccess.sha256`.

- [ ] **Step 1: Add failing HTTP and transaction tests**

Test 100,000/100,001 boundaries for charge and refund, HQ direct approval, other-hotel denial, expected-total price conflict, duplicate idempotent replay, different staff conflict, approval expiry, approve/reject permissions, required rejection reason, stale request version, immutable quote-night rows and cancel allowed only from `PENDING_APPROVAL`/`APPROVED` before any hold or adjustment exists.

```java
mockMvc.perform(post("/api/staff/reservations/{id}/change-requests", RESERVATION)
        .header("X-Staff-Session", branchToken)
        .header("Idempotency-Key", "change-create-1")
        .contentType(APPLICATION_JSON)
        .content(requestJson(500_000L)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.status").value("APPROVED"))
    .andExpect(jsonPath("$.approval.limitKrw").value(100_000));
```

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeApprovalIntegrationTest test`

Expected: compilation FAIL because controller, policy and services do not exist.

- [ ] **Step 3: Implement the minimal request aggregate**

Bind exact configuration:

```yaml
reservation:
  change:
    settlement-enabled: ${RESERVATION_CHANGE_SETTLEMENT_ENABLED:false}
    direct-limit-krw: 100000
    approval-ttl: 24h
    hold-ttl: 15m
```

Compute request hash from staff ID, reservation ID, target dates, room type, rate plan and expected total. Create quote/request/automatic approval/event in one transaction. For branch values above 100,000 create `PENDING_APPROVAL` without an approval row. Return only actions valid for the authenticated role and state.

- [ ] **Step 4: Implement approval, rejection and reprice**

Require `HQ_ADMIN` for approve/reject. Approval limit equals the approved quote absolute delta. Reprice creates a new immutable quote; preserve branch auto approval at or below 100,000 and preserve HQ approval only for the same direction and amount at or below the approval limit. Otherwise append `APPROVAL_INVALIDATED` and return to `PENDING_APPROVAL`. Cancel requires the current request version, is idempotent for the same key, and moves only approval-only requests to `CANCELLED` with an event.

- [ ] **Step 5: Run request/approval tests**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeApprovalIntegrationTest,ReservationStayQuoteIntegrationTest,StaffReservationStayChangeIntegrationTest' test`

Expected: all tests PASS.

- [ ] **Step 6: Commit Task 3**

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangePolicy.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRequestService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApprovalService.java services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeViews.java services/api/src/main/java/team/hotelchain/reservationchange/CreateReservationChangeRequest.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRequestView.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApprovalRequest.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRejectionRequest.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeVersionRequest.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangePolicyView.java services/api/src/main/resources/application.yml services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApprovalIntegrationTest.java
git commit -m "feat(reservations): add change approval workflow"
```

### 작업 4: operation revision으로 기존 예약 mutation 보호

**파일:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeMutationGuard.java`
- Create/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeMutationGuardIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/StaffReservationPartyUpdateService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/CancellationService.java`
- Modify: `services/api/src/main/java/team/hotelchain/operations/StaffOperationsService.java`
- Modify: `services/api/src/main/java/team/hotelchain/operations/StaffRoomReassignmentService.java`
- Test: matching existing integration tests in reservation and operations packages.

**계약:**
- Produces: `prepareCriticalMutation(UUID reservationId)` for cancellation, party, assignment, reassignment and direct stay mutation; `rejectOperationalTransitionWhenActive(UUID reservationId)` for check-in and no-show; and `incrementRevision(UUID reservationId)` after the guarded mutation succeeds.
- Behavior: ordinary critical mutations expire `PENDING_APPROVAL`/`APPROVED` as `RESERVATION_CHANGED` and reject settlement-active states. Check-in and no-show reject every active request, including approval-only states. The caller increments `reservation.operation_revision` exactly once only after its mutation succeeds.

- [ ] **Step 1: Write failing guard tests**

For party update, room assignment, reassignment, cancellation and direct stay update, assert pending approval expires and revision increments. For check-in and no-show, assert both approval-only and settlement-active requests return 409 with no reservation/request changes. For `AWAITING_PAYMENT`, assert every critical mutation returns 409. Assert guest name/email correction neither increments revision nor expires the request.

```java
assertThatThrownBy(() -> operations.checkIn(branchToken, RESERVATION))
        .isInstanceOf(BusinessConflictException.class)
        .extracting(error -> ((BusinessConflictException) error).code())
        .isEqualTo("RESERVATION_CHANGE_SETTLEMENT_ACTIVE");
```

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeMutationGuardIntegrationTest test`

Expected: FAIL because current mutation paths ignore active change requests.

- [ ] **Step 3: Implement and wire the guard surgically**

Call the appropriate guard only after replay/no-op checks while the reservation row is locked. Keep each existing service's current authorization, lock order and audit insert. Increment the revision after the domain update but inside the same transaction so a failed/no-op mutation cannot consume a revision.

```java
public void prepareCriticalMutation(UUID reservationId) {
    ActiveChange state = lockActiveChange(reservationId);
    if (state != null && SETTLEMENT_ACTIVE.contains(state.status())) {
        throw new BusinessConflictException(
                "RESERVATION_CHANGE_SETTLEMENT_ACTIVE",
                "예약 변경 정산을 먼저 완료하거나 조정해 주세요.");
    }
    expireApprovalOnlyChange(state);
}

public void rejectOperationalTransitionWhenActive(UUID reservationId) {
    if (lockActiveChange(reservationId) != null) {
        throw activeSettlementConflict();
    }
}

public void incrementRevision(UUID reservationId) {
    jdbc.update("update reservation set operation_revision = operation_revision + 1 where id = ?", reservationId);
}
```

- [ ] **Step 4: Run focused mutation regression**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeMutationGuardIntegrationTest,StaffReservationPartyUpdateIntegrationTest,StaffReservationStayChangeIntegrationTest,CancellationIntegrationTest,StaffOperationsIntegrationTest,StaffRoomReassignmentIntegrationTest,StaffReservationGuestUpdateIntegrationTest' test`

Expected: all tests PASS; prior mutation behavior is preserved outside active settlement.

- [ ] **Step 5: Commit Task 4**

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeMutationGuard.java services/api/src/main/java/team/hotelchain/reservation/StaffReservationPartyUpdateService.java services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeService.java services/api/src/main/java/team/hotelchain/reservation/CancellationService.java services/api/src/main/java/team/hotelchain/operations/StaffOperationsService.java services/api/src/main/java/team/hotelchain/operations/StaffRoomReassignmentService.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeMutationGuardIntegrationTest.java
git commit -m "feat(reservations): guard active change settlements"
```

### 작업 5: 직원 요청·본사 승인 UI 추가

**파일:**
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Create: `SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx`
- Create: `SDTPL_ADM/src/components/hotel-admin/reservation-change-approval-queue.tsx`
- Create: `SDTPL_ADM/src/components/hotel-admin/reservation-change-timeline.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx`
- Create/Test: `SDTPL_ADM/e2e/staff-reservation-change-approval.spec.ts`

**계약:**
- Consumes: policy/list/detail/create/approve/reject/reprice JSON from Task 3.
- Produces: `ReservationChangePanel`, `ReservationChangeApprovalQueue`, `ReservationChangeTimeline` React components.
- Preserves: existing reservation search, assignment, guest, party and cancellation interactions.

- [ ] **Step 1: Read the required Next.js 16 documentation**

Run from `SDTPL_ADM`:

```powershell
Get-Content -Raw AGENTS.md
Get-Content -Raw node_modules/next/dist/docs/01-app/01-getting-started/05-server-and-client-components.md
Get-Content -Raw node_modules/next/dist/docs/01-app/02-guides/forms.md
Get-Content -Raw node_modules/next/dist/docs/03-architecture/accessibility.md
```

- [ ] **Step 2: Write failing Playwright approval tests**

Mock staff APIs and verify branch 100,000 direct status, 100,001 approval request, no inventory/settlement call during approval, HQ-only approval filter, existing/new conditions, daily prices, approve, rejection reason, request version and response-loss idempotency key reuse. At 390×844 verify no horizontal overflow and keyboard focus returns after the confirmation dialog.

```ts
await page.getByRole("button", { name: "본사 승인 요청" }).click();
await expect(page.getByRole("status", { name: "예약 변경 상태" }))
  .toContainText("본사 승인 대기");
expect(calls.hold).toBe(0);
expect(calls.payment).toBe(0);
```

- [ ] **Step 3: Verify RED**

Run: `cd SDTPL_ADM; pnpm exec playwright test e2e/staff-reservation-change-approval.spec.ts`

Expected: FAIL because the request and approval UI is absent.

- [ ] **Step 4: Add typed API methods and split the existing editor**

Move stay-change form behavior out of the 1,000-line `reservation-management.tsx`. The new panel loads policy and active request when detail opens, uses server `actions`, and never infers approval from role and amount alone. Keep one idempotency key per unchanged form submission.

```ts
export type ReservationChangeRequestView = {
  id: string;
  reservationId: string;
  status: ReservationChangeStatus;
  version: number;
  quote: ReservationChangeQuote;
  approval: ReservationChangeApproval | null;
  actions: string[];
  events: ReservationChangeEvent[];
};
```

- [ ] **Step 5: Add HQ approval queue without a new sidebar destination**

Show `승인 대기` only for `HQ_ADMIN` inside the reservation management page. Reuse Card, Table, Dialog, AlertDialog, Label, Input and Button. Require a nonblank rejection reason and send the current request version.

- [ ] **Step 6: Run UI checks**

Run:

```powershell
cd SDTPL_ADM
pnpm exec playwright test e2e/staff-reservation-change-approval.spec.ts e2e/staff-reservations.spec.ts
pnpm exec eslint src/components/hotel-admin/reservation-management.tsx src/components/hotel-admin/reservation-change-panel.tsx src/components/hotel-admin/reservation-change-approval-queue.tsx src/components/hotel-admin/reservation-change-timeline.tsx src/lib/staff-api.ts e2e/staff-reservation-change-approval.spec.ts
pnpm exec tsc --noEmit
```

Expected: Playwright, ESLint and TypeScript PASS.

- [ ] **Step 7: Commit and stop at Checkpoint A**

```powershell
git add -- SDTPL_ADM/src/lib/staff-api.ts SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx SDTPL_ADM/src/components/hotel-admin/reservation-change-approval-queue.tsx SDTPL_ADM/src/components/hotel-admin/reservation-change-timeline.tsx SDTPL_ADM/e2e/staff-reservation-change-approval.spec.ts
git commit -m "feat(reservations): add change approval workspace"
```

체크포인트 A 완료 기준: 요청·견적·승인이 영속화되고, 승인 중에는 재고를 hold하지 않으며, 모든 중요 mutation이 올바르게 무효화 또는 차단되고, 지점·본사 UI 검증이 통과한다. 정산 테이블을 시작하기 전에 이 체크포인트를 검토한다.

---

## 체크포인트 B — fake 정산 오케스트레이션

### 작업 6: 정산 스키마와 fake 원 거래 추가

**파일:**
- Create: `services/api/src/main/resources/db/migration/V35__reservation_change_settlement.sql`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/PaymentAdjustmentGateway.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/FakePaymentAdjustmentGateway.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/TestPaymentService.java`
- Modify: `services/api/src/main/resources/application.yml`
- Modify: `services/api/src/test/resources/application.yml`
- Modify: `compose.yaml`
- Create/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java`
- Test: `services/api/src/test/java/team/hotelchain/reservation/ReservationIntegrationTest.java`

**계약:**
- Produces: `payment_transaction`, `reservation_change_hold_day`, `payment_adjustment_attempt`, `reservation_change_outbox`, `reservation_change_customer_session` and nullable V33 `change_request_id`.
- Produces: nested provider-neutral gateway command/result records and `PaymentAdjustmentGateway.createCheckout`, `.refund`, `.query`.
- Consumes: successful test payment and existing reservation total.

- [ ] **Step 1: Write failing schema and fake transaction tests**

Assert all V35 tables and unique keys, no raw token column, V33 nullable link, a database CHECK that `refunded_amount_krw <= captured_amount_krw`, and one fake `payment_transaction` for a successful test payment. Existing failed and expired test payments must not create a captured transaction.

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeSettlementIntegrationTest,ReservationIntegrationTest' test`

Expected: FAIL because settlement schema and gateway contract do not exist.

- [ ] **Step 3: Add V35 and provider-neutral types**

Use statuses `NEW`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `UNKNOWN` for adjustment attempts and `PENDING`, `PROCESSING`, `DONE`, `FAILED` for outbox commands. Store only `public_token_hash CHAR(64)` and customer session token hashes. Add `claim_token`, `attempt_count`, `next_attempt_at`, `lease_expires_at` to outbox.

```java
public interface PaymentAdjustmentGateway {
    GatewayAdjustmentResult createCheckout(GatewayCheckoutCommand command);
    GatewayAdjustmentResult refund(GatewayRefundCommand command);
    GatewayAdjustmentResult query(GatewayQueryCommand command);

    record GatewayCheckoutCommand(
            UUID attemptId, String idempotencyKey, long amountKrw,
            String currency, URI returnUrl) {}

    record GatewayRefundCommand(
            UUID attemptId, String idempotencyKey, String originalGatewayTransactionId,
            long amountKrw, String currency) {}

    record GatewayQueryCommand(
            UUID attemptId, String gatewayTransactionId) {}

    record GatewayAdjustmentResult(
            String providerEventId, String gatewayTransactionId,
            GatewayResultStatus status, URI checkoutUrl, String errorCode) {}

    enum GatewayResultStatus { PENDING, SUCCEEDED, FAILED, UNKNOWN }
}
```

`payment_adjustment_attempt.adjustment_type` is one of `CREATE_CHECKOUT`, `REFUND_ORIGINAL`, or `REFUND_ADJUSTMENT`. A successful additional charge is also inserted into `payment_transaction` so a later operator-approved compensating refund has a concrete transaction to reference.

- [ ] **Step 4: Record fake original charges from test payment**

On first successful test payment insert `provider = 'FAKE'`, merchant account `LOCAL`, captured amount and currency in the same transaction. Idempotent payment replay must not insert a second transaction.

- [ ] **Step 5: Configure the adapter and feature flags**

Set the test profile to `reservation.change.settlement-enabled: true` and `reservation.change.gateway: fake`, while the production default remains disabled with no usable real adapter. In Compose development, explicitly enable fake settlement without adding credentials.

- [ ] **Step 6: Run schema/payment regression and commit**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeSettlementIntegrationTest,ReservationIntegrationTest' test`

Expected: PASS.

```powershell
git add -- services/api/src/main/resources/db/migration/V35__reservation_change_settlement.sql services/api/src/main/java/team/hotelchain/reservationchange/PaymentAdjustmentGateway.java services/api/src/main/java/team/hotelchain/reservationchange/FakePaymentAdjustmentGateway.java services/api/src/main/java/team/hotelchain/payment/TestPaymentService.java services/api/src/main/resources/application.yml services/api/src/test/resources/application.yml compose.yaml services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java services/api/src/test/java/team/hotelchain/reservation/ReservationIntegrationTest.java
git commit -m "feat(reservations): add fake settlement foundation"
```

### 작업 7: 일자별 대상 재고 hold와 만료 구현

**파일:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeHoldService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeExpiryJob.java`
- Create/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeHoldIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRequestService.java`

**계약:**
- Produces: `ReservationChangeHoldService.HoldResult acquire(UUID requestId, long expectedVersion)` and `void release(UUID requestId, String reason)`.
- Consumes: approved current quote and `ReservationStayQuoteService` availability.
- Guarantees: reservation row → request row → sorted `(room_type_id, stay_date)` inventory lock order.

- [ ] **Step 1: Write failing hold concurrency tests**

Cover no hold before approval, all target dates held atomically, some-date sold out rollback, same-type overlap as `EXISTING_CONFIRMED`, net-new date hold, different room type full hold, last-room concurrent requests, release exactly once, check-in-day expiry, and an approved zero-delta reprice that acquires the hold and moves directly to `READY_TO_APPLY` without an adjustment attempt.

```java
assertThat(inventory(ROOM_TYPE, overlapDate).held()).isZero();
assertThat(holdKind(REQUEST, overlapDate)).isEqualTo("EXISTING_CONFIRMED");
assertThat(inventory(ROOM_TYPE, addedDate).held()).isEqualTo(1);
```

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeHoldIntegrationTest test`

Expected: FAIL because holds are not implemented.

- [ ] **Step 3: Implement acquire and release**

Reprice before locking, then lock and validate again inside the transaction. Insert one hold-day row for every target stay date. Increment `inventory_day.held` only for `NEW_HOLD`. Release only rows whose status is `HELD` and update both inventory and hold rows in one transaction.

When an explicit reprice produces an approved `NONE` direction, `ReservationChangeRequestService` calls acquire in the same orchestration path and advances to `READY_TO_APPLY`. CHARGE and REFUND stay `APPROVED` until their dedicated staff action acquires the hold; amount and target conditions are never accepted from those action requests.

Define the returned value in the service so the expiry/version contract is concrete:

```java
public record HoldResult(
        UUID requestId, long requestVersion, Instant expiresAt, int newHeldNights) {}
```

- [ ] **Step 4: Add bounded expiry work**

The scheduled job claims one expired request at a time with `FOR UPDATE SKIP LOCKED`. It may expire approval-only requests immediately. It must not release holds when an adjustment attempt is `PROCESSING`, `SUCCEEDED` or `UNKNOWN`; those requests move to or remain `RECONCILIATION_REQUIRED`.

- [ ] **Step 5: Run hold tests and commit**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeHoldIntegrationTest,ReservationChangeApprovalIntegrationTest' test`

Expected: PASS.

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeHoldService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeExpiryJob.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeRequestService.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeHoldIntegrationTest.java
git commit -m "feat(reservations): hold target stay inventory"
```

### 작업 8: 추가 결제 링크와 안전한 고객 세션 오케스트레이션

**파일:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeOutboxWorker.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/SettlementCommandClaim.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/FakePaymentAdjustmentController.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangePaymentController.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangePaymentLinkRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangePaymentView.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java`
- Modify/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java`

**계약:**
- Produces: staff `payment-link` endpoint, public token-to-cookie session exchange, current view and checkout endpoint.
- Produces: `SettlementCommandClaim claimNext()` and claim-token guarded completion methods; fake-only hosted checkout/callback endpoints that feed the same internal gateway-result handler future provider webhooks will use.
- Consumes: `ReservationChangeHoldService`, `PaymentAdjustmentGateway`, `ReservationAccess.sha256`.

- [ ] **Step 1: Write failing payment-link and outbox tests**

Test CHARGE-only action, 43-character base64url token validation, hash-only storage, idempotent URL replay, replacement before checkout, no replacement after checkout, 15-minute expiry, outbox retry/lease, fake success/failure/unknown, duplicate/out-of-order gateway event and token removal from returned API paths. Prove the fake callback route is absent unless both settlement and fake-gateway flags are enabled.

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeSettlementIntegrationTest test`

Expected: FAIL on missing endpoints and outbox worker.

- [ ] **Step 3: Implement the durable command boundary**

In one transaction acquire hold, hash the client token, create a `NEW` adjustment attempt and enqueue `CREATE_CHECKOUT`. The worker claims with lease and calls the gateway after the claim transaction commits. Completion updates only the matching claim token.

```java
public record SettlementCommandClaim(
        UUID outboxId, UUID requestId, UUID attemptId,
        String commandType, int attemptCount, UUID claimToken) {}
```

`FakePaymentAdjustmentController` exposes the fake hosted-checkout simulator only under `reservation.change.settlement-enabled=true` and `reservation.change.gateway=fake`. Its success/failure/unknown callback carries a stable fake event ID into `ReservationChangeSettlementService.recordGatewayResult(...)`; that method enforces the event unique key and current-state checks. No generic unsigned production webhook route is added in this plan.

- [ ] **Step 4: Implement token exchange and cookie session**

`POST /session` accepts `X-Reservation-Change-Token`, stores a hash of a newly generated customer session token, sets `reservation_change_session` as HttpOnly, SameSite=Strict, Path=/api/reservation-change-payments, and Secure outside localhost. GET current exposes only reservation number suffix, target stay, room/rate names, additional amount, currency, expiry, environment label and status. POST checkout returns the already-created fake hosted URL and never accepts an amount from the browser.

- [ ] **Step 5: Run payment-link tests and commit**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeSettlementIntegrationTest,ReservationChangeHoldIntegrationTest' test`

Expected: PASS.

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeOutboxWorker.java services/api/src/main/java/team/hotelchain/reservationchange/SettlementCommandClaim.java services/api/src/main/java/team/hotelchain/reservationchange/FakePaymentAdjustmentController.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangePaymentController.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangePaymentLinkRequest.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangePaymentView.java services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java
git commit -m "feat(reservations): orchestrate change payment links"
```

### 작업 9: 원 거래 부분 환불 오케스트레이션

**파일:**
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeOutboxWorker.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java`
- Modify/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java`

**계약:**
- Produces: staff refund endpoint and `REFUND` outbox command.
- Consumes: captured `payment_transaction`, cumulative refunded amount and current approved quote.

- [ ] **Step 1: Add failing refund tests**

Cover REFUND-only action, no original transaction, wrong provider mode, amount above remaining captured balance, exact cumulative boundary, idempotent retry, explicit failure releasing hold, success to READY, unknown retaining hold and reconciliation, duplicate result event and no second refund command.

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeSettlementIntegrationTest test`

Expected: FAIL because refund action is missing.

- [ ] **Step 3: Implement refund command creation and completion**

Lock original `payment_transaction`, calculate `captured_amount_krw - refunded_amount_krw`, reject `PAYMENT_TRANSACTION_NOT_SETTLEABLE` before acquiring hold, then enqueue one idempotent REFUND. On success atomically increment cumulative refund and move request to `READY_TO_APPLY`. On explicit failure release hold; on unknown preserve hold and set reconciliation status.

- [ ] **Step 4: Run settlement tests and commit**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeSettlementIntegrationTest,ReservationIntegrationTest' test`

Expected: PASS.

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeOutboxWorker.java services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java
git commit -m "feat(reservations): orchestrate change refunds"
```

### 작업 10: 정산된 변경의 단일 적용과 직접 endpoint 차단

**파일:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyResult.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyJob.java`
- Create/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApplyIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeController.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeExpiryJob.java`
- Test: `services/api/src/test/java/team/hotelchain/reservation/StaffReservationStayChangeIntegrationTest.java`

**계약:**
- Produces: `boolean claimAndApplyNext()` and `ReservationChangeRequestView retryApply(...)` for HQ reconciliation.
- Consumes: READY request, successful adjustment or zero delta, hold rows, quote-night rows and V33 audit.
- Guarantees: one reservation/revision update and one V33 `change_request_id` per completed request.

- [ ] **Step 1: Write failing apply and race tests**

Test CHARGE, REFUND and NONE finalization; NEW_HOLD held→confirmed; old confirmed release; same-type overlap net delta; `reservation_night` replacement; total and operation revision; V33 link; duplicate apply; response loss; stale base revision; expired/missing hold; payment success followed by transient DB failure; lease recovery; retry limit to reconciliation.

- [ ] **Step 2: Verify RED**

Run: `cd services/api; .\mvnw.cmd -Dtest=ReservationChangeApplyIntegrationTest test`

Expected: FAIL because apply service does not exist.

- [ ] **Step 3: Implement apply claim and final transaction**

Claim READY as APPLYING with a short lease. In the final transaction lock reservation, request and sorted union inventory. Verify version, revision, no room assignment, future check-in, quote, holds and adjustment status. Apply per-date deltas, update reservation and nights, insert V33/event, and mark COMPLETED.

Define `ReservationChangeApplyResult` as `record ReservationChangeApplyResult(UUID requestId, UUID reservationId, long reservationOperationRevision, Instant completedAt)`. It is an internal worker result and is not serialized directly to either frontend.

```java
@Transactional
public ReservationChangeApplyResult apply(UUID requestId, UUID claimToken) {
    LockedReservation reservation = lockReservationForApply(requestId);
    LockedChange request = lockClaimedRequest(requestId, claimToken);
    verifyApplyInvariants(reservation, request);
    applyInventoryDeltas(reservation, request);
    updateReservationAndNights(reservation, request);
    completeAuditAndRequest(reservation, request);
    return result(request);
}
```

- [ ] **Step 4: Gate the old direct PATCH only when settlement is enabled**

When `reservation.change.settlement-enabled=true`, return 409 `CHANGE_SETTLEMENT_REQUIRED` before direct mutation. Keep the old behavior when false so rollback binaries and existing focused tests remain valid. Internal apply must not call the public controller path.

- [ ] **Step 5: Run apply and old-flow regression**

Run: `cd services/api; .\mvnw.cmd '-Dtest=ReservationChangeApplyIntegrationTest,ReservationChangeSettlementIntegrationTest,ReservationChangeHoldIntegrationTest,StaffReservationStayChangeIntegrationTest,CancellationIntegrationTest,StaffOperationsIntegrationTest' test`

Expected: PASS.

- [ ] **Step 6: Commit Task 10**

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyResult.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyJob.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeExpiryJob.java services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeService.java services/api/src/main/java/team/hotelchain/reservation/StaffReservationStayChangeController.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeApplyIntegrationTest.java
git commit -m "feat(reservations): apply settled stay changes"
```

### 작업 11: 고객 결제 링크 화면 구현

**파일:**
- Create: `apps/web/src/components/reservation-change-payment-page.tsx`
- Create: `apps/web/src/lib/reservation-change-payment-session.ts`
- Create/Test: `apps/web/src/lib/reservation-change-payment-session.test.ts`
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/lib/customer-route.ts`
- Modify: `apps/web/src/lib/customer-route.test.ts`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`
- Create/Test: `SDTPL_ADM/e2e/customer-reservation-change-payment.spec.ts`
- Create: `SDTPL_ADM/playwright.reservation-change-payment.config.ts`

**계약:**
- Consumes: customer session/current/checkout endpoints from Task 8.
- Produces: `/reservation-change-payment#token` route that exchanges fragment before rendering data.
- Preserves: reservation search, CMS preview fragment and current customer routes.

- [ ] **Step 1: Write failing token capture and route tests**

Test exact 43-character token capture, `history.replaceState` before API data load, no sessionStorage/localStorage copy, invalid token rejection, route classification and existing website preview fragment isolation.

```ts
const calls: string[] = []
const token = captureReservationChangePaymentToken(locationLike, (path) => calls.push(path))
expectEqual(token, "A".repeat(43), "43자 token을 반환한다")
expectEqual(calls, ["/reservation-change-payment"], "fragment를 즉시 제거한다")
```

Keep this test dependency-free like `customer-route.test.ts`: define a small `expectEqual`, invoke the exported test function at module load, and run it directly with the repository's Node 24 runtime.

- [ ] **Step 2: Write failing customer Playwright tests**

At 1280 and 390 widths verify fragment removal, session exchange header, masked reservation number, target conditions, amount, expiry, fake checkout success/failure, refresh restoration by cookie, terminal status, no normal booking/CMS data request, keyboard action and no horizontal overflow.

- [ ] **Step 3: Verify RED**

Run:

```powershell
cd apps/web
node src/lib/reservation-change-payment-session.test.ts
node src/lib/customer-route.test.ts
pnpm exec tsc -p tsconfig.app.json --noEmit
cd ../../SDTPL_ADM
pnpm exec playwright test --config=playwright.reservation-change-payment.config.ts
```

Expected: the new direct Node test or Playwright FAIL because the route and page are absent; the existing route test still passes.

- [ ] **Step 4: Implement isolated route and API client**

Return the payment page before normal hotel/CMS effects start. Exchange token with credentials included, clear the fragment immediately, then use the HttpOnly cookie for current/checkout. Never render guest email, staff identity or approval reason.

- [ ] **Step 5: Add focused accessible styling**

Reuse customer design tokens from `styles.css`. Provide visible focus, `role=status` for gateway preparation/completion, `role=alert` for failure, non-color status text, 44px action height and wrapping price/date content at 390px.

- [ ] **Step 6: Run customer tests and build**

Run:

```powershell
cd apps/web
node src/lib/reservation-change-payment-session.test.ts
node src/lib/customer-route.test.ts
pnpm exec tsc -p tsconfig.app.json --noEmit
pnpm run build
cd ../../SDTPL_ADM
pnpm exec playwright test --config=playwright.reservation-change-payment.config.ts
```

Expected: both direct unit tests, TypeScript compile, Vite production build and customer Playwright PASS.

- [ ] **Step 7: Commit Task 11**

```powershell
git add -- apps/web/src/components/reservation-change-payment-page.tsx apps/web/src/lib/reservation-change-payment-session.ts apps/web/src/lib/reservation-change-payment-session.test.ts apps/web/src/lib/api.ts apps/web/src/lib/customer-route.ts apps/web/src/lib/customer-route.test.ts apps/web/src/App.tsx apps/web/src/styles.css SDTPL_ADM/e2e/customer-reservation-change-payment.spec.ts SDTPL_ADM/playwright.reservation-change-payment.config.ts
git commit -m "feat(reservations): add change payment page"
```

### 작업 12: 직원 정산 상태·조정 UI 완성

**파일:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeReconciliationService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeReconciliationRequest.java`
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/reservation-change-approval-queue.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/reservation-change-timeline.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx`
- Create/Test: `SDTPL_ADM/e2e/staff-reservation-change-settlement.spec.ts`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java`
- Modify/Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java`

**계약:**
- Consumes: server actions for payment link, refund, cancel and role/state-specific reconciliation.
- Produces: copyable customer link, refund confirmation, status polling while active, and HQ reconciliation controls backed by `POST /api/staff/reservation-change-requests/{id}/reconcile`.

- [ ] **Step 1: Add failing API and Playwright tests**

Verify only allowed buttons render per action, 32-byte token generation, same token/key retry after response loss, no raw token in logs/events, explicit refund confirmation, polling only in active states, stop on terminal states, COMPLETED list refresh, and HQ-only reconciliation with current version plus required reason. Backend tests cover `QUERY_GATEWAY`, `RETRY_APPLY`, `RELEASE_AFTER_CONFIRMED_FAILURE` and `REFUND_ADJUSTMENT_AND_CANCEL` authorization and state preconditions.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd services/api
.\mvnw.cmd -Dtest=ReservationChangeSettlementIntegrationTest test
cd ../../../SDTPL_ADM
pnpm exec playwright test e2e/staff-reservation-change-settlement.spec.ts
```

Expected: FAIL because settlement actions and reconciliation UI are incomplete.

- [ ] **Step 3: Implement action-driven controls**

Generate raw link token only in browser memory, submit it with a stable idempotency key, and render the returned link in a labeled read-only input. Use server expiry and state; do not start a second settlement after reload. Require confirmation before refund and show that it returns to the original payment method.

- [ ] **Step 4: Implement bounded polling and explicit reconciliation**

Poll detail only for `AWAITING_PAYMENT`, `REFUND_PENDING`, `READY_TO_APPLY`, and `APPLYING`; stop on unmount or terminal state. `RECONCILIATION_REQUIRED` does not auto-retry. The server returns only valid HQ actions:

- `QUERY_GATEWAY` enqueues an idempotent query and remains frozen until the result is recorded.
- `RETRY_APPLY` is available only when the financial adjustment is proven successful.
- `RELEASE_AFTER_CONFIRMED_FAILURE` is available only when the latest query proves no financial change; it releases new holds and cancels the request.
- `REFUND_ADJUSTMENT_AND_CANCEL` is available only for a proven successful additional charge. It creates a manual, reason-audited `REFUND_ADJUSTMENT`; only its success callback releases holds and cancels the request.

A successful original partial refund that cannot be applied has no automatic inverse action and stays `RECONCILIATION_REQUIRED` for external recovery. Every reconciliation mutation is HQ-only, requires a nonblank reason, request version and idempotency key, and appends an event.

- [ ] **Step 5: Run staff UI and backend tests**

Run:

```powershell
cd services/api
.\mvnw.cmd '-Dtest=ReservationChangeSettlementIntegrationTest,ReservationChangeApplyIntegrationTest' test
cd ../../../SDTPL_ADM
pnpm exec playwright test e2e/staff-reservation-change-approval.spec.ts e2e/staff-reservation-change-settlement.spec.ts e2e/staff-reservations.spec.ts
pnpm exec eslint src/components/hotel-admin/reservation-management.tsx src/components/hotel-admin/reservation-change-panel.tsx src/components/hotel-admin/reservation-change-approval-queue.tsx src/components/hotel-admin/reservation-change-timeline.tsx src/lib/staff-api.ts e2e/staff-reservation-change-approval.spec.ts e2e/staff-reservation-change-settlement.spec.ts
pnpm exec tsc --noEmit
```

Expected: all focused tests PASS.

- [ ] **Step 6: Commit Task 12**

```powershell
git add -- services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeReconciliationService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeReconciliationRequest.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeOutboxWorker.java services/api/src/main/java/team/hotelchain/reservationchange/StaffReservationChangeController.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java SDTPL_ADM/src/lib/staff-api.ts SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx SDTPL_ADM/src/components/hotel-admin/reservation-change-approval-queue.tsx SDTPL_ADM/src/components/hotel-admin/reservation-change-timeline.tsx SDTPL_ADM/src/components/hotel-admin/reservation-management.tsx SDTPL_ADM/e2e/staff-reservation-change-settlement.spec.ts
git commit -m "feat(reservations): complete change settlement workspace"
```

### 작업 13: 릴리스 체크포인트 검증·근거 기록·실제 PG 비활성 유지

**파일:**
- Create: `docs/changes/2026-09-13-reservation-change-settlement-approval.md`
- Modify: `docs/overview/current-development-context.md`
- Verify: every file touched in Tasks 1–12.

**계약:**
- Consumes: Checkpoint A and B acceptance evidence.
- Produces: Korean change record with implemented scope, exact commands, results, unverified real-PG scope and rollback order.

- [ ] **Step 1: Run the complete backend change-path suite**

Run:

```powershell
cd services/api
.\mvnw.cmd '-Dtest=ReservationChangeApprovalIntegrationTest,ReservationStayQuoteIntegrationTest,ReservationChangeMutationGuardIntegrationTest,ReservationChangeHoldIntegrationTest,ReservationChangeSettlementIntegrationTest,ReservationChangeApplyIntegrationTest,StaffReservationStayChangeIntegrationTest,StaffReservationPartyUpdateIntegrationTest,StaffReservationGuestUpdateIntegrationTest,CancellationIntegrationTest,StaffOperationsIntegrationTest,StaffRoomReassignmentIntegrationTest,ReservationIntegrationTest' test
```

Expected: zero failures and errors. Record the actual test count from Maven output.

- [ ] **Step 2: Run both frontend release gates**

Run:

```powershell
cd SDTPL_ADM
pnpm exec playwright test e2e/staff-reservation-change-approval.spec.ts e2e/staff-reservation-change-settlement.spec.ts e2e/staff-reservations.spec.ts
pnpm exec playwright test --config=playwright.reservation-change-payment.config.ts
pnpm exec tsc --noEmit
pnpm run build
cd ../apps/web
pnpm run build
```

Expected: both production builds, staff E2E and customer payment E2E PASS.

- [ ] **Step 3: Rebuild local API and verify migrations/readiness**

Run from repository root:

```powershell
docker compose up -d --build api
docker compose logs --tail 160 api
Invoke-WebRequest -UseBasicParsing http://localhost:4080/actuator/health/readiness
```

Expected: Flyway reaches V35, readiness is `UP`, gateway mode is fake, and no credential is required.

- [ ] **Step 4: Perform safe browser verification**

At `http://localhost:4001/dashboard/reservations`, use a test reservation only. Verify branch direct/approval-required labels, HQ approval, fake payment link, customer `http://127.0.0.1:4000/reservation-change-payment#token`, fragment removal, amount/status, and final list refresh. Do not send email/SMS, enter real card data, use an actual customer reservation, or enable a real provider.

- [ ] **Step 5: Write Korean evidence and remaining scope**

The change record must include:

```markdown
## 구현 범위
## 상태·금액 정책
## 검증 결과
## 실제 PG 전 남은 작업
## 배포·롤백
```

State explicitly that provider selection, secret setup, signed production webhook, real sandbox transaction, fee/accounting reconciliation and automated customer delivery remain unverified.

- [ ] **Step 6: Check diff hygiene and final status**

Run:

```powershell
git diff --check
git status --short --branch
```

Expected: no whitespace errors and only intended source/test/migration/docs files. Do not stage `.tmp/`, build output, database dumps, media files, environment files or secrets.

- [ ] **Step 7: Commit the release evidence**

```powershell
git add -- docs/changes/2026-09-13-reservation-change-settlement-approval.md docs/overview/current-development-context.md
git commit -m "docs(reservations): record change settlement verification"
```

체크포인트 B 완료 기준: fake 추가 결제·부분 환불·차액 없음 경로가 한 번의 최종 예약 변경으로 수렴하고, 실패 시 기존 예약을 유지하며, 불명확한 결과가 본사 조정 대상으로 남고, 직원·고객 UI 검증이 통과하며, 실제 PG는 비활성 상태다.

## 실제 PG 별도 계획 진입 조건

이 계획에서는 실제 PG SDK나 인증 정보를 추가하지 않는다. provider 작업 전에 provider 이름, hosted checkout·부분 환불 API 버전, webhook 서명 계약, merchant account 매핑, sandbox 인증 정보 전달 방식, 환불 수수료·회계 정책과 운영 rollout 승인을 확보한다. 그 다음 상태 머신을 바꾸지 않고 `PaymentAdjustmentGateway`와 `payment_transaction`을 재사용하는 provider별 설계 보완 문서와 별도 구현 계획을 작성한다.
