# 일반 콘텐츠 페이지 초안 미리보기 Implementation Plan

**Goal:** 본사 관리자가 저장·발행 전 일반 콘텐츠 페이지를 고객 화면과 유사한 형태로 확인하게 한다.

**Architecture:** `ContentPageEditor`가 현재 메모리의 metadata와 구조화 블록을 전용 읽기 `Dialog`에 전달한다. dialog는 안전한 로컬 미디어 경로만 표현하며 어떤 변경 API도 호출하지 않는다.

**Tech Stack:** Next.js 16, React 19, TypeScript, shadcn/ui Dialog, Lucide, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-11-content-page-draft-preview-design.md`

## Global Constraints

- 사용자 제공 공유 작업 트리에서 진행하며 branch, worktree, commit을 만들지 않는다.
- `CONTENT_PAGE`의 미저장 입력만 표시하고, 서버·DB·발행 이력·미디어 사용 위치를 바꾸지 않는다.
- 고객 미디어 규칙과 일치하는 안전한 로컬 전달 경로만 이미지에 사용한다.

### Task 1: 미리보기 동작을 먼저 고정한다

**Files:**
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

- [x] 일반 콘텐츠 페이지에서 저장하지 않은 HERO 제목과 TEXT 본문을 입력한 뒤 미리보기 dialog가 이를 표시하는 E2E를 추가한다.
- [x] `닫기`와 Escape 뒤 시작 버튼 포커스 복귀, 변경 API 요청 부재를 검증한다.

### Task 2: 읽기 전용 preview component를 구현한다

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/content-page-preview-dialog.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`

- [x] `HERO`, `TEXT`, `CTA`를 현재 순서대로 읽기 전용으로 표현하는 dialog를 추가한다.
- [x] 안전한 이미지 경로만 요청하고, 나머지는 자리표시자로 처리한다.
- [x] 편집기 상단에 미리보기 제어를 연결하고 명시적인 닫기·Escape 동작을 제공한다.

### Task 3: focused 검증과 문서화

**Files:**
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/overview/project-brief.md`
- Modify: `docs/decisions/decision-log.md`

- [x] 대상 Playwright와 TypeScript 검사를 실행한다.
- [x] 구현 이유, 검증 결과, 미검증 범위를 한국어 문서에 기록한다.
- [x] `git diff --check`로 공백 오류를 확인한다.
