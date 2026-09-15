# Toss Live Payment Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 Toss 테스트 결제 코어를 `toss-live`로 안전하게 확장하고 checkout kill switch와 webhook 재조회 경계를 강화한다.

**Architecture:** 승인·조회·환불 코어는 공유하고 `TOSS_TEST`/`TOSS_LIVE` provider snapshot만 분리한다. 신규 checkout만 feature flag로 막으며, 진행 중 승인·환불·조회 worker는 계속 동작한다. webhook 본문은 성공 권한이 아니며 알려진 주문의 Toss API 재조회만 enqueue한다.

**Tech Stack:** Java 21, Spring Boot 4.1, JDBC, PostgreSQL/Flyway, Java `HttpClient`, JUnit 5, AssertJ

**Spec:** `docs/superpowers/specs/2026-09-15-toss-live-payment-operations-design.md`

## Global Constraints

- 현재 미커밋 예약 변경 수정을 먼저 소유자가 확정한다. 실행자는 stash, reset, checkout으로 이를 숨기거나 되돌리지 않는다.
- 가격·재고·예약 확정·환불 성공 판정은 Spring Boot만 수행한다.
- 운영 시크릿과 실제 MID를 Git, 로그, 테스트 fixture에 기록하지 않는다.
- 가상계좌·계좌이체·휴대폰·해외 결제는 추가하지 않는다.
- 기존 `FAKE`, `TOSS_TEST` 행과 API를 호환 유지하고 migration checksum을 변경하지 않는다.

---

### Task 1: Toss 환경별 설정 계약

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/payment/TossPaymentsProperties.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/TossPaymentsConfiguration.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/TossPaymentEnvironment.java`
- Modify: `services/api/src/main/resources/application.yml`
- Modify: `.env.example`
- Test: `services/api/src/test/java/team/hotelchain/payment/TossPaymentsConfigurationTest.java`

**Interfaces:**
- Produces: `TossPaymentEnvironment.from(String provider)`, `providerCode()`, `requireConfiguration(TossPaymentsProperties)`
- Produces: `payment.checkout-enabled` boolean; default `false` when provider is `toss-live`

- [ ] **Step 1: Write failing environment tests**

```java
@Test void liveRejectsTestKeysAndHttpOrigin() {
    var properties = new TossPaymentsProperties("test_gck_x", "test_gsk_x", "mid", "http://hotel.test");
    assertThatThrownBy(() -> TossPaymentEnvironment.LIVE.requireConfiguration(properties))
            .isInstanceOf(IllegalStateException.class);
}

@Test void testAndLiveExposeDifferentProviderCodes() {
    assertThat(TossPaymentEnvironment.TEST.providerCode()).isEqualTo("TOSS_TEST");
    assertThat(TossPaymentEnvironment.LIVE.providerCode()).isEqualTo("TOSS_LIVE");
}
```

- [ ] **Step 2: Run the test and confirm the missing type failure**

Run: `cd services/api; ./mvnw -Dtest=TossPaymentsConfigurationTest test`

Expected: FAIL because `TossPaymentEnvironment` does not exist.

- [ ] **Step 3: Implement explicit test/live validation**

```java
public enum TossPaymentEnvironment {
    TEST("toss-test", "TOSS_TEST", "test_", false),
    LIVE("toss-live", "TOSS_LIVE", "live_", true);

    public static TossPaymentEnvironment from(String provider) { /* accept exactly toss-test or toss-live */ }
    public void requireConfiguration(TossPaymentsProperties properties) { /* key pair, MID, URI and HTTPS checks */ }
    public String providerCode() { return providerCode; }
}
```

Use `URI` parsing; for live require `https`, a non-empty host, no user-info, query, or fragment. Validate that client and secret keys both start with the environment prefix and are not equal. Keep `requireTestConfiguration()` only as a delegating compatibility method until all callers move in Task 2.

- [ ] **Step 4: Wire properties and document safe defaults**

```yaml
payment:
  provider: ${PAYMENT_PROVIDER:fake}
  checkout-enabled: ${PAYMENT_CHECKOUT_ENABLED:false}
```

Add `PAYMENT_CHECKOUT_ENABLED=false` and the accepted provider values to `.env.example`; leave all secret values empty.

- [ ] **Step 5: Run configuration tests**

Run: `cd services/api; ./mvnw -Dtest=TossPaymentsConfigurationTest,TossPaymentsHttpClientTest test`

Expected: PASS with no live key value printed.

- [ ] **Step 6: Commit**

```bash
git add .env.example services/api/src/main/resources/application.yml services/api/src/main/java/team/hotelchain/payment/TossPaymentsProperties.java services/api/src/main/java/team/hotelchain/payment/TossPaymentsConfiguration.java services/api/src/main/java/team/hotelchain/payment/TossPaymentEnvironment.java services/api/src/test/java/team/hotelchain/payment/TossPaymentsConfigurationTest.java
git commit -m "feat(payments): validate Toss live configuration"
```

### Task 2: Provider snapshot 분리와 checkout kill switch

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/payment/PaymentCheckoutPolicy.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/TossReservationPaymentService.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/TossReservationPaymentController.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/TossRefundService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/TossPaymentAdjustmentGateway.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/TossChangePaymentController.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/ReservationChangeSettlementService.java`
- Modify: `services/api/src/main/java/team/hotelchain/reservation/PaymentProviderSafety.java`
- Test: `services/api/src/test/java/team/hotelchain/payment/TossReservationPaymentIntegrationTest.java`
- Test: `services/api/src/test/java/team/hotelchain/reservationchange/TossPaymentAdjustmentIntegrationTest.java`

**Interfaces:**
- Consumes: `TossPaymentEnvironment`
- Produces: `PaymentCheckoutPolicy.requireEnabled()` throwing `BusinessConflictException("PAYMENT_CHECKOUT_DISABLED", ...)`

- [ ] **Step 1: Add failing live-provider and kill-switch tests**

```java
@Test void disabledCheckoutCreatesNoAttemptOrHold() {
    assertThatThrownBy(() -> service.checkout(reservationId, TOKEN, "checkout-1"))
        .isInstanceOfSatisfying(BusinessConflictException.class,
            error -> assertThat(error.code()).isEqualTo("PAYMENT_CHECKOUT_DISABLED"));
    assertThat(jdbc.queryForObject("select count(*) from payment_provider_attempt", Integer.class)).isZero();
}

@Test void liveChargeStoresLiveProviderSnapshot() {
    var result = liveFixture.completeChangeCharge();
    assertThat(result.provider()).isEqualTo("TOSS_LIVE");
}
```

- [ ] **Step 2: Run focused tests and verify current hard-coded `TOSS_TEST` failure**

Run: `cd services/api; ./mvnw -Dtest=TossReservationPaymentIntegrationTest,TossPaymentAdjustmentIntegrationTest test`

Expected: FAIL because checkout ignores the flag and rows are hard-coded to `TOSS_TEST`.

- [ ] **Step 3: Inject environment and policy at the mutation boundary**

```java
public final class PaymentCheckoutPolicy {
    private final boolean enabled;
    public void requireEnabled() {
        if (!enabled) throw new BusinessConflictException(
            "PAYMENT_CHECKOUT_DISABLED", "새 결제 시작이 일시 중지되었습니다.");
    }
}
```

Call it before creating a new reservation checkout or a new `CREATE_CHECKOUT` adjustment. Do not call it from confirm, lookup, refund, cancellation, webhook reconciliation, or apply workers.

- [ ] **Step 4: Replace provider literals with the environment snapshot**

Use `environment.providerCode()` for inserts, lookups, compatibility checks, unique-key queries, and completed `payment_transaction` rows. Change Spring conditions so Toss services/controllers are enabled for exactly `toss-test` or `toss-live`; fake mode must still start without a Toss client.

- [ ] **Step 5: Run payment and reservation-change regressions**

Run: `cd services/api; ./mvnw -Dtest=TossPaymentsHttpClientTest,TossReservationPaymentIntegrationTest,TossPaymentAdjustmentIntegrationTest,ReservationChangeSettlementIntegrationTest,CustomerSelfServiceReservationChangeIntegrationTest test`

Expected: PASS; fake tests store no Toss row and test mode still stores `TOSS_TEST`.

- [ ] **Step 6: Commit**

```bash
git add services/api/src/main/java/team/hotelchain/payment services/api/src/main/java/team/hotelchain/reservation/PaymentProviderSafety.java services/api/src/main/java/team/hotelchain/reservationchange services/api/src/test/java/team/hotelchain/payment/TossReservationPaymentIntegrationTest.java services/api/src/test/java/team/hotelchain/reservationchange/TossPaymentAdjustmentIntegrationTest.java
git commit -m "feat(payments): separate Toss live transactions"
```

### Task 3: Webhook 재조회 ingress 강화

**Files:**
- Create: `services/api/src/main/resources/db/migration/V43__toss_live_webhook_ingress.sql`
- Modify: `services/api/src/main/java/team/hotelchain/reservationchange/TossPaymentWebhookController.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/TossWebhookRequest.java`
- Test: `services/api/src/test/java/team/hotelchain/payment/TossPaymentWebhookIntegrationTest.java`

**Interfaces:**
- Produces: `POST /api/payments/toss/webhook`, max 64 KiB, accepted event `PAYMENT_STATUS_CHANGED`, response `202` for valid known/unknown orders without trusting body status
- Produces: dedupe key `(provider, transmission_id)` and lookup key `(provider, order_id)`

- [ ] **Step 1: Add failing webhook boundary tests**

```java
@Test void duplicateTransmissionEnqueuesOneLookup() { /* post same transmission id twice; assert one pending lookup */ }
@Test void unknownOrderDoesNotCreatePaymentState() { /* post valid body; assert no attempt or transaction */ }
@Test void bodyStatusCannotCompleteReservation() { /* known order + DONE body; assert only QUERY row */ }
@Test void oversizedOrUnsupportedEventIsRejected() { /* 64 KiB+1 and CANCEL_STATUS_CHANGED -> 413/400 */ }
```

- [ ] **Step 2: Run and observe duplicate/event validation failures**

Run: `cd services/api; ./mvnw -Dtest=TossPaymentWebhookIntegrationTest test`

Expected: FAIL because the current table has only `order_id` and the controller does not bind transmission metadata.

- [ ] **Step 3: Add additive webhook schema**

```sql
ALTER TABLE toss_webhook_lookup
  ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'TOSS_TEST',
  ADD COLUMN transmission_id VARCHAR(100),
  ADD COLUMN event_type VARCHAR(60),
  ADD COLUMN received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE UNIQUE INDEX toss_webhook_transmission_idx
  ON toss_webhook_lookup(provider, transmission_id) WHERE transmission_id IS NOT NULL;
```

Replace the old order-only primary-key assumption with an additive surrogate ID or a provider/order unique key without modifying V40.

- [ ] **Step 4: Parse raw bytes before JSON and enqueue only authoritative lookup**

Accept `byte[]`, reject bodies over 65,536 bytes, parse only `eventType` and `data.orderId`/`orderId`, validate `[A-Za-z0-9_-]{6,64}`, and read `tosspayments-webhook-transmission-id`. Never map `status`, amount, paymentKey, or cancellation data into reservation state.

- [ ] **Step 5: Run webhook and adjacent payment tests**

Run: `cd services/api; ./mvnw -Dtest=TossPaymentWebhookIntegrationTest,TossReservationPaymentIntegrationTest,TossPaymentAdjustmentIntegrationTest test`

Expected: PASS with one lookup for duplicate delivery and no state transition from webhook body.

- [ ] **Step 6: Commit**

```bash
git add services/api/src/main/resources/db/migration/V43__toss_live_webhook_ingress.sql services/api/src/main/java/team/hotelchain/payment/TossWebhookRequest.java services/api/src/main/java/team/hotelchain/reservationchange/TossPaymentWebhookController.java services/api/src/test/java/team/hotelchain/payment/TossPaymentWebhookIntegrationTest.java
git commit -m "feat(payments): harden Toss webhook lookups"
```

### Task 4: Phase verification and operations record

**Files:**
- Create: `docs/changes/2026-09-15-toss-live-payment-core.md`
- Modify: `docs/overview/current-development-context.md`

- [ ] **Step 1: Run the focused backend gate**

Run: `cd services/api; ./mvnw -Dtest=TossPaymentsConfigurationTest,TossPaymentsHttpClientTest,TossReservationPaymentIntegrationTest,TossPaymentWebhookIntegrationTest,TossPaymentAdjustmentIntegrationTest,ReservationChangeSettlementIntegrationTest,CustomerSelfServiceReservationChangeIntegrationTest,ReservationCancellationIntegrationTest test`

Expected: all selected tests PASS with zero failures/errors.

- [ ] **Step 2: Compile and inspect migration/diff hygiene**

Run: `cd services/api; ./mvnw -DskipTests compile; cd ../..; git diff --check; git status --short`

Expected: compile and diff check exit 0; `.tmp/` remains untouched.

- [ ] **Step 3: Write the Korean change record**

Record exact commands/counts, provider separation, kill-switch semantics, webhook non-authority, migrations, rollback, and unverified live credentials/HTTPS delivery. Do not claim live payment or signed general-payment webhook verification.

- [ ] **Step 4: Commit documentation**

```bash
git add docs/changes/2026-09-15-toss-live-payment-core.md docs/overview/current-development-context.md
git commit -m "docs(payments): record Toss live core verification"
```

- [ ] **Step 5: Stop before external side effects**

Do not insert live keys, enable live checkout, register a public webhook, charge a card, or issue a refund. Report the exact environment prerequisites requiring user action.
