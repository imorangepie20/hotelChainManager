# Toss Payments Test Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 토스페이먼츠 테스트 결제창으로 신규 예약과 예약 변경의 추가 결제·부분 환불을 검증하면서 서버의 가격·재고·예약 확정 권한과 기존 fake 회귀를 보존한다.

**Architecture:** Spring Boot는 서버 주문·금액을 저장하고 외부 승인·취소를 고정 멱등 키로 수행한다. 외부 호출은 DB 잠금 트랜잭션 밖에서 claim하고, 검증된 provider 결과만 짧은 DB 트랜잭션에서 거래·예약·재고에 반영한다. 고객 Vite 앱은 토스 결제창을 열고 same-origin 결과 API만 조회한다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, JDBC, PostgreSQL/Flyway, JDK HttpClient, React 19, Vite, TypeScript, Toss Payments JavaScript SDK v2, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-14-toss-test-payment-design.md`

## Global Constraints

- 실제 과금·라이브 키·운영 계약·공개 터널은 범위 밖이며 `test_` 키만 허용한다.
- 고객 4000, 관리자 4001, API 4080을 유지한다. 기존 4001 프로세스를 중단·교체하거나 다른 관리자 포트를 만들지 않는다.
- 시크릿·카드 정보·관리 token·원문 결제 link token을 Git, 브라우저 응답, 로그에 저장하지 않는다.
- callback과 웹훅은 결제 성공의 증거가 아니다. 서버가 provider 거래·주문·금액·merchant를 재검증한다.
- V35 checksum과 `fake` gateway를 보존하며 migration은 additive로만 추가한다.
- timeout/UNKNOWN은 hold를 자동 해제하거나 새 주문과 새 멱등 키로 재청구하지 않는다.

## File Structure

- `services/api/src/main/java/team/hotelchain/payment/TossPaymentsProperties.java`: 테스트 키·merchant·고객 origin의 fail-closed 검증.
- `services/api/src/main/java/team/hotelchain/payment/TossPaymentsClient.java`, `TossPaymentsHttpClient.java`: 승인·조회·취소의 좁은 provider 계약과 Basic 인증 HTTP 구현.
- `services/api/src/main/java/team/hotelchain/payment/TossReservationPaymentService.java`, `TossReservationPaymentController.java`: 신규 예약 checkout·confirm·상태.
- `services/api/src/main/java/team/hotelchain/reservationchange/TossPaymentAdjustmentGateway.java`: 기존 변경 정산의 provider adapter.
- `services/api/src/main/java/team/hotelchain/reservationchange/TossPaymentWebhookController.java`: 재조회 힌트만 처리하는 공개 endpoint.
- `services/api/src/main/resources/db/migration/V38__toss_test_payment_attempts.sql`: provider order, claim, result dedupe를 저장.
- `services/api/src/test/java/team/hotelchain/payment/TossPaymentsHttpClientTest.java`, `TossReservationPaymentIntegrationTest.java`, `services/api/src/test/java/team/hotelchain/reservationchange/TossPaymentAdjustmentIntegrationTest.java`: provider contract와 PostgreSQL 상태 회귀.
- `apps/web/src/lib/toss-payments.ts`: SDK 1회 로드·결제창 호출·callback parsing.
- `apps/web/src/components/reservation-payment-page.tsx`, `reservation-change-payment-page.tsx`, `apps/web/src/lib/api.ts`, `apps/web/src/App.tsx`: server-issued checkout과 결과 UI.
- `apps/web/src/lib/toss-payments.test.ts`, `apps/web/test/toss-test-payment.spec.ts`: amount authority·query 제거·390px·키보드 회귀.
- `.env.example`, `compose.yaml`, `services/api/src/main/resources/application.yml`: 값 없이 설정 이름과 provider mode.
- `docs/changes/2026-09-14-toss-test-payment.md`, `docs/overview/current-development-context.md`: 한국어 검증·한계 기록.

---

### Task 1: Provider configuration, schema, and HTTP adapter

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/payment/TossPaymentsProperties.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/TossPaymentsClient.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/TossPaymentsHttpClient.java`
- Create: `services/api/src/main/resources/db/migration/V38__toss_test_payment_attempts.sql`
- Create: `services/api/src/test/java/team/hotelchain/payment/TossPaymentsHttpClientTest.java`
- Modify: `services/api/src/main/resources/application.yml`, `.env.example`, `compose.yaml`

**Interfaces:**
- Produces `confirm(ConfirmCommand)`, `lookup(paymentKey)`, `cancel(CancelCommand)` returning payment key, order ID, KRW amount, provider status, transaction key, and safe error code.
- Produces `requireTestConfiguration()`, rejecting absent, mixed, or non-`test_` keys.
- Produces `payment_provider_attempt` states `NEW|APPROVING|SUCCEEDED|FAILED|UNKNOWN`, unique provider order and idempotency key.

- [ ] **Step 1: Write the failing contract tests**

```java
@Test void rejectsLiveOrMissingKeys() {
  assertThatThrownBy(() -> properties("live_ck_x", "test_sk_x").requireTestConfiguration())
      .hasMessageContaining("test_");
}
@Test void confirmSendsStableKeyAndServerOrder() {
  ProviderPayment result = client.confirm(new ConfirmCommand("pay", "order", 120000, "KRW", "idem-1"));
  assertThat(result.status()).isEqualTo(DONE);
  assertThat(captured.header("Idempotency-Key")).isEqualTo("idem-1");
  assertThat(captured.body()).contains("\"orderId\":\"order\"").contains("\"amount\":120000");
}
```

- [ ] **Step 2: Run the contract test**

Run: `./mvnw.cmd -Dtest=TossPaymentsHttpClientTest test`

Expected: FAIL because no Toss configuration/client types exist.

- [ ] **Step 3: Add minimal migration and adapter**

```sql
CREATE TABLE payment_provider_attempt (
  id UUID PRIMARY KEY, reservation_id UUID NOT NULL REFERENCES reservation(id),
  provider VARCHAR(30) NOT NULL, merchant_account VARCHAR(80) NOT NULL,
  order_id VARCHAR(64) NOT NULL, payment_key VARCHAR(160),
  idempotency_key VARCHAR(100) NOT NULL, amount_krw BIGINT NOT NULL CHECK (amount_krw > 0),
  currency CHAR(3) NOT NULL CHECK (currency = 'KRW'),
  status VARCHAR(20) NOT NULL CHECK (status IN ('NEW','APPROVING','SUCCEEDED','FAILED','UNKNOWN')),
  provider_event_id VARCHAR(160), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(provider, order_id), UNIQUE(provider, idempotency_key)
);
```

Use only the fixed Toss API origin, Basic authentication from in-memory `secret + ":"`, and a fixed provider idempotency key. Map verified `DONE` to success, stable provider rejections to failed, and timeout/5xx to unknown. Persist only a redacted code and HTTP class.

- [ ] **Step 4: Run direct regression**

Run: `./mvnw.cmd -Dtest=TossPaymentsHttpClientTest,ReservationChangeSettlementIntegrationTest test`

Expected: PASS; V38 applies and fake transactions still use `FAKE`.

- [ ] **Step 5: Commit**

```powershell
git add services/api/src/main/java/team/hotelchain/payment services/api/src/main/resources/application.yml services/api/src/main/resources/db/migration/V38__toss_test_payment_attempts.sql services/api/src/test/java/team/hotelchain/payment/TossPaymentsHttpClientTest.java .env.example compose.yaml
git commit -m "feat(payment): add Toss test provider foundation"
```

### Task 2: New reservation checkout, confirmation, and expiry safety

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/payment/TossReservationPaymentService.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/TossReservationPaymentController.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/TossReservationPaymentView.java`
- Create: `services/api/src/test/java/team/hotelchain/payment/TossReservationPaymentIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/ReservationExpiryService.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/TestPaymentService.java`

**Interfaces:**
- Consumes Task 1 client and provider attempt.
- Produces `POST /api/reservations/{id}/payment-checkout`, `POST /api/reservations/{id}/payment-confirm`, `GET /api/reservations/{id}/payment-status` with existing reservation token.
- Produces `CheckoutView(orderId, amountKrw, currency, clientKey, successUrl, failUrl, environmentLabel)`.

- [ ] **Step 1: Write failing authoritative-state tests**

```java
@Test void rejectsCallbackAmountWithoutInventoryChange() {
  CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "checkout-once");
  assertThatThrownBy(() -> payments.confirm(reservation.id(), TOKEN,
      new ConfirmPaymentRequest(checkout.orderId(), "payment-key", 1L))).hasMessageContaining("결제 금액");
  assertInventory(2, 0);
}
@Test void duplicateConfirmCapturesOnce() {
  CheckoutView checkout = payments.checkout(reservation.id(), TOKEN, "checkout-once");
  assertThat(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)))
      .isEqualTo(payments.confirm(reservation.id(), TOKEN, confirmation(checkout)));
  assertThat(count("payment_transaction")).isEqualTo(1);
}
```

- [ ] **Step 2: Run to prove the test fails**

Run: `./mvnw.cmd -Dtest=TossReservationPaymentIntegrationTest test`

Expected: FAIL because checkout and confirmation services do not exist.

- [ ] **Step 3: Implement claim-before-call confirmation**

Lock and validate only `PENDING_PAYMENT`, valid hold, stored KRW total, and customer token while creating/replaying one order. Claim `NEW|UNKNOWN` to `APPROVING`, call the provider outside the transaction, then lock again. Only provider `DONE` with matching payment key/order/amount/KRW/merchant may atomically change held→confirmed, reservation→`CONFIRMED`, and insert one `TOSS_TEST` original transaction. Explicit failure keeps the normal retryable hold. Unknown preserves hold and becomes reconciliation-required.

Make expiry lock first and refuse automatic release during `APPROVING|UNKNOWN`. Keep fake testing active only in fake mode; never create fake transactions in `toss-test` mode.

- [ ] **Step 4: Run direct payment regressions**

Run: `./mvnw.cmd -Dtest=TossReservationPaymentIntegrationTest,PaymentExpiryIntegrationTest,ReservationChangeSettlementIntegrationTest test`

Expected: PASS for tampered amount, duplicate confirmation, expiry race, explicit failure, unknown, and fake isolation.

- [ ] **Step 5: Commit**

```powershell
git add services/api/src/main/java/team/hotelchain/payment services/api/src/test/java/team/hotelchain/payment
git commit -m "feat(payment): confirm Toss test reservation payments"
```

### Task 3: Reservation-change charges, refunds, and reconciliation

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/TossPaymentAdjustmentGateway.java`
- Create: `services/api/src/main/java/team/hotelchain/reservationchange/TossPaymentWebhookController.java`
- Create: `services/api/src/test/java/team/hotelchain/reservationchange/TossPaymentAdjustmentIntegrationTest.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/PaymentAdjustmentGateway.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeOutboxWorker.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/CancellationService.java`

**Interfaces:**
- Consumes Task 1 client and Task 2 provider attempt claim.
- Produces Toss mode adapter with provider `TOSS_TEST`; public webhook only enqueues known-order lookup.

- [ ] **Step 1: Write failing adjustment tests**

```java
@Test void mismatchedLookupRequiresReconciliation() {
  UUID id = createChangeChargeAttempt();
  adapter.returnLookup(doneForDifferentAmount());
  worker.processNext();
  assertThat(requestStatus()).isEqualTo("RECONCILIATION_REQUIRED");
  assertThat(changeTransactions()).isZero();
}
@Test void partialRefundUsesStoredTossKeyOnce() {
  createRefundableTossReservation();
  worker.processNext();
  worker.processNext();
  assertThat(refundCalls()).containsExactly("original-payment-key:100000");
  assertThat(originalRefundedAmount()).isEqualTo(100000L);
}
```

- [ ] **Step 2: Run to prove the test fails**

Run: `./mvnw.cmd -Dtest=TossPaymentAdjustmentIntegrationTest test`

Expected: FAIL because the Toss adjustment adapter does not exist.

- [ ] **Step 3: Implement provider-aware settlement**

Keep fake behavior unchanged. For Toss change charge, store a server order and use Task 2’s confirmation semantics; checkout alone is never success. For a refund, require a same-merchant `TOSS_TEST` original payment key, send exact amount with the persisted attempt key, and increment refunded total only for a new verified cancel transaction key. Reject provider mismatch before a hold is acquired. Timeout/5xx stays unknown with hold; explicit rejection releases the new hold; successful lookup alone can move to ready-to-apply.

The webhook accepts bounded JSON only for known orders then schedules lookup. It cannot mutate a result from body status or rely on a generic signature header. Full cancellation must create a fixed refund plan for each remaining Toss transaction; it cannot mark cancelled or return stock until all planned refunds are verified.

- [ ] **Step 4: Run settlement and cancellation regressions**

Run: `./mvnw.cmd -Dtest=TossPaymentAdjustmentIntegrationTest,ReservationChangeSettlementIntegrationTest,ReservationChangeApplyIntegrationTest,CancellationIntegrationTest test`

Expected: PASS for duplicate event, provider mismatch, unknown hold preservation, exact partial refund, fake compatibility, and cancellation accounting.

- [ ] **Step 5: Commit**

```powershell
git add services/api/src/main/java/team/hotelchain/reservationchange services/api/src/main/java/team/hotelchain/reservation/CancellationService.java services/api/src/test/java/team/hotelchain/reservationchange
git commit -m "feat(payment): connect Toss test settlement gateway"
```

### Task 4: Customer SDK checkout and result UI

**Files:**
- Create: `apps/web/src/lib/toss-payments.ts`
- Create: `apps/web/src/lib/toss-payments.test.ts`
- Create: `apps/web/test/toss-test-payment.spec.ts`
- Modify: `apps/web/src/lib/api.ts`, `apps/web/src/App.tsx`
- Modify: `apps/web/src/components/reservation-change-payment-page.tsx`
- Modify: `apps/web/src/lib/customer-route.ts`, `apps/web/src/lib/customer-route.test.ts`, `apps/web/src/styles.css`

**Interfaces:**
- Consumes Task 2 checkout/status views and existing change-payment session API.
- Produces `requestTossCheckout(checkout)` that passes only server-issued order, amount, client key, success URL, and fail URL to the SDK.

- [ ] **Step 1: Write failing pure client tests**

```ts
expectEqual(
  toConfirmationInput(new URLSearchParams('paymentKey=pk&orderId=o&amount=120000')),
  { paymentKey: 'pk', orderId: 'o', amountKrw: 120000 },
  '복귀 값은 서버 확인용으로만 정규화한다',
)
expectThrows(() => toConfirmationInput(new URLSearchParams('paymentKey=pk&orderId=o&amount=0')),
  '0원 결과를 거부한다')
```

- [ ] **Step 2: Run to prove the tests fail**

Run: `pnpm exec tsx src/lib/toss-payments.test.ts; pnpm exec tsx src/lib/customer-route.test.ts`

Expected: FAIL because the Toss helper module does not exist.

- [ ] **Step 3: Implement SDK loading and result status**

Load `https://js.tosspayments.com/v2/standard` only once through a typed promise. Reject missing SDK, duplicate loading, non-test client key, and nonpositive server amount. After provider return, copy callback values, immediately replace history with clean same-origin route, then call server confirmation/status. Render only server state: unknown/reconciliation is not complete. Keep fragment token removal before provider calls, focus error alerts, minimum 44px action size, and no 390px overflow.

- [ ] **Step 4: Run direct client checks**

Run: `pnpm exec tsc -b; pnpm run build; pnpm exec playwright test test/toss-test-payment.spec.ts`

Expected: PASS; mocked SDK receives exactly server values, provider query values are removed, card data is never posted to API, and keyboard/mobile behavior works.

- [ ] **Step 5: Commit**

```powershell
git add apps/web/src apps/web/test apps/web/package.json apps/web/pnpm-lock.yaml
git commit -m "feat(web): add Toss test payment checkout flow"
```

### Task 5: Sandbox proof, review, document, merge, and cleanup

**Files:**
- Create: `docs/changes/2026-09-14-toss-test-payment.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `.env.example`

**Interfaces:**
- Consumes Tasks 1–4 and ignored test-only configuration.
- Produces a Korean record containing only masked order suffix, amount, provider/internal status, transaction type, refund delta, and time.

- [ ] **Step 1: Run all directly changed checks**

Run: `./mvnw.cmd -Dtest=TossPaymentsHttpClientTest,TossReservationPaymentIntegrationTest,TossPaymentAdjustmentIntegrationTest,PaymentExpiryIntegrationTest,ReservationChangeSettlementIntegrationTest,ReservationChangeApplyIntegrationTest,CancellationIntegrationTest test`

Run: `pnpm exec tsc -b; pnpm run build; pnpm exec playwright test test/toss-test-payment.spec.ts`

Expected: every named direct/adjacent test passes; record unrelated whole-suite failures separately.

- [ ] **Step 2: Run controlled real sandbox proof**

With existing 4000 and 4080 only, complete test card approval, confirm internal transaction/booking, execute a small change charge and partial refund, then cancel remaining eligible amount. Stop and ask only for a browser click if provider UI requires it; never ask for secret or card data in chat. Do not claim webhook delivery tested without a user-approved public HTTPS callback.

- [ ] **Step 3: Review, document, and commit**

Run `git diff --check`, request focused code review, apply supported findings, and rerun affected tests. Write Korean verification, migration, rollback, sandbox proof, and unverified external webhook details.

```powershell
git add docs/changes/2026-09-14-toss-test-payment.md docs/overview/current-development-context.md .env.example
git commit -m "docs(payment): record Toss test verification"
```

- [ ] **Step 4: Merge and remove only this feature worktree**

```powershell
git -C C:\Users\jowoo\hotelChainManager merge --no-ff codex/toss-test-payment -m "merge: Toss test payment gateway"
```

Verify named direct checks on merged main plus unchanged 4001/4080 HTTP 200. Confirm no feature commit remains unmerged, then remove only `C:\Users\jowoo\hotelChainManager\.worktrees\toss-test-payment` and delete only `codex/toss-test-payment`; preserve all other worktrees and user files.

## Plan Self-Review

- Spec coverage: Tasks 1–3 cover test-key gating, transaction claims, provider result verification, expiry safety, charge/refund, webhook-as-hint, reconciliation, and cancellation. Task 4 covers SDK security and accessibility. Task 5 covers real sandbox evidence, documentation, review, merge, and cleanup.
- Placeholder scan: every task declares files, interfaces, failure-first test, command, implementation behavior, and commit.
- Type consistency: Task 1 creates the client and provider attempt; Tasks 2–3 consume it; Task 2 creates checkout/status views used by Task 4; Task 5 consumes all earlier contracts.
