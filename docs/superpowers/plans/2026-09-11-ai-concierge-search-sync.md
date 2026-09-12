# AI 예약 도우미 조건 동기화와 최신 검색 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 추천을 예약 바에 적용할 때 고객 웹이 Spring의 최신 판매 가능 객실을 다시 조회하게 한다.

**Architecture:** `App`이 예약 조건과 availability 결과의 유일한 UI 소유자로 남는다. `ConciergePanel`은 대화 criteria를 이어 받고, 적용 요청을 비동기 callback으로 넘길 뿐 AI 후보를 예약 카드로 승격하지 않는다.

**Tech Stack:** React 19, TypeScript, Vite, FastAPI/LangGraph, Spring Boot availability API.

**Spec:** `docs/superpowers/specs/2026-09-11-ai-concierge-search-sync-design.md`

## Global Constraints

- Spring Boot만 가격·재고·예약 가능 여부를 계산한다.
- AI 후보는 설명용이며 적용 시 최신 Spring availability 응답만 예약 카드에 사용한다.
- 실패·0건에서는 이전 offer와 선택을 남기지 않는다.
- 새 UI 테스트 프레임워크나 외부 의존성을 추가하지 않는다.
- 예약 생성·임시 확보·결제는 변경하지 않는다.

---

### Task 1: 현재 예약 조건을 AI 대화에 전달하고 이전 추천을 무효화

**Files:**
- Modify: `apps/web/src/components/concierge-panel.tsx`
- Modify: `apps/web/src/App.tsx`

**Interfaces:**
- Produces: `ConciergePanel`의 exported `ConciergeCriteria` type.
- Consumes: `criteria: ConciergeCriteria`와 `onApply(criteria): Promise<boolean>`.

- [x] **Step 1: 현재 조건을 받는 UI 계약을 먼저 작성한다.**

`ConciergePanel` props를 아래 형태로 바꾸고 App은 선택된 호텔 지역과 현재 예약 입력값을 전달하도록 호출부를 수정한다.

```ts
export type ConciergeCriteria = { region?: string; checkIn?: string; checkOut?: string; adults?: number; children?: number; rooms?: number; breakfastIncluded?: boolean };
type Props = { criteria: ConciergeCriteria; onApply: (criteria: ConciergeCriteria) => Promise<boolean> };
```

`criteria`의 안정적인 값이 바뀌면 `result`를 `null`로 만들어 과거 후보의 적용 버튼을 없앤다.

- [x] **Step 2: 변경 계약의 실패 기준을 고정한다.**

Run:

```powershell
pnpm.cmd build
```

Working directory: `apps/web`

기준: 비동기 `onApply` 또는 App의 새 criteria 전달이 누락되면 검사 또는 build 단계에서 계약 위반이 드러난다. 최종 통과 결과는 Step 4에 기록한다.

- [x] **Step 3: 최소 상태 동기화를 구현한다.**

패널은 `/chat` body에 `result?.criteria ?? criteria`를 넣는다. 현재 예약 바가 바뀌면 이전 응답을 지우고, 새 대화만 이어 간다. state setter가 빈 values로 초기화하는 문제 없이 selected hotel의 `region`을 포함한다.

- [x] **Step 4: 고객 웹 build를 통과시킨다.**

Run:

```powershell
pnpm.cmd build
```

Working directory: `apps/web`

Expected: TypeScript와 Vite production build가 통과한다.

### Task 2: AI 적용 시 Spring 최신 availability 재조회

**Files:**
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/components/concierge-panel.tsx`
- Create: `apps/web/src/lib/latest-availability-request.ts`
- Test: `apps/web/test/latest-availability-request.test.ts`

**Interfaces:**
- Consumes: `api.availability(URLSearchParams)`와 `ConciergeCriteria`.
- Produces: App의 `applyConciergeCriteria(criteria): Promise<boolean>`.

- [x] **Step 1: 최신 결과만 예약 카드로 사용한다는 실패 기준을 기록한다.**

App의 적용 callback은 AI의 `offers`를 state에 복사하지 않고 다음 계약을 만족해야 한다.

```ts
const applied = await onApply(result.criteria);
if (applied) setOpen(false);
```

`onApply`은 지역·날짜·성인이 없거나 Spring 검색이 실패하면 `false`를 반환한다. 성공 시에만 Spring availability 결과로 `offers`를 채운다.

- [x] **Step 2: 이전 적용 흐름의 실패 기준을 고정한다.**

기준: 적용 callback이 AI offer를 직접 예약 카드 상태에 복사하거나 `api.availability`를 새로 호출하지 않으면 최신 재고·가격 계약을 충족하지 않는다.

- [x] **Step 3: 선택한 조건을 인자로 받는 검색 함수를 구현한다.**

기존 form `search`에서 availability 호출을 분리한다. AI callback은 지역을 현재 hotel ID로 해석한 뒤 hotel, date, guest, room, breakfast state를 갱신하고 분리된 함수에 동일 조건을 넘긴다. 분리된 함수는 `api.availability`의 새 응답을 조식 필터링해 `offers`에 설정하고, 호출 전 `selected`와 이전 offers를 비운다. `LatestAvailabilityRequest`는 조건 키가 달라진 뒤 늦게 도착한 응답이 카드·선택·안내·loading을 덮지 못하게 하며, 같은 AI 조건으로 시작한 새 요청은 유지한다.

성공 메시지는 "AI 조건을 적용해 최신 판매 가능 객실을 다시 조회했습니다."로 표시한다. 실패 시 패널을 닫지 않고 "최신 객실 조회에 실패했습니다. 예약 바에서 다시 검색해 주세요."를 안내한다.

- [x] **Step 4: 고객 웹 build를 다시 통과시킨다.**

Run:

```powershell
pnpm.cmd build
```

Working directory: `apps/web`

Expected: TypeScript와 production build가 통과한다.

### Task 3: 안전한 실제 대화 확인과 문서화

**Files:**
- Modify: `docs/changes/2026-09-10-ai-concierge.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/overview/project-brief.md`

- [x] **Step 1: 실제 AI 대화와 최신 검색을 예약 생성 없이 확인한다.**

고객 웹에서 예약 바의 날짜·인원을 정한 뒤 AI에 조식 조건만 입력한다. `/chat` request body에 기존 criteria가 포함되는지, 적용 후 `GET /api/availability`가 새로 발생하는지, 최신 카드의 total·remaining이 Spring 응답과 일치하는지를 확인한다. 실패/0건은 예약 생성·결제를 하지 않고 안내만 확인한다.

- [x] **Step 2: 변경 기록에 실제 검증과 한계를 적는다.**

AI는 여전히 정해진 한국어 패턴으로 조건을 추출하며 LLM·정책 임베딩과 고객 입력 자동 E2E는 구현하지 않았다는 점을 기록한다. build, 실제 chat/availability 비교, UI 적용 여부만 실제 결과로 적는다.

- [x] **Step 3: 변경 파일의 공백·줄바꿈 상태를 확인한다.**

Run:

```powershell
git diff --check
```

결과: 이번 AI 파일과 문서의 공백·줄바꿈을 점검했고, `git diff --check`는 공백 오류 없이 통과했다. 기존 수정 파일의 LF→CRLF 변환 경고만 출력했다.


## 실행 결과

- 고객 웹은 예약 바의 현재 조건을 `/chat`에 보내고, AI 적용 뒤 Spring availability를 직접 다시 조회한다. AI offer는 예약 카드 상태로 복사하지 않는다.
- `LatestAvailabilityRequest` 독립 실행 순서 보호 검사를 TypeScript 컴파일 뒤 Node로 실행해, 다른 조건의 늦은 응답은 무효화하고 같은 AI 조건에서 시작한 새 요청은 유지하는 것을 확인했다. 이 검사는 현재 `pnpm build`에 자동 등록되어 있지 않다.
- `apps/web`의 `pnpm.cmd build`가 통과했다. 실제 `9000 /chat`과 `4080 /api/availability`에서 속초·2026-09-18~20·성인 2·조식 포함 조건의 객실 유형·요금제·총액·잔여 객실이 일치했다.
- headless 고객 웹에서 `/chat` body의 현재 criteria, 적용 뒤 새 availability 요청, 조식 포함 카드 렌더링을 확인했다. 예약 생성·임시 확보·결제는 실행하지 않았다.
