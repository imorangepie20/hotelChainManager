# Customer Self-Service Reservation Change Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 확정 예약 고객이 예약 상세에서 날짜·객실·요금제·성인·아동을 직접 재견적하고 차액 정산 후 예약을 변경할 수 있게 한다.

**Architecture:** 고객 관리 token으로만 접근하는 quote/request/cancel API를 추가하고 기존 변경 hold·gateway·outbox·apply 상태 머신을 재사용한다. 고객 요청은 직원 ID를 위조하지 않고 `CUSTOMER` 출처로 감사하며, 추가 결제는 같은 브라우저 cookie로 이어지고 0원·환불은 서버가 즉시 시작한다.

**Tech Stack:** Java 21, Spring Boot, JDBC, Flyway, PostgreSQL, React 19, TypeScript, Vite, 기존 Toss test gateway.

**Spec:** `docs/superpowers/specs/2026-09-15-customer-self-service-reservation-change-design.md`

## Global Constraints

- 가격·재고·예약 확정 권한은 Spring Boot에 둔다.
- 기존 예약은 정산과 적용 성공 전까지 `CONFIRMED` 상태와 기존 재고를 유지한다.
- 직원 변경의 100,000원 승인 정책은 그대로 유지한다.
- 고객 직접 변경은 직원 승인을 거치지 않는다.
- 한 예약에는 활성 변경 요청 하나만 허용한다.
- 객실 수는 변경하지 않는다.
- 브라우저 검증은 사용자가 수행한다.
- `.tmp/`와 사용자 변경은 건드리지 않는다.

---

### Task 1: 고객 요청 출처와 인원 감사 스키마

**Files:**
- Create: `services/api/src/main/resources/db/migration/V42__customer_self_service_reservation_change.sql`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyService.java`
- Test: `services/api/src/test/java/team/hotelchain/reservationchange/CustomerSelfServiceReservationChangeIntegrationTest.java`

**Interfaces:**
- Produces: `reservation_change_request.request_origin`, nullable `requested_by`, 고객 견적 table, 인원 변경 감사 columns.

- [ ] **Step 1: Write the failing migration/integration test**

```java
@Test
void customerChangeSchemaSupportsCustomerActorAndPartyAudit() {
    UUID quoteId = insertCustomerQuote(reservationId, 0, 2, 1);
    UUID requestId = insertCustomerRequest(reservationId, quoteId, "CUSTOMER", null, 2, 1);
    assertThat(jdbc.queryForObject("select request_origin from reservation_change_request where id=?", String.class, requestId))
            .isEqualTo("CUSTOMER");
}
```

- [ ] **Step 2: Run the focused test and verify it fails on missing columns**

Run: `cd services/api; ./mvnw -Dtest=CustomerSelfServiceReservationChangeIntegrationTest#customerChangeSchemaSupportsCustomerActorAndPartyAudit test`

Expected: FAIL because V42 columns/table do not exist.

- [ ] **Step 3: Add additive V42 migration**

```sql
ALTER TABLE reservation_change_request
  ADD COLUMN request_origin VARCHAR(20) NOT NULL DEFAULT 'STAFF'
    CHECK (request_origin IN ('STAFF', 'CUSTOMER')),
  ALTER COLUMN requested_by DROP NOT NULL;
ALTER TABLE reservation_change_request
  ADD CONSTRAINT reservation_change_request_actor_check CHECK (
    (request_origin = 'STAFF' AND requested_by IS NOT NULL) OR
    (request_origin = 'CUSTOMER' AND requested_by IS NULL));

CREATE TABLE reservation_change_customer_quote (
  id UUID PRIMARY KEY,
  reservation_id UUID NOT NULL REFERENCES reservation(id),
  base_operation_revision BIGINT NOT NULL,
  check_in DATE NOT NULL,
  check_out DATE NOT NULL,
  adults INTEGER NOT NULL CHECK (adults > 0),
  children INTEGER NOT NULL CHECK (children >= 0),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (check_out > check_in)
);
CREATE INDEX reservation_change_customer_quote_expiry_idx
  ON reservation_change_customer_quote (expires_at);

ALTER TABLE reservation_stay_change
  ALTER COLUMN staff_id DROP NOT NULL,
  ADD COLUMN actor_origin VARCHAR(20) NOT NULL DEFAULT 'STAFF'
    CHECK (actor_origin IN ('STAFF', 'CUSTOMER')),
  ADD COLUMN previous_adults INTEGER,
  ADD COLUMN previous_children INTEGER,
  ADD COLUMN adults INTEGER,
  ADD COLUMN children INTEGER;
```

- [ ] **Step 4: Record customer actor and previous/target party during apply**

Extend the locked reservation query with `adults, children`. Insert `actor_origin`, nullable `staff_id`, previous party and request target party into `reservation_stay_change`. Preserve existing staff rows through defaults.

- [ ] **Step 5: Run migration and apply regression tests**

Run: `cd services/api; ./mvnw -Dtest=CustomerSelfServiceReservationChangeIntegrationTest,ReservationChangeApplyIntegrationTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/api/src/main/resources/db/migration/V42__customer_self_service_reservation_change.sql services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeApplyService.java services/api/src/test/java/team/hotelchain/reservationchange/CustomerSelfServiceReservationChangeIntegrationTest.java
git commit -m "feat(reservations): audit customer change actors"
```

### Task 2: 고객 인원을 반영하는 서버 견적

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeQuoteRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeQuoteView.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationStayQuoteService.java`
- Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationStayQuoteIntegrationTest.java`

**Interfaces:**
- Produces: `quote(reservationId, checkIn, checkOut, adults, children, lockReservation)` and customer quote DTOs.

- [ ] **Step 1: Add failing target-party quote tests**

```java
@Test
void quoteFiltersOffersByRequestedPartyAndKeepsOwnInventory() {
    ReservationStayQuote quote = service.quote(reservationId, targetIn, targetOut, 3, 1, false);
    assertThat(quote.adults()).isEqualTo(3);
    assertThat(quote.children()).isEqualTo(1);
    assertThat(quote.offers()).allMatch(offer -> offer.maxOccupancy() * quote.rooms() >= 4);
}
```

- [ ] **Step 2: Run and confirm signature failure**

Run: `cd services/api; ./mvnw -Dtest=ReservationStayQuoteIntegrationTest#quoteFiltersOffersByRequestedPartyAndKeepsOwnInventory test`

Expected: FAIL because the target-party overload does not exist.

- [ ] **Step 3: Add the overload and validation**

```java
public ReservationStayQuote quote(UUID reservationId, LocalDate checkIn, LocalDate checkOut,
        int adults, int children, boolean lockReservation) {
    if (adults < 1 || children < 0) throw new IllegalArgumentException("투숙 인원을 확인해 주세요.");
    ReservationSnapshot reservation = loadReservation(reservationId, lockReservation);
    return buildQuote(reservation, checkIn, checkOut, adults, children);
}

public ReservationStayQuote quote(UUID reservationId, LocalDate checkIn, LocalDate checkOut,
        boolean lockReservation) {
    ReservationSnapshot reservation = loadReservation(reservationId, lockReservation);
    return buildQuote(reservation, checkIn, checkOut, reservation.adults(), reservation.children());
}
```

Use target adults/children in capacity filtering and in the returned snapshot. Keep the existing overload for staff callers.

- [ ] **Step 4: Run quote and staff request regressions**

Run: `cd services/api; ./mvnw -Dtest=ReservationStayQuoteIntegrationTest,ReservationChangeApprovalIntegrationTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeQuoteRequest.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeQuoteView.java services/api/src/main/java/team/hotelchain/reservationchange/ReservationStayQuoteService.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationStayQuoteIntegrationTest.java
git commit -m "feat(reservations): quote customer party changes"
```

### Task 3: 고객 quote와 변경 요청 API

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeController.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeService.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeStartView.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeSummaryController.java`
- Test: `services/api/src/test/java/team/hotelchain/reservationchange/CustomerSelfServiceReservationChangeIntegrationTest.java`

**Interfaces:**
- Produces:
  - `POST /api/reservations/{id}/change-quotes`
  - `POST /api/reservations/{id}/change-requests`
  - `POST /api/reservations/{id}/change-requests/{requestId}/cancel`
  - `GET /api/reservations/{id}/change-eligibility`

- [ ] **Step 1: Add failing authorization, quote, idempotency and active-request tests**

```java
@Test void wrongManagementTokenCannotQuoteOrCreateCustomerChange() {
    assertThatThrownBy(() -> service.quote("wrong-token", reservationId, quoteInput))
            .isInstanceOf(ReservationNotFoundException.class);
}
@Test void customerQuoteReturnsOwnInventoryAndExpiresAt() {
    var result = service.quote(managementToken, reservationId, quoteInput);
    assertThat(result.quoteId()).isNotNull();
    assertThat(result.offers()).isNotEmpty();
    assertThat(result.expiresAt()).isAfter(clock.instant());
}
@Test void createRequotesUnderLockAndRejectsChangedPrice() {
    var quote = service.quote(managementToken, reservationId, quoteInput);
    fixtures.raiseRate(quote.offers().getFirst().ratePlanId(), 10_000);
    assertThatThrownBy(() -> service.create(managementToken, reservationId, "change-1",
            fixtures.startInput(quote)))
            .isInstanceOfSatisfying(BusinessConflictException.class,
                    error -> assertThat(error.code()).isEqualTo("PRICE_CHANGED"));
}
@Test void duplicateIdempotencyKeyReturnsSameRequest() {
    var quote = service.quote(managementToken, reservationId, quoteInput);
    var first = service.create(managementToken, reservationId, "change-1", fixtures.startInput(quote));
    var replay = service.create(managementToken, reservationId, "change-1", fixtures.startInput(quote));
    assertThat(replay.requestId()).isEqualTo(first.requestId());
}
@Test void secondActiveCustomerChangeIsRejected() {
    var firstQuote = service.quote(managementToken, reservationId, quoteInput);
    service.create(managementToken, reservationId, "change-1", fixtures.startInput(firstQuote));
    var secondQuote = service.quote(managementToken, reservationId, quoteInput);
    assertThatThrownBy(() -> service.create(managementToken, reservationId, "change-2",
            fixtures.startInput(secondQuote)))
            .isInstanceOfSatisfying(BusinessConflictException.class,
                    error -> assertThat(error.code()).isEqualTo("RESERVATION_CHANGE_ACTIVE"));
}
@Test void eligibilityExplainsAssignedReservation() {
    fixtures.assignRoom(reservationId);
    var result = service.eligibility(managementToken, reservationId);
    assertThat(result.allowed()).isFalse();
    assertThat(result.reasonCode()).isEqualTo("ROOM_ALREADY_ASSIGNED");
}
```

Add the named fixture helpers to the same test class; they insert one confirmed reservation with an original payment transaction and update only the requested rate rows.

- [ ] **Step 2: Run and verify endpoint failures**

Run: `cd services/api; ./mvnw -Dtest=CustomerSelfServiceReservationChangeIntegrationTest test`

Expected: FAIL with missing customer controller/service.

- [ ] **Step 3: Implement customer quote creation**

The service must call `reservationAccess.require(id, token)`, reject non-`CONFIRMED` reservations, enforce local-date cutoff, call the target-party quote overload, persist a UUID quote with the reservation operation revision and a 15-minute expiry, and return only server offer values. `eligibility` returns `allowed` and a stable `reasonCode`; it never trusts the client clock.

- [ ] **Step 4: Implement locked request creation**

```java
@Transactional
public CustomerReservationChangeStartView create(String token, UUID reservationId,
        String idempotencyKey, CustomerReservationChangeRequest input) {
    reservationAccess.require(reservationId, token);
    CustomerQuote saved = lockUnexpiredQuote(input.quoteId(), reservationId);
    ReservationStayQuote latest = quotes.quote(reservationId, saved.checkIn(), saved.checkOut(),
            saved.adults(), saved.children(), true);
    SelectedStayOffer selected = latest.selected(input.roomTypeId(), input.ratePlanId());
    requireRevisionAndExpectedTotal(saved, latest, selected, input.expectedTotal());
    return createCustomerRequestAndStartSettlement(latest, selected, idempotencyKey);
}
```

Insert `request_origin='CUSTOMER'`, `requested_by=NULL`, status `APPROVED`, target party, quote nights and `CUSTOMER_REQUEST_CREATED` event. Do not insert a staff approval row.

- [ ] **Step 5: Implement customer cancellation before settlement starts**

Lock the reservation and request, require matching reservation ID and management token, accept only `APPROVED` or an unstarted `AWAITING_PAYMENT`, release new holds and mark `CANCELLED` idempotently.

- [ ] **Step 6: Run API tests**

Run: `cd services/api; ./mvnw -Dtest=CustomerSelfServiceReservationChangeIntegrationTest,CustomerReservationChangeControllerTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeController.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeService.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeRequest.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeStartView.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeSummaryController.java services/api/src/test/java/team/hotelchain/reservationchange/CustomerSelfServiceReservationChangeIntegrationTest.java
git commit -m "feat(reservations): start customer self-service changes"
```

### Task 4: 차액 방향별 자동 정산 시작

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeController.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeStartView.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeStartResult.java`
- Test: `services/api/src/test/java/team/hotelchain/reservationchange/CustomerSelfServiceReservationChangeIntegrationTest.java`
- Test: `services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java`

**Interfaces:**
- Produces: `startCustomerSettlement(reservationId, requestId, managementToken, idempotencyKey)` returning internal `CustomerReservationChangeStartResult(status, customerSessionToken)`; the controller maps it to JSON without the token.

- [ ] **Step 1: Add failing CHARGE/NONE/REFUND tests**

```java
@Test void positiveDifferenceCreatesHoldCheckoutAndCustomerSession() {
    var result = fixtures.startChangeWithDifference(100_000);
    assertThat(result.status()).isEqualTo("AWAITING_PAYMENT");
    assertThat(result.customerSessionToken()).isNotBlank();
}
@Test void zeroDifferenceMovesDirectlyToReadyToApply() {
    assertThat(fixtures.startChangeWithDifference(0).status()).isEqualTo("READY_TO_APPLY");
}
@Test void negativeDifferenceStartsOriginalPaymentRefund() {
    assertThat(fixtures.startChangeWithDifference(-50_000).status()).isEqualTo("REFUND_PENDING");
}
@Test void unknownSettlementPreservesOriginalReservationAndHold() {
    UUID requestId = fixtures.startUnknownChange();
    assertThat(fixtures.reservationDates(reservationId)).containsExactly(originalCheckIn, originalCheckOut);
    assertThat(fixtures.requestStatus(requestId)).isEqualTo("AWAITING_PAYMENT");
    assertThat(fixtures.heldDayCount(requestId)).isPositive();
}
```

Add these fixture methods to `CustomerSelfServiceReservationChangeIntegrationTest`; each creates its own reservation and deterministic price delta.

- [ ] **Step 2: Run and verify orchestration is absent**

Run: `cd services/api; ./mvnw -Dtest=CustomerSelfServiceReservationChangeIntegrationTest test`

Expected: FAIL because customer settlement is not started.

- [ ] **Step 3: Extract actor-neutral settlement primitives**

Keep current staff public methods. Extract private methods that receive `Actor(origin, staffId)` and share provider compatibility, hold acquisition, attempt/outbox creation, state transition and event insertion. Customer events must use null `actor_staff_id` and payload `{"actor":"CUSTOMER"}`.

- [ ] **Step 4: Create same-browser payment session without public link transfer**

For `CHARGE`, generate the payment attempt and `reservation_change_customer_session` in one transaction. Return the raw session token once; the controller sets the existing HttpOnly, SameSite=Lax cookie scoped to `/api/reservation-change-payments`. Never return or log the token in JSON.

- [ ] **Step 5: Route NONE and REFUND**

For `NONE`, acquire hold and transition to `READY_TO_APPLY`. For `REFUND`, reuse the verified original transaction and outbox refund path. All three directions use the request version and idempotency key.

- [ ] **Step 6: Run settlement regressions**

Run: `cd services/api; ./mvnw -Dtest=CustomerSelfServiceReservationChangeIntegrationTest,ReservationChangeSettlementIntegrationTest,TossPaymentAdjustmentIntegrationTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeController.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeStartView.java services/api/src/main/java/team/hotelchain/reservationchange/CustomerReservationChangeStartResult.java services/api/src/test/java/team/hotelchain/reservationchange/CustomerSelfServiceReservationChangeIntegrationTest.java services/api/src/test/java/team/hotelchain/reservationchange/ReservationChangeSettlementIntegrationTest.java
git commit -m "feat(reservations): settle customer changes immediately"
```

### Task 5: 고객 웹 API 계약과 경로

**Files:**
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/lib/customer-route.ts`
- Modify: `apps/web/src/lib/customer-route.test.ts`
- Create: `apps/web/src/lib/reservation-change-state.ts`
- Create: `apps/web/src/lib/reservation-change-state.test.ts`

**Interfaces:**
- Produces: `reservation-change` route and typed `changeEligibility`, `changeQuote`, `startChange`, `cancelChange` client methods.

- [ ] **Step 1: Add failing route and state contract tests**

```ts
deepEqual(resolveCustomerRoute('/reservations/12345678-1234-1234-1234-123456789abc/change'), {
  kind: 'reservation-change',
  pathname: '/reservations/12345678-1234-1234-1234-123456789abc/change',
  reservationId: '12345678-1234-1234-1234-123456789abc',
})
equal(changeAction('AWAITING_PAYMENT'), 'PAY')
equal(changeAction('REFUND_PENDING'), 'POLL')
equal(changeAction('COMPLETED'), 'RETURN')
```

- [ ] **Step 2: Run and verify failures**

Run: `cd apps/web; pnpm exec tsx src/lib/customer-route.test.ts; pnpm exec tsx src/lib/reservation-change-state.test.ts`

Expected: FAIL for missing route and state helper.

- [ ] **Step 3: Add route before generic reservation-detail matching**

Add `{ kind: 'reservation-change'; pathname: string; reservationId: string }` and match `/reservations/:uuid/change` before `/reservations/:id`.

- [ ] **Step 4: Add exact API types and calls**

```ts
export type CustomerChangeQuote = {
  quoteId: string; expiresAt: string; baseOperationRevision: number
  previousTotal: number; offers: Offer[]
}
changeEligibility: (id: string, token: string) =>
  request<CustomerChangeEligibility>(`/api/reservations/${encodeURIComponent(id)}/change-eligibility`, {
    headers: headers(token), cache: 'no-store',
  }),
changeQuote: (id: string, token: string, input: CustomerChangeQuoteInput) =>
  request<CustomerChangeQuote>(`/api/reservations/${encodeURIComponent(id)}/change-quotes`, {
    method: 'POST', headers: headers(token), body: JSON.stringify(input),
  }),
startChange: (id: string, token: string, key: string, input: CustomerChangeStartInput) =>
  request<CustomerChangeStart>(`/api/reservations/${encodeURIComponent(id)}/change-requests`, {
    method: 'POST', headers: headers(token, key), body: JSON.stringify(input), credentials: 'include',
  }),
cancelChange: (id: string, requestId: string, token: string, key: string) =>
  request<CustomerChangeStart>(`/api/reservations/${encodeURIComponent(id)}/change-requests/${encodeURIComponent(requestId)}/cancel`, {
    method: 'POST', headers: headers(token, key),
  }),
```

- [ ] **Step 5: Run contracts and TypeScript**

Run: `cd apps/web; pnpm exec tsx src/lib/customer-route.test.ts; pnpm exec tsx src/lib/reservation-change-state.test.ts; pnpm exec tsc -b`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/web/src/lib/api.ts apps/web/src/lib/customer-route.ts apps/web/src/lib/customer-route.test.ts apps/web/src/lib/reservation-change-state.ts apps/web/src/lib/reservation-change-state.test.ts
git commit -m "feat(web): add customer change contracts"
```

### Task 6: 예약 변경 화면과 상세 진입점

**Files:**
- Create: `apps/web/src/components/reservation-change-page.tsx`
- Modify: `apps/web/src/components/reservation-management-page.tsx`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/index.css`
- Create: `apps/web/src/lib/reservation-change-copy.ts`
- Create: `apps/web/src/lib/reservation-change-copy.test.ts`

**Interfaces:**
- Consumes: Task 5 route and API methods.
- Produces: `/reservations/:id/change` customer UI.

- [ ] **Step 1: Add failing copy/UI state contracts**

```ts
equal(changeText('ko', 'changeReservation'), '예약 변경')
equal(changeText('en', 'changeReservation'), 'Change reservation')
equal(changeText('ko', 'priceChanged'), '요금이 변경되었습니다. 새 금액을 확인해 주세요.')
```

- [ ] **Step 2: Run and verify missing module failure**

Run: `cd apps/web; pnpm exec tsx src/lib/reservation-change-copy.test.ts`

Expected: FAIL because the copy module is absent.

- [ ] **Step 3: Add reservation-detail entry and status actions**

For every confirmed reservation, load `changeEligibility` and render `예약 변경`. Disable it with the server-provided reason when change summary is active, check-in cutoff has passed, or room assignment blocks apply. Keep cancellation as a separate action.

- [ ] **Step 4: Build the change page with existing controls**

Reuse `StayDatePicker` and `GuestSelector`. Load reservation and access from `BookingSessionStore`; prefill current conditions. Submit dates/party to `changeQuote`, render returned offer cards, and show previous total, new total, signed difference, refund/charge wording and expiry.

- [ ] **Step 5: Connect confirmation and recovery**

Persist the idempotency key in session storage until a response arrives. On CHARGE, open the existing Toss change checkout. On NONE/REFUND, poll `change-summary` every three seconds. On COMPLETED, reload the reservation and return to its detail. Preserve error focus and prevent duplicate submission.

- [ ] **Step 6: Add responsive and keyboard styles**

Use existing booking tokens. At widths below 720px stack comparison cards and keep the confirm action full-width. Ensure field labels, `aria-live` processing state, alert focus, Escape behavior and visible focus rings.

- [ ] **Step 7: Run web contracts, typecheck and build**

Run: `cd apps/web; pnpm exec tsx src/lib/reservation-change-copy.test.ts; pnpm exec tsx src/lib/customer-route.test.ts; pnpm exec tsx src/lib/reservation-change-state.test.ts; pnpm exec tsc -b; pnpm run build`

Expected: PASS and Vite production build exits 0.

- [ ] **Step 8: Commit**

```bash
git add apps/web/src/components/reservation-change-page.tsx apps/web/src/components/reservation-management-page.tsx apps/web/src/App.tsx apps/web/src/index.css apps/web/src/lib/reservation-change-copy.ts apps/web/src/lib/reservation-change-copy.test.ts
git commit -m "feat(web): add self-service reservation changes"
```

### Task 7: 최종 변경 범위 검증과 운영 기록

**Files:**
- Create: `docs/changes/2026-09-15-customer-self-service-reservation-change.md`
- Modify: `docs/overview/current-development-context.md`

**Interfaces:**
- Produces: 재현 가능한 비브라우저 검증 기록과 사용자 브라우저 체크리스트.

- [ ] **Step 1: Run focused backend suite**

Run: `cd services/api; ./mvnw -Dtest=CustomerSelfServiceReservationChangeIntegrationTest,ReservationStayQuoteIntegrationTest,ReservationChangeApprovalIntegrationTest,ReservationChangeHoldIntegrationTest,ReservationChangeSettlementIntegrationTest,ReservationChangeApplyIntegrationTest,TossPaymentAdjustmentIntegrationTest test`

Expected: PASS with zero failures/errors.

- [ ] **Step 2: Run focused frontend contracts and gates**

Run: `cd apps/web; pnpm exec tsx src/lib/customer-route.test.ts; pnpm exec tsx src/lib/reservation-change-state.test.ts; pnpm exec tsx src/lib/reservation-change-copy.test.ts; pnpm exec tsc -b; pnpm run build`

Expected: PASS.

- [ ] **Step 3: Verify migration and diff hygiene**

Run: `git diff --check; git status --short`

Expected: no whitespace errors; only intended files plus untouched `.tmp/`.

- [ ] **Step 4: Write Korean change record**

Record changed behavior, API/security boundary, migrations, exact commands and results. List browser checks for additional payment, zero difference, refund, refresh recovery, mobile and keyboard as user-owned unverified items.

- [ ] **Step 5: Update current development context**

Add a dated entry that links the design, plan and change record and distinguishes automated verification from browser/real Toss verification.

- [ ] **Step 6: Commit**

```bash
git add docs/changes/2026-09-15-customer-self-service-reservation-change.md docs/overview/current-development-context.md
git commit -m "docs(reservations): record customer change verification"
```

- [ ] **Step 7: Stop after verification**

Do not run Playwright or operate the browser. Report the local commits, server restart requirement, automated evidence and the exact browser flow the user should test.
