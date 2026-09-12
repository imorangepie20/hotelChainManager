# 리조트 상세 콘텐츠 블록 기반 Implementation Plan

> **For agentic workers:** Execute tasks in order. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 객실·다이닝·시설·프로모션 상세 페이지에 공통으로 필요한 갤러리, 특징 카드, 사양 표, 아코디언, 안내 목록을 현재 안전한 CMS 문서·발행 흐름 안에서 작성하고 고객 웹에 렌더링한다.

**Architecture:** 첫 단계는 기존 `HOME_PAGE`와 `CONTENT_PAGE`의 `seo + blocks` 문서를 확장한다. `HERO`의 단일 첫 블록 규칙과 초안/발행/version/audit/미디어 UUID 계약은 유지하고, 새 block마다 서버 validator·미디어 정규화·사용 위치 기록·고객 parser·관리자 폼을 같은 schema로 확장한다. `ROOM`-`room_type` 연결, 다지점 `PROMOTION`, 깊은 트리, 번역·승인·예약 발행은 후속 기능 단위다.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL 16, React·TypeScript·Vite, Next.js·React, Playwright.

**Spec:** `docs/architecture/lotte-resort-level-cms-functional-design.md`

## Global Constraints

- 기존 `HERO`, `TEXT`, `CTA` 문서와 공개 경로를 호환한다. 기존 고객 콘텐츠를 마이그레이션하거나 재발행하지 않는다.
- 블록 문서는 JSON 원문 편집이 아닌 관리자 폼으로만 작성한다.
- 외부 이미지·외부 CTA·HTML·script는 계속 거부한다. 이미지는 asset UUID와 정확한 안전 전달 경로를 사용한다.
- `HERO`는 첫 번째이자 하나인 블록이다. 블록 총수와 각 배열의 상한을 서버와 고객 parser에 동일하게 적용한다.
- gallery 자산은 초안/발행 `website_media_usage`에 각각 기록한다. 저장·발행·보관·복원·삭제의 기존 정합성을 깨지 않는다.
- 현재 공유 작업 트리에서 작업하며 branch, worktree, commit, reset, clean을 만들지 않는다.
- 변경 도메인의 테스트만 실행한다. 실제 사용자가 만든 페이지·자산을 저장·발행·보관·삭제하지 않는다.

## Block Contract

| 타입 | 필수 필드 | 수량 제한 |
| --- | --- | --- |
| `IMAGE_GALLERY` | `title`, `items[]`, item의 `imageAssetId`, `imageSrc`, `imageAlt` | 이미지 2~12장 |
| `FEATURE_GRID` | `title`, `items[]`, item의 `title`, `description` | 카드 2~6개 |
| `SPEC_TABLE` | `title`, `rows[]`, row의 `label`, `value` | 행 1~12개 |
| `ACCORDION` | `title`, `items[]`, item의 `title`, `content` | 항목 1~12개 |
| `NOTICE_LIST` | `title`, `items[]` | 안내 1~20개 |

각 block에는 선택 `eyebrow`·`description`을 둘 수 있다. `IMAGE_GALLERY.items[].caption`은 선택이다. `NOTICE_LIST.items[]`는 text와 `DEFAULT`/`IMPORTANT` severity를 가진다. 새 block은 `HERO` 이후에만 추가하고 block 총수는 20개로 늘린다.

### Task 1: 서버 문서·미디어 참조 계약

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/ContentPageValidator.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaReferenceService.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Interfaces:**
- Consumes: `Map<String, Object>`의 `seo`, `blocks` 문서.
- Produces: 새 five block이 포함된 `WebsitePageDocument` 초안·발행본과 정확한 `website_media_usage` 행.

- [ ] **Step 1: gallery와 상세 block을 저장·발행하는 실패 통합 테스트를 추가한다.**

테스트는 새 `CONTENT_PAGE`에 모든 block을 저장·발행하고 공개 resolve의 document와 `blocks[1].items[0].imageAssetId` draft/published usage를 확인한다. unsafe gallery URL, 한 장짜리 gallery, 잘못된 severity, 빈 표 행, 허용 범위를 넘는 배열은 거부한다.

- [ ] **Step 2: 대상 테스트가 새 block 거부로 실패하는지 확인한다.**

```powershell
# services/api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
```

- [ ] **Step 3: `ContentPageValidator`에 새 allowlist와 정확한 field·길이·배열 검증을 추가한다.**

공통 optional field를 별도 helper로 두고, gallery item은 HERO와 동일한 UUID·경로·alt 규칙을 적용한다. 첫 HERO와 하나의 HERO 규칙은 유지한다.

- [ ] **Step 4: `WebsiteMediaReferenceService`가 gallery asset을 정규화·동기화하게 한다.**

gallery item의 `imageAssetId`, `imageSrc`, `imageAlt`를 normalizer가 다시 확인하고, usage field path를 `blocks[{index}].items[{itemIndex}].imageAssetId`로 기록한다. 기존 HERO usage와 landing 처리에는 회귀가 없어야 한다.

- [ ] **Step 5: 대상 서버 테스트를 통과시킨다.**

동일한 `WebsitePageIntegrationTest` 한 개만 실행해 저장·발행·거부·미디어 usage를 확인한다.

### Task 2: 고객 parser·상세 렌더러

**Files:**
- Modify: `apps/web/src/lib/content-page.ts`
- Modify: `apps/web/src/lib/content-page.test.ts`
- Modify: `apps/web/src/components/content-page.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes: 공개 `ContentPageDocument`의 다섯 새 block.
- Produces: 안전하게 검증된 상세 섹션과 키보드 가능한 gallery/accordion UI.

- [ ] **Step 1: 새 block parsing과 unsafe 문서 거부를 검증하는 실패 TypeScript 테스트를 추가한다.**

gallery UUID/전달 경로 불일치, unknown block field, block 총수 21개, 잘못된 notice severity를 문서 전체 거부로 고정한다.

- [ ] **Step 2: parser type union과 validator를 구현한다.**

서버와 동일한 field·길이·수량·allowlist를 재현한다. parser는 JSON을 신뢰하지 않고 안전한 block만 반환한다.

- [ ] **Step 3: 고객 상세 section을 구현한다.**

gallery는 현재 선택 이미지, 이전/다음 버튼, 썸네일, `aria-live` 안내를 제공한다. feature grid, specification table, accordion, notice list는 semantic HTML을 사용한다. 페이지의 로컬 CTA 규칙은 기존 방식으로 유지한다.

- [ ] **Step 4: 고객 코드 수준 검증을 통과시킨다.**

```powershell
# apps/web
pnpm.cmd exec tsx src/lib/content-page.test.ts
pnpm.cmd build
```

### Task 3: 관리자 유형별 block 편집과 미리보기

**Files:**
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-preview-dialog.tsx`
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Interfaces:**
- Consumes: 서버가 허용한 structured block document.
- Produces: `IMAGE_GALLERY`, `FEATURE_GRID`, `SPEC_TABLE`, `ACCORDION`, `NOTICE_LIST`의 폼 작성, 순서 조정, 저장 전 preview.

- [ ] **Step 1: 새 block 추가·저장·발행 request를 assert하는 focused E2E를 작성한다.**

fixture는 gallery image UUID와 새 block 문서를 읽고 저장한다. UI에서 gallery, 사양 표, FAQ를 추가하고 `PUT` body를 확인하며 preview에서는 고객과 같은 순서·이미지·표·안내를 확인한다.

- [ ] **Step 2: 기존 편집기에 block type별 폼을 추가한다.**

block 추가 메뉴에 다섯 유형을 표시한다. gallery는 공통 `MediaField`를 각 item에 사용하고 이미지 2~12장, 다른 목록은 설계 상한을 UI에도 적용한다. block 이동·삭제는 HERO를 넘거나 첫 위치를 바꾸지 못하게 한다.

- [ ] **Step 3: preview dialog에 다섯 block을 읽기 전용으로 렌더링한다.**

CTA는 이동하지 않으며, 저장·발행·보관·복원 요청을 만들지 않는다. gallery image는 customer origin의 검증된 URL만 사용한다.

- [ ] **Step 4: focused 관리자 검증을 통과시킨다.**

```powershell
# SDTPL_ADM
pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "rich content blocks"
pnpm.cmd exec tsc --noEmit
```

### Task 4: 설계·변경 기록·runtime 확인

**Files:**
- Modify: `docs/architecture/lotte-resort-level-cms-functional-design.md`
- Modify: `docs/architecture/cms-functional-specification.md`
- Modify: `docs/changes/2026-09-11-lotte-resort-cms-redesign.md`
- Modify: `docs/overview/current-development-context.md`

- [ ] **Step 1: 구현한 block 계약, 호환 처리, 검증 결과·미검증 항목을 한국어로 기록한다.**
- [ ] **Step 2: Docker API를 재빌드하고 health와 기존 공개 `/brand/story` resolve를 읽기 전용으로 확인한다.**
- [ ] **Step 3: `git diff --check`를 실행한다.**
