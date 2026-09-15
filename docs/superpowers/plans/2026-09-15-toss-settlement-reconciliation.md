# Toss Settlement Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Toss 라이브 정산 자료를 일자·페이지 단위로 안전하게 수집하고 내부 승인·환불 원장과 대사해 본사 관리자에게 읽기 전용 불일치 화면을 제공한다.

**Architecture:** 결제 승인 client와 별도의 65초 timeout `TossSettlementClient`가 `soldDate` 기준 API 페이지를 읽는다. lease 기반 run worker는 HTTP 호출을 DB transaction 밖에서 수행하고 provider snapshot과 실행 이력을 additive 테이블에 보존한다. 대사 결과는 예약·재고·환불 원장을 수정하지 않는 파생 감사 데이터이며 `HQ_ADMIN`만 조회한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Java `HttpClient`, Jackson, JDBC, PostgreSQL/Flyway, JUnit 5, Next.js 16, React 19, TypeScript, shadcn/ui, Playwright

**Spec:** `docs/superpowers/specs/2026-09-15-toss-live-payment-operations-design.md`

## Global Constraints

- 가격·재고·예약 확정·환불 성공 판정은 계속 Spring Boot가 소유한다.
- 정산은 조회 전용 외부 호출이며 수동 정산 `POST /v1/settlements`를 구현하지 않는다.
- `payment_transaction`, `payment_provider_attempt`, `toss_refund_command`를 대사 결과로 자동 수정하지 않는다.
- 실제 라이브 키·MID·거래 fixture를 Git이나 로그에 기록하지 않는다.
- 기존 migration checksum을 변경하지 않고 V44 additive migration만 추가한다.
- 테스트 환경의 빈 정산 결과를 실제 수수료 대사 완료로 보고하지 않는다.
- 고객 결제 수단 범위는 국내 카드·간편결제이며 다른 수단을 새로 지원하지 않는다.
- `.tmp/`를 수정하거나 커밋하지 않는다.

---

### Task 1: 정산 조회 HTTP 계약

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementClient.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementHttpClient.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/TossPaymentsConfiguration.java`
- Test: `services/api/src/test/java/team/hotelchain/payment/settlement/TossSettlementHttpClientTest.java`

**Interfaces:**
- Produces: `TossSettlementClient.fetch(LocalDate soldDate, int page, int size): SettlementPage`
- Produces: `SettlementRecord(String merchantAccount, String paymentKey, String transactionKey, String orderId, String currency, String method, long amountKrw, long feeKrw, long feeSupplyKrw, long feeVatKrw, long payoutKrw, Instant approvedAt, LocalDate soldDate, LocalDate paidOutDate, boolean cancellation)`
- Produces: `SettlementPage(List<SettlementRecord> records, boolean hasNext)`

- [ ] **Step 1: Write failing request and response-contract tests**

```java
@Test void requests_one_sold_date_page_with_live_basic_auth() {
    var http = new CapturingHttpClient(200, settlementJson(500));
    var client = new TossSettlementHttpClient(liveProperties(), http, new ObjectMapper());
    var page = client.fetch(LocalDate.of(2026, 9, 14), 2, 500);
    assertThat(http.request().uri().getRawQuery())
        .isEqualTo("startDate=2026-09-14&endDate=2026-09-14&dateType=soldDate&page=2&size=500");
    assertThat(http.request().timeout()).contains(Duration.ofSeconds(65));
    assertThat(page.hasNext()).isTrue();
}

@Test void rejects_wrong_mid_currency_method_and_malformed_amounts() {
    assertThatThrownBy(() -> client(bodyWith("mId", "foreign-mid")).fetch(DAY, 1, 500))
        .isInstanceOf(SettlementResponseException.class);
    assertThatThrownBy(() -> client(bodyWith("method", "가상계좌")).fetch(DAY, 1, 500))
        .isInstanceOf(SettlementResponseException.class);
}
```

- [ ] **Step 2: Run tests and confirm the missing client failure**

Run: `cd services/api; ./mvnw -Dtest=TossSettlementHttpClientTest test`

Expected: FAIL because `TossSettlementClient` and `TossSettlementHttpClient` do not exist.

- [ ] **Step 3: Implement the dedicated read-only client**

Use `GET https://api.tosspayments.com/v1/settlements` with Basic secret-key authentication, `Accept: application/json`, `dateType=soldDate`, `page>=1`, and `1<=size<=5000`. Set request timeout to 65 seconds and construct its `HttpClient` with a dedicated executor. Parse only the listed scalar fields, sum every integral non-negative `fees[].fee`, and treat `cancel != null` as a cancellation record. Require response `mId` to equal configured MID, `currency=KRW`, method in `카드|간편결제`, nonblank identifiers and response `soldDate` equal to the requested day. Preserve fee/payout arithmetic for Task 3 classification instead of rejecting a structurally valid record. Map 429, 5xx, I/O, interruption and malformed success bodies to typed exceptions without returning partial records.

- [ ] **Step 4: Wire the client only for `toss-live`**

Add a named daemon executor and `TossSettlementClient` bean under `@ConditionalOnProperty(name="payment.provider", havingValue="toss-live")`. Reuse `TossPaymentsProperties` and `TossPaymentEnvironment.LIVE.requireConfiguration(properties)`; do not expose the secret in exception text.

- [ ] **Step 5: Run the focused HTTP contract**

Run: `cd services/api; ./mvnw -Dtest=TossSettlementHttpClientTest,TossPaymentsConfigurationTest,TossPaymentsHttpClientTest test`

Expected: PASS with paging, exact query encoding, 65-second timeout, fee sum, cancellation parsing, 4xx, 429, 5xx, interruption and malformed JSON covered.

- [ ] **Step 6: Commit**

```bash
git add services/api/src/main/java/team/hotelchain/payment/settlement services/api/src/main/java/team/hotelchain/payment/TossPaymentsConfiguration.java services/api/src/test/java/team/hotelchain/payment/settlement
git commit -m "feat(settlement): add Toss settlement client"
```

### Task 2: 실행·snapshot 저장과 재개 가능한 worker

**Files:**
- Create: `services/api/src/main/resources/db/migration/V44__toss_settlement_reconciliation.sql`
- Create: `services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementRunService.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementWorker.java`
- Modify: `services/api/src/main/resources/application.yml`
- Modify: `.env.example`
- Test: `services/api/src/test/java/team/hotelchain/payment/settlement/TossSettlementIntegrationTest.java`

**Interfaces:**
- Consumes: `TossSettlementClient.fetch(LocalDate, int, int)`
- Produces: `TossSettlementRunService.create(LocalDate from, LocalDate to, UUID requestedBy): UUID`
- Produces: `TossSettlementWorker.processNext(): boolean`

- [ ] **Step 1: Write failing migration and worker tests**

```java
@Test void stores_each_provider_transaction_once_across_overlapping_runs() {
    UUID first = runs.create(DAY, DAY, staffId);
    worker.processNext();
    UUID second = runs.create(DAY, DAY, staffId);
    worker.processNext();
    assertThat(count("toss_settlement_snapshot")).isEqualTo(2);
    assertThat(status(first)).isEqualTo("SUCCEEDED");
    assertThat(status(second)).isEqualTo("SUCCEEDED");
}

@Test void resumes_at_the_failed_page_without_deleting_snapshots() {
    provider.failPage(2);
    UUID run = runs.create(DAY, DAY, staffId);
    worker.processNext();
    assertThat(nextPage(run)).isEqualTo(2);
    provider.recover();
    worker.makeDue(run);
    worker.processNext();
    assertThat(status(run)).isEqualTo("SUCCEEDED");
}
```

- [ ] **Step 2: Run tests and confirm missing V44 tables**

Run: `cd services/api; ./mvnw -Dtest=TossSettlementIntegrationTest test`

Expected: FAIL because `toss_settlement_run` and `toss_settlement_snapshot` do not exist.

- [ ] **Step 3: Add the additive V44 schema**

Create `toss_settlement_run` with provider, MID, inclusive sold-date range, `PENDING|PROCESSING|SUCCEEDED|FAILED`, current date/page cursor, counts, attempt count, next-attempt time, lease token/expiry, safe error code, requester, idempotency key, request hash and timestamps; make `(requested_by,idempotency_key)` unique. Create immutable `toss_settlement_snapshot` with every `SettlementRecord` field, `first_run_id`, and unique `(merchant_account,payment_key,transaction_key,sold_date)`. Create `toss_settlement_reconciliation` keyed by `(run_id,reconciliation_key)` with nullable snapshot/internal transaction/refund references, status, expected/provider amounts, fee fields and detail code. Add range/status indexes and CHECK constraints for KRW, nonnegative fees, page/range bounds and allowed statuses.

- [ ] **Step 4: Implement claim, out-of-transaction fetch and page checkpoint**

`create` accepts at most 31 inclusive days and rejects future dates. `processNext` claims one due run with `FOR UPDATE SKIP LOCKED`, a UUID token and 90-second lease; fetches exactly one page outside the transaction; then inserts snapshots with `ON CONFLICT DO NOTHING` and advances the cursor only when the claim token still matches. A short page advances to the next date, and the day after `to` completes ingestion. Retryable failures preserve rows, clear the lease and use capped backoff; terminal contract failures set `FAILED`. The scheduled drain is bounded to five pages and enabled only by `TOSS_SETTLEMENT_ENABLED=true` with `payment.provider=toss-live`.

- [ ] **Step 5: Run persistence and restart tests**

Run: `cd services/api; ./mvnw -Dtest=TossSettlementIntegrationTest test`

Expected: PASS for overlapping windows, duplicate natural keys, page resume, stale lease reclaim, 429/5xx retry, malformed terminal failure, and transaction-free provider calls.

- [ ] **Step 6: Commit**

```bash
git add .env.example services/api/src/main/resources/application.yml services/api/src/main/resources/db/migration/V44__toss_settlement_reconciliation.sql services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementRunService.java services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementWorker.java services/api/src/test/java/team/hotelchain/payment/settlement/TossSettlementIntegrationTest.java
git commit -m "feat(settlement): persist resumable Toss snapshots"
```

### Task 3: 내부 승인·환불 대사 분류

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementReconciliationService.java`
- Modify: `services/api/src/main/java/team/hotelchain/payment/settlement/TossSettlementWorker.java`
- Test: `services/api/src/test/java/team/hotelchain/payment/settlement/TossSettlementReconciliationIntegrationTest.java`

**Interfaces:**
- Produces: `TossSettlementReconciliationService.reconcile(UUID runId): ReconciliationSummary`
- Produces statuses: `MATCHED`, `AMOUNT_MISMATCH`, `FEE_MISMATCH`, `MISSING_INTERNAL`, `MISSING_PROVIDER`, `PENDING`

- [ ] **Step 1: Write one failing test per classification**

```java
@ParameterizedTest
@MethodSource("reconciliationCases")
void classifies_without_mutating_financial_ledgers(Fixture fixture, String expected) {
    fixture.seed();
    long beforeRefunded = refundedAmount();
    reconciliation.reconcile(fixture.runId());
    assertThat(resultStatus(fixture.key())).isEqualTo(expected);
    assertThat(refundedAmount()).isEqualTo(beforeRefunded);
}
```

Cases must cover: matching approval by `payment_provider_attempt.provider_event_id`; matching reservation-change approval by `toss_adjustment_order.provider_event_id`; matching refund by `toss_refund_command.provider_event_id`; provider amount mismatch; `sum(fees[].fee) != supplyAmount + vat` or payout identity mismatch as `FEE_MISMATCH`; provider-only row; finalized internal-only row; and a row inside the configured delay window as `PENDING`.

- [ ] **Step 2: Run and confirm no reconciliation rows are produced**

Run: `cd services/api; ./mvnw -Dtest=TossSettlementReconciliationIntegrationTest test`

Expected: FAIL because `TossSettlementReconciliationService` does not exist.

- [ ] **Step 3: Implement deterministic reconciliation**

Match approvals by provider `TOSS_LIVE`, MID, paymentKey and approval transactionKey. Match refunds by MID, paymentKey and refund transactionKey; compare the absolute provider cancellation amount to command amount. `MISSING_PROVIDER` applies only to succeeded internal events older than `payment.toss.settlement-delay-days` and within the run range; newer candidates are `PENDING`. Store a new immutable result set per run using stable keys, aggregate counts on the run, and never update reservations, inventory, payment transactions, attempts or refund commands.

- [ ] **Step 4: Invoke reconciliation only after full ingestion**

After the final date/page is durably stored, call `reconcile(runId)` in a new transaction and set the run to `SUCCEEDED` only after results and summary counts commit. Retrying the final step must replace only the incomplete current run's derived rows, never provider snapshots or prior successful run history.

- [ ] **Step 5: Run reconciliation and payment regressions**

Run: `cd services/api; ./mvnw -Dtest=TossSettlementReconciliationIntegrationTest,TossSettlementIntegrationTest,TossReservationPaymentIntegrationTest,TossPaymentAdjustmentIntegrationTest,CancellationIntegrationTest test`

Expected: PASS with every status classification and unchanged financial ledgers.

- [ ] **Step 6: Commit**

```bash
git add services/api/src/main/java/team/hotelchain/payment/settlement services/api/src/test/java/team/hotelchain/payment/settlement
git commit -m "feat(settlement): reconcile Toss and internal ledgers"
```

### Task 4: 본사 전용 정산 운영 API

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/payment/settlement/StaffSettlementController.java`
- Create: `services/api/src/main/java/team/hotelchain/payment/settlement/SettlementViews.java`
- Test: `services/api/src/test/java/team/hotelchain/payment/settlement/StaffSettlementControllerIntegrationTest.java`

**Interfaces:**
- Produces: `POST /api/staff/settlements/runs` with `{from,to}` and `Idempotency-Key`
- Produces: `GET /api/staff/settlements/runs?from=&to=&page=&size=`
- Produces: `GET /api/staff/settlements/reconciliations?runId=&status=&page=&size=`
- Produces: `GET /api/staff/settlements/reconciliations/{id}`

- [ ] **Step 1: Write failing authorization, validation and paging tests**

```java
@Test void headquarters_can_create_and_read_but_branch_cannot() throws Exception {
    mvc.perform(post("/api/staff/settlements/runs")
        .header("X-Staff-Session", hqToken).header("Idempotency-Key", "settle-20260914")
        .contentType(APPLICATION_JSON).content("{\"from\":\"2026-09-14\",\"to\":\"2026-09-14\"}"))
        .andExpect(status().isAccepted());
    mvc.perform(get("/api/staff/settlements/runs").header("X-Staff-Session", branchToken))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run and confirm missing endpoints**

Run: `cd services/api; ./mvnw -Dtest=StaffSettlementControllerIntegrationTest test`

Expected: FAIL with 404 responses.

- [ ] **Step 3: Implement headquarters-only views**

Call `StaffAccessService.requireHeadquarters` on every endpoint. Validate ISO dates, inclusive range <=31 days, page >=1, size 1..100, allowed reconciliation statuses and idempotency key `[A-Za-z0-9_-]{1,100}`. Return run summary counts and paged reconciliation rows without secrets or raw provider JSON. Detail includes provider/internal identifiers, amounts, fee/payout, dates and safe detail code, but no customer personal data.

- [ ] **Step 4: Make run creation idempotent**

Store request hash and unique `(requested_by,idempotency_key)` in V44. Exact replay returns the same run; a changed date range with the same key returns 409. Return 202 for new and replayed pending work.

- [ ] **Step 5: Run API and staff access regressions**

Run: `cd services/api; ./mvnw -Dtest=StaffSettlementControllerIntegrationTest,StaffAccessIntegrationTest test`

Expected: PASS for HQ access, branch/anonymous denial, paging, filters, replay, conflicting replay and invalid range.

- [ ] **Step 6: Commit**

```bash
git add services/api/src/main/java/team/hotelchain/payment/settlement services/api/src/test/java/team/hotelchain/payment/settlement services/api/src/main/resources/db/migration/V44__toss_settlement_reconciliation.sql
git commit -m "feat(settlement): expose headquarters reconciliation API"
```

### Task 5: 본사 읽기 전용 정산 화면

**Files:**
- Modify: `SDTPL_ADM/src/lib/nav.ts`
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Create: `SDTPL_ADM/src/app/(dashboard)/dashboard/settlements/page.tsx`
- Create: `SDTPL_ADM/src/components/hotel-admin/settlement-reconciliation.tsx`
- Create: `SDTPL_ADM/src/lib/settlement-view-state.ts`
- Create: `SDTPL_ADM/src/lib/settlement-view-state.test.ts`
- Create: `SDTPL_ADM/e2e/settlement-reconciliation.spec.ts`

**Interfaces:**
- Consumes: Task 4 run/reconciliation list and detail APIs
- Produces: HQ-only `/dashboard/settlements` read-only screen

- [ ] **Step 1: Write failing pure-state and Playwright contracts**

```ts
assert.deepEqual(statusTone("AMOUNT_MISMATCH"), { label: "금액 불일치", tone: "destructive" });
assert.equal(buildSettlementQuery({ runId: "run-1", status: "MISSING_PROVIDER", page: 2 }),
  "runId=run-1&status=MISSING_PROVIDER&page=2&size=50");
```

The Playwright test mocks HQ session and APIs, verifies summary cards, status filter, table, row-detail dialog, loading/error/empty states, keyboard dialog close and 390×844 horizontal containment. It also verifies branch navigation does not contain `정산 대사`.

- [ ] **Step 2: Run and confirm missing UI modules**

Run: `cd SDTPL_ADM; pnpm exec tsx src/lib/settlement-view-state.test.ts; pnpm exec playwright test e2e/settlement-reconciliation.spec.ts --project=chromium`

Expected: FAIL because state helpers and the page do not exist.

- [ ] **Step 3: Add typed API and HQ-only navigation**

Add run, reconciliation summary/list/detail types and GET functions to `staff-api.ts`. Add `정산 대사` with `Landmark` icon to the HQ operations group only. Do not add a run-start button; this phase's UI is read-only.

- [ ] **Step 4: Build the responsive accessible view**

Reuse `Card`, `Badge`, `Table`, `Select`, `Dialog`, `Button`, `Skeleton` and existing spacing tokens. Show latest run status, matched/pending/mismatch counts, date range and last completion. Default the table to non-`MATCHED` rows, allow status filtering, keep identifiers breakable/tabular, provide explicit empty and error recovery states, and restore focus after the detail dialog closes.

- [ ] **Step 5: Run frontend verification**

Run: `cd SDTPL_ADM; pnpm exec tsx src/lib/settlement-view-state.test.ts; pnpm exec playwright test e2e/settlement-reconciliation.spec.ts e2e/admin-navigation.spec.ts --project=chromium; pnpm exec tsc --noEmit; pnpm run build`

Expected: all contracts, Chromium checks, TypeScript and production build pass.

- [ ] **Step 6: Commit**

```bash
git add SDTPL_ADM/src/lib/nav.ts SDTPL_ADM/src/lib/staff-api.ts SDTPL_ADM/src/app/\(dashboard\)/dashboard/settlements/page.tsx SDTPL_ADM/src/components/hotel-admin/settlement-reconciliation.tsx SDTPL_ADM/src/lib/settlement-view-state.ts SDTPL_ADM/src/lib/settlement-view-state.test.ts SDTPL_ADM/e2e/settlement-reconciliation.spec.ts
git commit -m "feat(admin): show settlement mismatches"
```

### Task 6: 단계 검증과 운영 기록

**Files:**
- Create: `docs/changes/2026-09-15-toss-settlement-reconciliation.md`
- Modify: `docs/overview/current-development-context.md`

- [ ] **Step 1: Run the focused backend gate**

Run: `cd services/api; ./mvnw -Dtest=TossSettlementHttpClientTest,TossSettlementIntegrationTest,TossSettlementReconciliationIntegrationTest,StaffSettlementControllerIntegrationTest,TossPaymentsHttpClientTest,TossReservationPaymentIntegrationTest,TossPaymentAdjustmentIntegrationTest,CancellationIntegrationTest test`

Expected: zero failures and errors; PostgreSQL validates V44.

- [ ] **Step 2: Run the admin gate**

Run: `cd SDTPL_ADM; pnpm exec tsx src/lib/settlement-view-state.test.ts; pnpm exec playwright test e2e/settlement-reconciliation.spec.ts e2e/admin-navigation.spec.ts --project=chromium; pnpm exec tsc --noEmit; pnpm run build`

Expected: all commands exit 0.

- [ ] **Step 3: Verify diff and write the Korean change record**

Run: `cd services/api; ./mvnw -DskipTests compile; cd ../..; git diff --check; git status --short`

Record exact test counts, V44, read-only authority, retry/resume behavior and rollback. Explicitly list as unverified: live credentials, actual `/v1/settlements`, real fee/payout values, next-day appearance, ingress/network allowlist and user browser interaction.

- [ ] **Step 4: Commit and push documentation**

```bash
git add docs/changes/2026-09-15-toss-settlement-reconciliation.md docs/overview/current-development-context.md docs/superpowers/plans/2026-09-15-toss-settlement-reconciliation.md
git commit -m "docs(settlement): record reconciliation verification"
git push
```

- [ ] **Step 5: Stop before external side effects**

Do not enable `TOSS_SETTLEMENT_ENABLED`, inject live secrets, call the real Toss endpoint, or claim real fee/payout reconciliation. The user must provide and approve the deployment environment and the first live read-only run.
