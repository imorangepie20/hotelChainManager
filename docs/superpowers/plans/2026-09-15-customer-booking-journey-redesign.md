# Customer Booking Journey Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 검색부터 객실 선택, 예약, 결제, 예약 관리와 변경 결제까지 끊김 없는 고객 전용 화면으로 재구성한다.

**Architecture:** 홈은 검색 조건 수집만 담당하고 예약 흐름은 경로별 React 페이지와 공통 `booking-session` 상태로 분리한다. 검색 조건만 URL에 두고 예약 관리 토큰과 예약자 정보는 브라우저 세션에 보관하며, 가격·재고·상태 전이는 기존 Spring Boot 응답만 사용한다. 관리자 변경 화면은 기존 API를 유지하면서 결제 링크 생성·복사 상태를 명확히 표시한다.

**Tech Stack:** React 19, TypeScript 7, Vite 8, React Day Picker, Toss Payments JavaScript SDK v2, Playwright, Next.js 16, shadcn/ui.

**Spec:** `docs/superpowers/specs/2026-09-15-customer-booking-journey-redesign.md`

## Global Constraints

- 메인에는 검색 시작 기능만 두고 객실 카드·예약자 입력·결제·예약 관리 패널을 렌더링하지 않는다.
- 검색 조건만 URL에 저장하며 예약자 정보, 관리 토큰, 결제 정보는 URL·로그·분석 이벤트에 넣지 않는다.
- 가격, 재고, 예약 확정, 변경 차액은 Spring Boot 응답만 표시한다.
- 고객 4000, 관리자 4001, API 4080을 유지한다.
- 테스트 결제 화면에는 실제 과금이 없음을 명시한다.
- 모바일 기준 폭은 390px이며 가로 스크롤과 고정 버튼의 콘텐츠 가림이 없어야 한다.
- 실제 토스 결제와 환불은 모든 화면과 계약 검증이 끝난 마지막 작업에서만 실행한다.
- 기존 CMS 공개 경로, 영문 페이지, 미리보기 격리와 관리자 권한 검증을 보존한다.

## File Structure

- `apps/web/src/lib/customer-route.ts`: 예약 경로를 CMS 경로보다 먼저 판별한다.
- `apps/web/src/lib/booking-query.ts`: 검색 조건 URL 직렬화·검증.
- `apps/web/src/lib/booking-session.ts`: 선택, 예약 관리 토큰, 진행 중 예약의 sessionStorage 계약.
- `apps/web/src/components/customer-shell.tsx`: 예약 전용 헤더, 단계 표시, 반응형 요약 틀.
- `apps/web/src/components/booking-search-page.tsx`: 검색 조건 수정과 객실·요금제 비교.
- `apps/web/src/components/booking-checkout-page.tsx`: 예약자 입력, 확보, 결제 시작.
- `apps/web/src/components/booking-result-page.tsx`: 토스 복귀 뒤 서버 상태 확인.
- `apps/web/src/components/reservation-management-page.tsx`: 동일 브라우저 예약 목록·상세·취소·변경 상태.
- `apps/web/src/components/reservation-change-payment-page.tsx`: 기존·변경 조건 비교와 추가 결제 상태.
- `apps/web/src/App.tsx`: CMS 홈과 전용 예약 페이지의 최상위 라우팅만 소유.
- `apps/web/src/styles.css`: 예약 전용 레이아웃과 모바일·포커스 상태.
- `apps/web/test/customer-booking-journey.spec.ts`: 고객 전체 화면 계약.
- `apps/web/test/customer-reservation-management.spec.ts`: 조회·취소·변경 상태.
- `SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx`: 링크 발급·복사 결과와 만료 안내.
- `SDTPL_ADM/e2e/staff-reservation-change-settlement.spec.ts`: 직원 링크 전달 회귀.
- `docs/changes/2026-09-15-customer-booking-journey-redesign.md`: 구현·검증·미검증 기록.

---

### Task 1: 예약 경로와 안전한 진행 상태

**Files:**
- Modify: `apps/web/src/lib/customer-route.ts`
- Modify: `apps/web/src/lib/customer-route.test.ts`
- Create: `apps/web/src/lib/booking-query.ts`
- Create: `apps/web/src/lib/booking-query.test.ts`
- Create: `apps/web/src/lib/booking-session.ts`
- Create: `apps/web/src/lib/booking-session.test.ts`

**Interfaces:**
- Produces `BookingCriteria`, `parseBookingCriteria(search)`, `serializeBookingCriteria(criteria)`.
- Produces `BookingSelection`, `BookingSessionStore.loadSelection()`, `saveSelection()`, `saveReservationAccess()`, `listReservationAccess()`.
- Extends `CustomerRoute.kind` with `booking-results`, `booking-checkout`, `booking-complete`, `reservations`, `reservation-detail`.

- [ ] **Step 1: Write failing route, query, and storage tests**

```ts
assert.equal(resolveCustomerRoute('/booking/results')?.kind, 'booking-results')
assert.deepEqual(parseBookingCriteria('?hotelId=h&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1'), {
  hotelId: 'h', checkIn: '2026-09-22', checkOut: '2026-09-24', adults: 2, children: 0, rooms: 1,
})
store.saveReservationAccess({ reservationId: 'r1', managementToken: 'secret' })
assert.equal(store.listReservationAccess()[0]?.managementToken, 'secret')
assert.equal(serializeBookingCriteria(validCriteria).includes('secret'), false)
```

- [ ] **Step 2: Run tests and confirm the missing contracts fail**

Run: `cd apps/web; pnpm exec tsx src/lib/customer-route.test.ts; pnpm exec tsx src/lib/booking-query.test.ts; pnpm exec tsx src/lib/booking-session.test.ts`

Expected: new booking route kinds and modules are missing.

- [ ] **Step 3: Implement strict route, query, and session contracts**

```ts
export type BookingCriteria = {
  hotelId: string; checkIn: string; checkOut: string
  adults: number; children: number; rooms: number
}
export type ReservationAccess = { reservationId: string; managementToken: string }
```

Reject invalid dates, checkout not after check-in, adults below 1, negative children, and rooms below 1. Store selection and access under versioned session keys; malformed or old values return `null` without throwing. Match dedicated booking routes before generic CMS pages.

- [ ] **Step 4: Run direct tests and type check**

Run: `cd apps/web; pnpm exec tsx src/lib/customer-route.test.ts; pnpm exec tsx src/lib/booking-query.test.ts; pnpm exec tsx src/lib/booking-session.test.ts; pnpm exec tsc -b`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add apps/web/src/lib/customer-route.ts apps/web/src/lib/customer-route.test.ts apps/web/src/lib/booking-query.ts apps/web/src/lib/booking-query.test.ts apps/web/src/lib/booking-session.ts apps/web/src/lib/booking-session.test.ts
git commit -m "refactor(web): define customer booking routes"
```

### Task 2: 홈 검색과 전용 객실 결과 화면

**Files:**
- Create: `apps/web/src/components/customer-shell.tsx`
- Create: `apps/web/src/components/booking-search-page.tsx`
- Create: `apps/web/test/customer-booking-journey.spec.ts`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes Task 1 `BookingCriteria`, URL helpers, and route kinds.
- Produces `BookingSearchPage({ criteria })` and `CustomerBookingShell({ step, summary, children })`.
- Saves one `BookingSelection` before navigation to `/booking/checkout`.

- [ ] **Step 1: Write failing browser contracts**

```ts
await page.goto('/#booking')
await page.getByRole('button', { name: '객실 검색' }).click()
await expect(page).toHaveURL(/\/booking\/results\?/)
await expect(page.getByRole('heading', { name: '예약 가능한 객실' })).toBeVisible()
await expect(page.locator('#stays .offer-card')).toHaveCount(0)
await page.getByRole('button', { name: /스탠다드 시티 선택/ }).click()
await expect(page).toHaveURL('/booking/checkout')
```

- [ ] **Step 2: Run the focused scenario**

Run: `cd apps/web; pnpm exec playwright test test/customer-booking-journey.spec.ts --grep "홈 검색과 객실 선택"`

Expected: FAIL because search remains inline on the home page.

- [ ] **Step 3: Extract the results page and slim the home**

Move availability state and room cards from `App.tsx` into `BookingSearchPage`. The home submit handler navigates with `serializeBookingCriteria`; the results page calls `/api/availability`, groups rates by room type, exposes condition editing, and clears selection after any criteria change. Use the server total as the primary price and persist the selected offer only through `BookingSessionStore`.

- [ ] **Step 4: Verify desktop, 390px, keyboard, and build**

Run: `cd apps/web; pnpm exec playwright test test/customer-booking-journey.spec.ts --grep "홈 검색과 객실 선택|390px|키보드"; pnpm run build`

Expected: results appear only at `/booking/results`; one primary action, no horizontal overflow, and tab order reaches condition controls, cards, and next action.

- [ ] **Step 5: Commit**

```powershell
git add apps/web/src/App.tsx apps/web/src/components/customer-shell.tsx apps/web/src/components/booking-search-page.tsx apps/web/src/styles.css apps/web/test/customer-booking-journey.spec.ts
git commit -m "feat(web): add dedicated room search results"
```

### Task 3: 예약자 입력, 확보, 결제, 완료 화면

**Files:**
- Create: `apps/web/src/components/booking-checkout-page.tsx`
- Create: `apps/web/src/components/booking-result-page.tsx`
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/lib/toss-payments.ts`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`
- Modify: `apps/web/test/customer-booking-journey.spec.ts`

**Interfaces:**
- Consumes Task 1 selection/session and Task 2 shell.
- Produces checkout states `DETAILS|HOLDING|PAYMENT_READY|PAYMENT_OPEN|EXPIRED|ERROR`.
- Produces `/booking/complete` server-state rendering for `CONFIRMED|PENDING_PAYMENT|EXPIRED`.

- [ ] **Step 1: Write failing checkout state tests**

```ts
await expect(page.getByText('객실 선택 → 예약 정보 → 결제')).toBeVisible()
await page.getByLabel('예약자 이름').fill('변경 테스트')
await page.getByLabel('이메일').fill('change-test@example.com')
await page.getByRole('button', { name: '예약 및 결제 진행' }).click()
await expect(page.getByText(/남은 확보 시간/)).toBeVisible()
await expect(page.getByRole('button', { name: /토스 테스트 결제/ })).toBeEnabled()
```

Add separate mocked cases for price change, sold out, payment-window cancel, failed payment, unknown confirmation, and expired hold. Assert that no guest email or management token appears in the URL.

- [ ] **Step 2: Run checkout scenarios**

Run: `cd apps/web; pnpm exec playwright test test/customer-booking-journey.spec.ts --grep "예약 정보|가격 변경|확보 만료|결제 결과"`

Expected: FAIL because checkout and complete routes have no page components.

- [ ] **Step 3: Implement the checkout state machine**

Create the reservation only from the reviewed selection and entered guest details. After success, save `{ reservationId, managementToken }`, lock editable fields, display a server-derived expiry countdown, and call the existing Toss checkout helper. On return, remove payment query values before calling confirmation/status. Render success only when the server returns `CONFIRMED`; unknown stays in confirmation polling with a manual `서버 상태 다시 확인` action.

- [ ] **Step 4: Run customer checkout regression**

Run: `cd apps/web; pnpm exec playwright test test/customer-booking-journey.spec.ts; pnpm exec tsc -b; pnpm run build`

Expected: all mocked states pass; closing the payment window preserves the hold and enables retry.

- [ ] **Step 5: Commit**

```powershell
git add apps/web/src/components/booking-checkout-page.tsx apps/web/src/components/booking-result-page.tsx apps/web/src/lib/api.ts apps/web/src/lib/toss-payments.ts apps/web/src/App.tsx apps/web/src/styles.css apps/web/test/customer-booking-journey.spec.ts
git commit -m "feat(web): build booking checkout journey"
```

### Task 4: 예약 목록, 상세, 취소와 변경 진행 상태

**Files:**
- Create: `apps/web/src/components/reservation-management-page.tsx`
- Create: `apps/web/test/customer-reservation-management.spec.ts`
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes Task 1 `listReservationAccess()` and existing customer reservation API.
- Produces `ReservationManagementPage({ reservationId? })`.
- Displays only actions returned or permitted by server state; it does not infer payment success or refund amount.

- [ ] **Step 1: Write failing reservation management scenarios**

```ts
await page.goto('/reservations')
await expect(page.getByRole('heading', { name: '내 예약' })).toBeVisible()
await page.getByRole('link', { name: /스탠다드 시티 예약 상세/ }).click()
await expect(page.getByText('예약 확정')).toBeVisible()
await expect(page.getByRole('button', { name: '예약 취소' })).toBeVisible()
await expect(page.getByText('추가 결제 대기')).toBeVisible()
```

Cover empty same-browser history, inaccessible token, confirmed cancellation preview, cancellation pending, cancelled, expired, and an active reservation change.

- [ ] **Step 2: Run focused management scenarios**

Run: `cd apps/web; pnpm exec playwright test test/customer-reservation-management.spec.ts`

Expected: FAIL because reservation cards are still an inline home section.

- [ ] **Step 3: Implement dedicated management pages**

Load each same-browser access token independently and omit inaccessible entries without exposing existence. Translate server states into customer labels. Show cancellation policy and expected refund before the confirmation dialog. For an active change, show target conditions, difference, expiry, and `추가 결제 계속하기` only when the current browser has a valid customer change session.

- [ ] **Step 4: Run management, accessibility, and build checks**

Run: `cd apps/web; pnpm exec playwright test test/customer-reservation-management.spec.ts; pnpm exec tsc -b; pnpm run build`

Expected: lists and details survive reload, confirmation focus returns correctly, and 390px has no overflow.

- [ ] **Step 5: Commit**

```powershell
git add apps/web/src/components/reservation-management-page.tsx apps/web/src/lib/api.ts apps/web/src/App.tsx apps/web/src/styles.css apps/web/test/customer-reservation-management.spec.ts
git commit -m "feat(web): add customer reservation management"
```

### Task 5: 변경 비교 결제와 직원 링크 전달 UX

**Files:**
- Modify: `apps/web/src/components/reservation-change-payment-page.tsx`
- Modify: `apps/web/src/styles.css`
- Modify: `apps/web/test/customer-reservation-management.spec.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx`
- Modify: `SDTPL_ADM/e2e/staff-reservation-change-settlement.spec.ts`

**Interfaces:**
- Consumes existing fragment-to-HttpOnly session exchange and staff payment-link API.
- Produces before/after comparison, `AWAITING_PAYMENT|READY_TO_APPLY|APPLYING|COMPLETED|EXPIRED|RECONCILIATION_REQUIRED` customer states.
- Produces administrator link states `NOT_CREATED|CREATING|READY|COPIED|EXPIRED`.

- [ ] **Step 1: Write failing customer and staff browser contracts**

```ts
await expect(customer.getByRole('term', { name: '기존 예약' })).toBeVisible()
await expect(customer.getByRole('term', { name: '변경 예약' })).toBeVisible()
await expect(customer.getByText('추가 결제 100,000원')).toBeVisible()
await staff.getByRole('button', { name: '고객 결제 링크 만들기' }).click()
await staff.getByRole('button', { name: '고객 결제 링크 복사' }).click()
await expect(staff.getByRole('status')).toContainText('복사했습니다')
```

Cover expired links, provider failure, server confirmation polling, completed changes, and clipboard failure with a selectable readonly URL fallback.

- [ ] **Step 2: Run focused change-flow tests**

Run: `cd apps/web; pnpm exec playwright test test/customer-reservation-management.spec.ts --grep "예약 변경"`

Run: `cd SDTPL_ADM; pnpm exec playwright test e2e/staff-reservation-change-settlement.spec.ts --grep "결제 링크"`

Expected: FAIL because the customer lacks before/after comparison and the administrator has no copy-result state.

- [ ] **Step 3: Implement comparison and delivery feedback**

Extend the existing customer view model only if the server already returns authoritative previous and target values; if fields are absent, add them to the existing change-payment response without a new state transition. Emphasize changed fields, display existing/new totals and difference above the one payment action, and keep expired/unknown results on-page. In the administrator panel, show creation time, expiry, copy success, and a clear local-test label. Do not claim email delivery because no provider is configured.

- [ ] **Step 4: Run customer and administrator checks**

Run: `cd apps/web; pnpm exec playwright test test/customer-reservation-management.spec.ts; pnpm run build`

Run: `cd SDTPL_ADM; pnpm exec playwright test e2e/staff-reservation-change-settlement.spec.ts; pnpm exec tsc --noEmit`

Expected: customer and staff states pass at desktop and 390px; clipboard fallback remains usable by keyboard.

- [ ] **Step 5: Commit**

```powershell
git add apps/web/src/components/reservation-change-payment-page.tsx apps/web/src/styles.css apps/web/test/customer-reservation-management.spec.ts SDTPL_ADM/src/components/hotel-admin/reservation-change-panel.tsx SDTPL_ADM/e2e/staff-reservation-change-settlement.spec.ts
git commit -m "feat(booking): complete reservation change UX"
```

### Task 6: 연결 검증, 실제 토스 시나리오와 문서

**Files:**
- Create: `docs/changes/2026-09-15-customer-booking-journey-redesign.md`
- Modify: `docs/overview/current-development-context.md`

**Interfaces:**
- Consumes Tasks 1–5 and current Toss test gateway.
- Produces one Korean verification record with UI, API, DB, inventory, transaction evidence and remaining limitations.

- [ ] **Step 1: Run all changed-path automated checks**

Run: `cd apps/web; pnpm exec tsx src/lib/customer-route.test.ts; pnpm exec tsx src/lib/booking-query.test.ts; pnpm exec tsx src/lib/booking-session.test.ts; pnpm exec playwright test test/customer-booking-journey.spec.ts test/customer-reservation-management.spec.ts; pnpm run build`

Run: `cd SDTPL_ADM; pnpm exec playwright test e2e/staff-reservation-change-settlement.spec.ts; pnpm exec tsc --noEmit; pnpm run build`

Expected: every named test and both production builds pass.

- [ ] **Step 2: Inspect the full journey before financial actions**

Use 4000, 4001, and 4080 only. Verify home → results → checkout → payment-ready → management and staff change → link → customer comparison using mocked or read-only provider states. Confirm desktop, 390×844, keyboard order, focus restoration, and no guest/token/payment values in URLs.

- [ ] **Step 3: Run one controlled Toss test journey**

After the UI proof passes, create one Standard City reservation, approve one 240,000 KRW test payment, change to Deluxe Ocean with one 100,000 KRW additional payment, then change back with one 100,000 KRW partial refund. At each stage compare browser status with `reservation`, `inventory_day`, `payment_provider_attempt`, `payment_transaction`, and `payment_adjustment_attempt`. Provider UI clicks require action-time user confirmation; actual card, OTP, or account authentication remains user-controlled.

- [ ] **Step 4: Record evidence and remaining limitations**

Document exact commands, passed checks, reservation/change status, amount deltas, inventory counts, and that external email/SMS delivery and public webhook delivery remain unverified. Do not store secrets, full payment keys, public link tokens, or personal test data.

- [ ] **Step 5: Commit**

```powershell
git add docs/changes/2026-09-15-customer-booking-journey-redesign.md docs/overview/current-development-context.md
git commit -m "docs(booking): record redesigned journey verification"
```

## Plan Self-Review

- Spec coverage: Tasks 1–4 cover dedicated routes, home search, room comparison, checkout, payment result, reload-safe reservation management, cancellation, responsive and keyboard behavior. Task 5 covers customer change comparison and explicit staff link delivery. Task 6 enforces UI completion before actual Toss charge/refund verification.
- Placeholder scan: 금지된 자리표시자나 정의되지 않은 후속 단계가 없으며 각 작업에 파일, 인터페이스, 실패 확인, 구현 동작, 통과 확인, 커밋이 있다.
- Type consistency: Task 1 creates route/query/session contracts consumed by Tasks 2–4. Task 2 produces the selection used by Task 3. Tasks 4–5 share reservation/change status from the existing API. Task 6 consumes only completed UI and existing server authority.
