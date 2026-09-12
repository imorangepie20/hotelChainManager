# CMS 미디어 카탈로그 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 본사가 안전한 이미지 카탈로그와 Docker 영속 업로드를 이용해 홈·지점·일반 페이지의 히어로를 관리하고 사용 위치를 확인하게 한다.

**Architecture:** `website_media_asset`가 불변 공개 전달 경로와 이미지 메타데이터를 소유한다. CMS 문서는 자산 UUID를 소유하고 서버가 전달 경로를 정규화하며, `website_media_usage`는 초안·발행 문서의 히어로 참조를 같은 트랜잭션에서 기록한다. 업로드 파일은 API Docker named volume에 저장되고 `/api/website/media/{id}/content`에서 공개적으로 전달된다.

**Tech Stack:** Java 21/Spring Boot/JdbcTemplate/Flyway/PostgreSQL, Docker Compose named volume, React/TypeScript/Next 16 관리자, Vite 고객 웹, Playwright, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-11-media-catalog-design.md`

## Global Constraints

- `HQ_ADMIN`만 카탈로그·업로드·사용 위치 관리 API를 사용하며, 가격·재고·예약·AI의 소유권은 바꾸지 않는다.
- 업로드 형식은 PNG/JPEG만, 최대 10 MiB와 24,000,000 픽셀만 허용한다. SVG·원격 URL·data URL·상위 경로는 거부한다.
- V12는 정적 `/images/sokcho-coast-hero.png`를 초기 내장 자산으로 등록한다. 다른 root Gemini 이미지는 등록하지 않는다.
- 문서에는 `imageAssetId`/`heroAssetId`와 서버가 채운 전달 경로를 함께 보존한다. 클라이언트가 제출한 경로는 신뢰하지 않는다.
- 파일 저장소는 Compose `hotel-media-data` volume이며, 오브젝트 스토리지·CDN·삭제·보관·변환·동영상은 범위 밖이다.
- 관련 테스트와 실제 DB/API·UI 상호작용을 변경 범위에 맞게 검증하되 전체 회귀 테스트를 불필요하게 반복하지 않는다.

---

### Task 1: 미디어 저장소·Flyway·서버 계약

**Files:**
- Create: `services/api/src/main/resources/db/migration/V12__website_media_catalog.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaAsset.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaUsage.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaService.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaManagementController.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/PublicWebsiteMediaController.java`
- Modify: `services/api/src/main/resources/application.yml`
- Modify: `services/api/Dockerfile`
- Modify: `compose.yaml`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsiteMediaIntegrationTest.java`

1. [x] Write integration tests for seed catalog visibility, HQ/branch authorization, valid PNG upload, JPEG/PNG detection, invalid format/oversize/pixel rejection, public delivery, and usage query before implementing the service.
2. [x] Add V12 tables, constraints, stable bundled asset metadata, migration of existing page JSON to asset IDs, and DRAFT/PUBLISHED usage backfill without changing historic page-version snapshots.
3. [x] Configure multipart limits and `WEBSITE_MEDIA_STORAGE_DIR`; create an image-owned writable `/app/media` directory and mount `hotel-media-data` at it in Compose.
4. [x] Implement HQ catalog list, multipart upload with ImageIO validation and UUID storage key, public uploaded-file delivery, and usage lookup. Return only delivery URLs that the browser can read without a staff header.
5. [x] Run the focused media integration test and confirm V12 migration·서비스 계약을 검증한다. `WebsiteMediaIntegrationTest`를 포함한 대상 서버 테스트 17건이 통과했다.

### Task 2: 자산 참조 정규화와 사용 위치 동기화

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaReferenceService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/ContentPageValidator.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebContentService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`
- Modify: `services/api/src/test/java/team/hotelchain/webcontent/WebContentIntegrationTest.java`

1. [x] Extend existing test fixtures with the bundled asset UUID and verify missing asset IDs·위조 경로·원격 경로가 새 계약에서 거부된다.
2. [x] Normalize structured HERO and landing hero references through the active asset row before the existing document validators run; keep exact server-delivered paths in persisted documents.
3. [x] Make page creation, landing/home/content save, and publish synchronize DRAFT/PUBLISHED usage only after their optimistic version mutations succeed.
4. [x] Run the focused web-content server tests and verify the older no-asset payloads fail for the new contract rather than silently bypassing catalog validation.

### Task 3: 관리자 미디어 선택·업로드 UX

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/media-field.tsx`
- Create: `SDTPL_ADM/src/components/hotel-admin/media-picker-dialog.tsx`
- Modify: `SDTPL_ADM/src/lib/staff-api.ts`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-content-editor.tsx`
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

1. [x] Add staff API types and calls for catalog list, multipart upload, and usage lookup. Keep multipart separate from JSON `contentRequest` so the browser supplies the boundary.
2. [x] Add a reusable MediaField with 16:9 preview, selected metadata, asset selection dialog, and page-specific required alt input.
3. [x] Implement the dialog’s asset card selection, upload fields, explicit bottom `선택` confirmation, loading/error state, and selected asset usage list using existing Dialog/Card/Button patterns.
4. [x] Replace free-text image-path inputs in the HOME_PAGE/CONTENT_PAGE and HOTEL_LANDING editors. Ensure created content pages use the active bundled asset and current dirty/save/publish guards remain intact.
5. [x] Add compact Playwright coverage for selecting an asset in a structured page, changing a landing asset, upload success/failure state, usage display, and saved JSON asset IDs. TypeScript 검사와 Playwright 7건이 통과했다.

### Task 4: 고객 문서 파서와 이미지 렌더링

**Files:**
- Modify: `apps/web/src/lib/content-page.ts`
- Modify: `apps/web/src/lib/content-page.test.ts`
- Modify: `apps/web/src/lib/destination-content.ts`
- Modify: `apps/web/src/components/content-page.tsx`

1. [x] Update parser tests for required UUID asset references, built-in and API delivery paths, unknown/unsafe URL rejection, and malformed document fallback.
2. [x] Parse server-normalized image references defensively; never accept external or data URLs.
3. [x] Render safe delivery URLs for home/general page and landing hero without changing booking, reservation, or route behavior.
4. [x] Run the content parser test and one production customer build.

### Task 5: 실제 검증과 문서

**Files:**
- Modify: `docs/superpowers/plans/2026-09-11-media-catalog.md`
- Modify: `docs/changes/2026-09-10-web-content-management.md`
- Modify: `docs/overview/current-development-context.md`
- Modify: `docs/architecture/full-site-implementation-design.md`
- Modify: `docs/decisions/decision-log.md`

1. [x] Rebuild API once, confirm Flyway V12 and volume ownership, then read the HQ catalog through the actual administrator picker without exposing a token or password. Live multipart upload is covered by the integration test and was not left as a permanent verification asset in the development catalog.
2. [x] Verify the existing published home resolve contract, catalog usage states, and HQ-only behavior in the targeted integration test. A live upload-backed page was intentionally not saved or published because V12 has no asset deletion or archive operation.
3. [x] Open the customer route and administrator editor in the browser. Confirm the picker, selected metadata, page-specific alt text, usage list, and public bundled image load.
4. [x] Record exact targeted commands/results, actual API/UI behavior, storage limitation, and remaining media work in Korean. Run `git diff --check` before reporting completion.

## 2026-09-11 구현·검증 결과

- 서버 대상 테스트: `WebsiteMediaIntegrationTest`, `WebContentIntegrationTest`, `WebsitePageIntegrationTest` 17건 통과.
- 관리자: `pnpm.cmd exec tsc --noEmit`, `pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium` 7건 통과.
- 고객: `node --experimental-strip-types src/lib/content-page.test.ts`, `pnpm.cmd build` 통과.
- Docker: API 재빌드 뒤 Flyway V12와 두 repeatable migration 적용, `hotel` 사용자로 `/app/media` 쓰기 가능, readiness `UP`을 확인했다.
- 실제 in-app browser의 관리자 CMS에서 자산 1건과 초안·발행 사용 위치 10건을 확인했다. 실제 업로드 결과를 페이지에 발행하지 않은 이유는 V12에 자산 삭제·보관 기능이 없어서 검증용 파일을 운영 카탈로그에 남기지 않기 위해서다.

## Risks and Defenses

- A page could retain an arbitrary `imageSrc` if only structured content changes. Landing and structured documents are normalized through the same media reference service.
- Browser images cannot attach `X-Staff-Session`. Uploaded delivery is public while catalog changes remain HQ-only.
- A Docker image can run as non-root and fail to write a new volume. The Dockerfile creates and owns `/app/media` before switching to the `hotel` user, and Compose mounts the named volume there.
- A failed optimistic update must not leave usage rows lying about. Reference synchronization follows the successful page UPDATE in the same transaction.
- Image uploads need stronger production handling eventually. This scope deliberately excludes file deletion, transforms, virus scanning, object storage, and CDN invalidation.
