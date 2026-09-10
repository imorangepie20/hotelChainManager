# 관리자 진입 대시보드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 제공된 관리자 테마에서 본사·지점 컨텍스트를 전환하는 4001 운영 진입 화면을 만든다.

**Architecture:** 기존 Dashboard route와 UI 컴포넌트를 유지한다. 화면 전용 클라이언트 컴포넌트가 선택 상태를 갖고 페이지는 이를 배치한다. API 호출과 예약 변경은 하지 않는다.

**Tech Stack:** Next.js 16.2.7, React 19.2.4, Tailwind CSS 4, 제공된 shadcn 기반 UI 컴포넌트.

**Spec:** [관리자 진입 대시보드 설계](../specs/2026-09-10-admin-entry-dashboard-design.md)

## Global Constraints

- 관리자 포트는 `4001`이다.
- `SDTPL_ADM/` 공통 Card·Button·Sidebar를 재사용한다.
- 데이터 준비 전에는 지표값·예약값을 생성해 표시하지 않는다.
- API 호출, 인증, 예약 변경을 이 작업에 추가하지 않는다.

### Task 1: 운영 컨텍스트 대시보드

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/operations-overview.tsx`
- Modify: `SDTPL_ADM/src/app/(dashboard)/dashboard/default/page.tsx`
- Modify: `SDTPL_ADM/package.json`

**Interfaces:**
- Produces: `OperationsOverview`, 관리자 첫 화면에서 렌더링한다.

- [x] 제공 Card·Button과 `lucide-react` 아이콘으로 본사·속초·설악산·제주 선택 버튼을 만든다.
- [x] 본사는 지점 준비 상태 카드를, 지점은 도착·출발·객실·청소 준비 카드를 표시한다.
- [x] 선택 버튼은 키보드 포커스와 `aria-pressed`를 제공한다.
- [x] 기존 기본 대시보드 위젯을 `OperationsOverview`로 교체한다.
- [x] `dev` 스크립트에 `--port 4001`을 설정하고 Playwright 대상도 같은 포트로 변경한다.
- [x] Next.js 최적화 빌드와 4001 HTTP 200 응답을 확인한다.

### Task 2: 기록 갱신

**Files:**
- Modify: `docs/overview/current-development-context.md`
- Create: `docs/changes/2026-09-10-admin-entry-dashboard.md`

- [x] 구현 범위와 검증 결과, 인증·API 미연결 항목을 기록한다.
