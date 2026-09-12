# CMS 언어별 초안·발행 구현 계획

> 실행 방식: 현재 작업 디렉터리의 사용자 변경을 보존하며 직접 구현한다. 사용자 요청은 기존 구현의 연속이며 커밋·별도 작업 디렉터리 생성은 하지 않는다.

목표: 한국어 호환 경로를 유지하면서 영어 콘텐츠를 별도 저장·발행하고 영어 고객 경로에서 공개한다.

실행 결과: [다국어 변경 기록](../../changes/2026-09-12-cms-locales.md). 서버 통합 55건, 관리자/고객 직접 E2E 13건, 관리자 타입·고객 직접 테스트/build, Docker V20와 한국어 데이터 보존을 확인했다. 임의 언어별 slug와 승인 등 확장 항목은 후속 범위다.

설계: [언어별 설계](../specs/2026-09-12-cms-locales-design.md). 기술: Spring Boot/JdbcTemplate/PostgreSQL, React/TypeScript, 기존 Next 관리자 컴포넌트.

## 작업 1: 서버 번역과 호환 확장

대상: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteTranslationService.java`, `WebsiteTranslationController.java`, `PublicWebsitePageController.java`, `WebsiteMediaReferenceService.java`, `WebsiteMediaService.java`, `WebsitePageService.java`, `db/migration/V20__website_page_translations.sql`, `WebsiteTranslationIntegrationTest.java`.

- [x] MockMvc로 기존 한국어 발행 페이지를 `locale=en` 조회했을 때 404인지 먼저 검증한다. 현 구현의 잘못된 한국어 응답으로 실패하는 것을 확인한다.

```java
mvc.perform(get("/api/website/pages/resolve").param("path", "/brand/locale-story").param("locale", "en"))
    .andExpect(status().isNotFound());
```

- [x] 추가 번역·이력 테이블 및 usage locale을 만들고 ko usage는 기본 ko로 보존한다. 영어 읽기/초안 가져오기/저장/발행/이력과 공개 locale 분기를 구현한다.
- [x] 두 언어를 순차 저장·발행해 ko/en 콘텐츠·버전·usage의 독립성을 실제 DB로 확인한다. 영어 publish stale version·지점 권한·보관 거부 및 archive/restore/delete를 테스트한다.
- [x] `WebsiteTranslationIntegrationTest,WebsitePageIntegrationTest,WebsiteMediaIntegrationTest,WebContentIntegrationTest` 직접 통합 테스트를 실행한다. 기존 한국어 경로 회귀를 포함한다.

## 작업 2: 관리자 언어별 편집

대상: `SDTPL_ADM/src/lib/staff-api.ts`, `website-content-editor.tsx`, 새 `website-translation-editor.tsx`, 기존 `content-page-editor.tsx`와 `e2e/website-content-editor.spec.ts`.

- [x] 영어 선택 후 초안 없음/명시적 가져오기 UI를 Playwright로 먼저 검증해 실패를 확인한다.
- [x] 영어용 로딩·version·history를 독립 컴포넌트에 두고 기존 구조화/랜딩 편집기를 재사용한다. 한국어 구조·수명주기 동작은 유지한다.
- [x] 미저장 언어 전환·초안 저장 전 발행 방지·낮은 viewport·Escape/focus를 검증한다. page/locale의 늦은 응답을 폐기한다.
- [x] 변경 직접 E2E 및 `tsc --noEmit`, 변경 파일 eslint를 실행한다.

## 작업 3: 고객 locale 경로

대상: `apps/web/src/lib/customer-route.ts`, `api.ts`, `App.tsx`, 직접 route/parser 테스트.

- [x] `/en/brand/story`를 영어 route로 구분하는 테스트를 먼저 실행해 실패를 확인한다.
- [x] locale별 API와 고객 링크를 연결하며 미발행 영어 페이지에 한국어 정적/CMS fallback을 사용하지 않는다.
- [x] 직접 테스트·TypeScript·production build와 영어 누락 안내/발행 콘텐츠 화면을 검증한다.

## 작업 4: 기록과 runtime

- [x] Docker API 재빌드에서 V20 적용·health와 기존 ko resolve·빈 en 공개를 읽기 전용으로 확인한다.
- [x] 변경 기록에 결과·미검증·첫 단계 호환 이관과 언어별 slug/전체 예약 UI 번역 후속 범위를 기록하고 현재 개발 상태를 갱신한다.
