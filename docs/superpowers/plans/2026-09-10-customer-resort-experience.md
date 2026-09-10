# 고객 웹 리조트 경험 업그레이드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실제 예약 흐름을 유지한 채 고객 웹을 지점별 리조트 경험과 예약 전환 중심 화면으로 재구성한다.

**Architecture:** `App.tsx`는 API·예약 상태·세션 복구를 유지하고, 정적 지점 콘텐츠는 `destination-content.ts`로 분리한다. 프레젠테이션 섹션은 선택 지점과 예약 바 이동 콜백을 받아 렌더링하며 API를 호출하지 않는다. `styles.css`는 새 정보 구조의 데스크톱·390px 모바일 레이아웃을 책임진다.

**Tech Stack:** React 19, TypeScript, Vite 8, lucide-react, CSS

**Spec:** `docs/superpowers/specs/2026-09-10-customer-resort-experience-design.md`

## Global Constraints

- 고객 웹 포트는 `4000`, API 포트는 `4080`이다.
- 가격·재고·예약 확정은 Spring Boot API 반환값만 사용한다.
- 실제 롯데리조트 이미지·문구·로고를 복제하지 않고, 가상 호텔 자산만 사용한다.
- 예약 검색·확보·테스트 결제·취소·세션 복구 API 계약을 바꾸지 않는다.
- 키보드 포커스, 모바일 예약 폼, 상태 안내를 유지한다.

---

### Task 1: 지점 콘텐츠 모델과 이미지 자산

**Files:**
- Create: `apps/web/src/lib/destination-content.ts`
- Create: `apps/web/public/images/seorak-forest-hero.png`
- Create: `apps/web/public/images/jeju-volcanic-hero.png`
- Modify: `apps/web/src/App.tsx`

**Interfaces:**
- Produces: `DestinationContent`, `destinationContentByRegion(region: string): DestinationContent`
- Consumes: `Hotel.region` from `apps/web/src/lib/api.ts`

- [ ] **Step 1: Create the destination content contract**

```ts
export type DestinationContent = {
  heroImage: string
  heroAlt: string
  eyebrow: string
  title: string
  description: string
  arrival: { address: string; checkInOut: string; highlight: string }
  experiences: Array<{ category: string; title: string; description: string }>
  offers: Array<{ title: string; detail: string; bookingPeriod: string; stayPeriod: string }>
}

export function destinationContentByRegion(region: string): DestinationContent
```

- [ ] **Step 2: Add three independent fictional destination records**

Use the exact regions `속초`, `설악산`, `제주도`; use `속초` as the fallback. Give each record a different hero image, place description, three experiences, and two offers without price or availability claims.

- [ ] **Step 3: Generate fictional Seorak and Jeju hero images**

Create images with no text, no logos, and no real hotel branding. Preserve the existing `sokcho-coast-hero.png` asset.

- [ ] **Step 4: Pass the selected destination to the presentation tree**

```ts
const destination = destinationContentByRegion(selectedHotel?.region ?? '속초')
```

- [ ] **Step 5: Build to verify types and static assets**

Run: `pnpm build` in `apps/web`.
Expected: TypeScript and Vite build complete without errors.

### Task 2: Header, destination hero, and reusable booking entry

**Files:**
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes: `destination: DestinationContent`, existing `hotelId`, `checkIn`, `checkOut`, `adults`, and `search`.
- Produces: accessible navigation anchors and a semantic `#booking` search form.

- [ ] **Step 1: Make the header destination-aware and navigable**

Add anchors for `#stays`, `#experiences`, `#offers`, `#booking`, and a visible `예약 조회` link to `#reservation-management`. Keep the mobile menu button’s `aria-expanded` state and close the menu when an anchor is selected.

- [ ] **Step 2: Replace the single generic hero copy with destination content**

```tsx
<section className="hero" aria-labelledby="hero-title">
  <img src={destination.heroImage} alt={destination.heroAlt} />
  <div className="hero-copy">
    <p className="eyebrow">{destination.eyebrow}</p>
    <h1 id="hero-title">{destination.title}</h1>
    <p>{destination.description}</p>
    <a href="#booking" className="text-link">객실 예약하기 <ArrowRight size={18} /></a>
  </div>
</section>
```

- [ ] **Step 3: Preserve the existing search form behavior**

Keep the current `search` handler, date constraints, hotel select, adults select, busy state, and `role="status"` notice. Move only markup and styling; do not alter `URLSearchParams` fields or API calls.

- [ ] **Step 4: Implement desktop and mobile header/hero/booking styles**

At 900px keep the booking fields in two columns and at 600px use one column. Provide visible focus styles for menu, links, form controls, and submit button. Ensure the hero image uses `object-fit: cover` and a readable overlay.

- [ ] **Step 5: Build to verify the existing reservation contract remains compiled**

Run: `pnpm build` in `apps/web`.
Expected: build succeeds without modifying `src/lib/api.ts`.

### Task 3: Editorial experience, offers, and arrival information

**Files:**
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes: `destination.experiences`, `destination.offers`, `destination.arrival`.
- Produces: `#experiences`, `#offers`, and arrival information sections that link back to `#booking`.

- [ ] **Step 1: Add the experience section**

Render the three destination experiences as semantic articles inside `#experiences`. Each article shows category, title, description, and an anchor labelled `${title}로 예약하기` that points to `#booking`.

- [ ] **Step 2: Add the special-offer section**

Render `destination.offers` inside `#offers`. Every offer must display `예약 기간`, `투숙 기간`, title, detail, and a `예약하기` anchor to `#booking`. Do not add a price, discount percentage, or stock count.

- [ ] **Step 3: Add the arrival-information section**

Render address, check-in/out, and destination highlight using a definition list. Add a booking CTA and use the selected destination’s text.

- [ ] **Step 4: Apply editorial card layout**

Use a three-column experience grid on desktop, two columns for offers, and a single column below 600px. Avoid horizontal scrolling and preserve sufficient text contrast over all image overlays.

- [ ] **Step 5: Build to verify accessible HTML and TypeScript**

Run: `pnpm build` in `apps/web`.
Expected: build succeeds.

### Task 4: Reservation result styling and targeted visual verification

**Files:**
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`
- Modify: `docs/changes/2026-09-10-customer-booking.md`
- Modify: `docs/overview/current-development-context.md`

**Interfaces:**
- Consumes: existing `selected`, `reservation`, `busy`, `error`, `notice` state.
- Produces: `#reservation-management` that preserves all existing reservation actions.

- [ ] **Step 1: Add the reservation-management anchor**

Apply `id="reservation-management"` to the reservation result section. Keep `pay('SUCCESS')`, `pay('FAILURE')`, `cancel`, and the recovery link unchanged.

- [ ] **Step 2: Refine reservation and offer result styles**

Align result panels, offer cards, notices, and cancellation controls with the new navy·ivory visual system. Do not hide status, reservation number, nightly total, or error details.

- [ ] **Step 3: Run the production build**

Run: `pnpm build` in `apps/web`.
Expected: successful build.

- [ ] **Step 4: Verify one real browser reservation journey**

At `http://127.0.0.1:4000`, search available 속초 dates, select an offer, secure it, complete test payment, and cancel it. Confirm the visible result state after each action.

- [ ] **Step 5: Verify keyboard and mobile behavior**

At 390×844, confirm no horizontal scroll and that the booking form stays usable. With keyboard only, reach header navigation, destination select, date controls, adult select, and search submit with a visible focus indicator.

- [ ] **Step 6: Record outcomes and remaining scope in Korean**

Update both documentation files with changed behavior, validation outcome, unverified items, and follow-up AI/detailed-route scope.

- [ ] **Step 7: Commit implementation changes**

```powershell
git add apps/web docs/changes/2026-09-10-customer-booking.md docs/overview/current-development-context.md
git commit -m "feat: upgrade customer resort experience"
```
