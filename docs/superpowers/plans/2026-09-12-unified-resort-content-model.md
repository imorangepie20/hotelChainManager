# 통합 리조트 콘텐츠 모델 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 현재 공유 트리에서만 실행하며, branch·worktree·commit·reset·clean을 만들지 않는다.

**Goal:** 객실·다이닝·부대시설·경험·프로모션·이용 안내·브랜드를 안전한 초안/발행 CMS 모델로 운영하고 고객 공개 상세·목록, 예약 진입, 관리자 편집·390px 미리보기까지 연결한다.

**Architecture:** `website_page`는 페이지 identity·버전·수명주기 경계로 유지하고, `content_kind`와 상태별 정규화 연결 테이블을 더한다. 구조화 block 문서는 종류별 validator가 검증하며, 저장은 DRAFT 연결만, 발행은 같은 트랜잭션에서 PUBLISHED 연결과 불변 version snapshot을 바꾼다. 고객 웹은 공개 read model만 parser로 다시 검증하고, 객실 CTA는 room type ID만 예약 검색 상태에 전달한 뒤 availability 결과를 다시 필터링한다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, PostgreSQL 16/Flyway, React·TypeScript·Vite, Next.js 16.2.7·React 19.2.4, Playwright Chromium.

**Spec:** `docs/superpowers/specs/2026-09-12-unified-resort-content-model-design.md`

## Global Constraints

- `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`, 기존 `CONTENT_PAGE`, 공개 URL, 초안/발행/version/audit/media usage는 호환 유지한다. 기존 사용자 페이지·자산은 자동 저장·발행·보관·삭제하지 않는다.
- `ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`, `PROMOTION`, `GUIDE`, `BRAND`만 이번 `CONTENT_PAGE.content_kind` 대상이다. 번역·검토/승인·예약 발행·redirect·OG/robots·media variant/파일 교체는 구현하지 않는다.
- 가격·재고·요금제·판매 가능 여부·예약 확정·결제는 Spring 예약 도메인의 소유다. CMS document, 공개 collection, CTA payload에 가격·재고·할인액을 쓰거나 예약 요청으로 보내지 않는다.
- 외부 URL·HTML·script·Markdown·data URL·상위 경로는 저장·공개·렌더링하지 않는다. 이미지는 asset UUID 및 검증된 내부 전달 경로, CTA는 내부 경로만 허용한다.
- 새 block은 `blockId` UUID를 가진다. 과거 snapshot JSON은 바꾸지 않고, 누락된 과거 block ID는 `pageId + publishedVersion + ordinal`에서 결정적으로 계산해 응답에만 붙인다. 이후 초안 저장에서만 실제 block ID를 쓴다.
- `ROOM`은 상태별 room type 하나, `PROMOTION`은 상태별 대상 지점 하나 이상을 요구한다. page/room type/target hotel/related page의 지점 범위와 lifecycle을 저장과 발행에서 모두 재검증한다.
- 모든 변경은 HQ 권한·기존 optimistic version을 요구한다. 저장·발행·보관·복원·삭제는 관계, media usage, version snapshot, audit을 한 DB transaction으로 유지한다.
- 관리자 UI는 기존 공통 컴포넌트를 사용하고 키보드 가능한 label·오류 복구·저장 전 데스크톱/390px preview를 제공한다. 고객 갤러리는 키보드·스크린 리더·모바일 swipe를 유지한다.
- 현재 공유 작업 트리에서만 작업하고, commit·branch·worktree·기존 변경 정리를 하지 않는다. 변경 영역의 focused test만 실행한다.

## File Structure

| 경로 | 책임 |
| --- | --- |
| `services/api/src/main/resources/db/migration/V16__unified_resort_content_model.sql` | page kind, 상태별 관계 테이블, 기존 페이지 kind backfill, snapshot/audit constraint 확장 |
| `services/api/src/main/java/team/hotelchain/webcontent/ContentKind.java` | page type별 허용 kind·지점 소유·필수 block을 표현하는 단일 enum |
| `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageConnections.java` | 관리자/공개 문서가 공유하는 room type·target hotel·related page 연결 계약 |
| `services/api/src/main/java/team/hotelchain/webcontent/ContentReferenceCatalog.java` | 관리자 선택기에만 주는 활성 지점, room type, 페이지 후보 read model |
| `services/api/src/main/java/team/hotelchain/webcontent/ContentPageValidator.java` | kind별 block allowlist, `blockId`, 길이·수량·안전 URL 계약 |
| `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java` | create/save/publish/lifecycle, 관계 복사, 공개 resolve·collection read model |
| `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java` | 확장된 관리자 page 및 content-reference endpoint |
| `services/api/src/main/java/team/hotelchain/webcontent/PublicWebsitePageController.java` | 공개 resolve와 collection endpoint |
| `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java` | migration 이후 권한·정합성·snapshot·공개 목록의 서버 통합 검증 |
| `apps/web/src/lib/content-page.ts` | 공개 상세/카드/연결 DTO를 다시 검증하는 TypeScript parser |
| `apps/web/src/lib/content-page.test.ts` | kind별 block·card·CTA·unsafe input parser 테스트 |
| `apps/web/src/components/content-page.tsx` | kind-aware 상세 block renderer와 예약 CTA action |
| `apps/web/src/components/content-collection-page.tsx` | 안전한 목록 카드 및 빈 목록 상태 |
| `apps/web/src/main.tsx` | collection route와 상세 resolve result를 현재 고객 예약 상태에 연결 |
| `apps/web/src/styles.css` | 목록, 운영 시간, 위치, promotion summary, 390px responsive style |
| `SDTPL_ADM/src/app/(dashboard)/dashboard/website/page.tsx` | 서버 DTO/API 호출, 선택 reference state, editor props 연결 |
| `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx` | kind 선택, 관계 picker, 유형별 block form, save payload |
| `SDTPL_ADM/src/components/hotel-admin/content-page-preview-dialog.tsx` | 모든 새 block의 read-only desktop/390px preview |
| `SDTPL_ADM/src/components/hotel-admin/content-page-create-dialog.tsx` | 부모·kind·소유 지점을 먼저 선택하는 생성 dialog |
| `SDTPL_ADM/e2e/website-content-editor.spec.ts` | 생성, 선택 제한, 저장/발행 payload, preview와 오류 복구 E2E |
| `docs/changes/2026-09-12-unified-resort-content-model.md` | 변경 이유, migration, focused 검증, runtime 확인, 미검증 항목 |
| `docs/overview/current-development-context.md` | 실제 완료 범위와 후속 범위 요약 |

## Shared Contracts

```java
public enum ContentKind {
    HOME, DESTINATION, ROOM, DINING, FACILITY, EXPERIENCE, PROMOTION, GUIDE, BRAND;

    boolean requiresHotel();
    boolean allowsHotel();
    Set<String> allowedBlockTypes();
    Set<String> requiredBlockTypes();
}

public record WebsitePageConnections(
        List<UUID> roomTypeIds,
        List<UUID> targetHotelIds,
        List<WebsitePageRelation> relatedPages) { }

public record WebsitePageRelation(UUID targetPageId, String relationType, int displayOrder) { }

public record WebsitePageDocument(
        UUID id, String pageType, ContentKind contentKind, UUID hotelId,
        Map<String, Object> draftContent, WebsitePageConnections draftConnections, int draftVersion,
        WebsitePageMetadata draftMetadata, Map<String, Object> publishedContent,
        WebsitePageConnections publishedConnections, int publishedVersion,
        WebsitePageMetadata publishedMetadata, String lifecycleStatus, int lifecycleVersion) { }

public record PublishedWebsitePage(
        UUID id, String type, ContentKind contentKind, String path, UUID hotelId,
        Map<String, Object> content, WebsitePageConnections connections) { }
```

```ts
export type ContentKind = 'HOME' | 'DESTINATION' | 'ROOM' | 'DINING' | 'FACILITY' | 'EXPERIENCE' | 'PROMOTION' | 'GUIDE' | 'BRAND'
export type ContentPageConnection = {
  roomTypeIds: string[]
  targetHotelIds: string[]
  relatedPages: Array<{ targetPageId: string; relationType: 'RELATED' | 'MANUAL_CARD'; displayOrder: number }>
}
export type BookingIntent = { hotelId: string; roomTypeId?: string }
```

`TEXT`와 기존 `CTA`는 기존 페이지 호환 block으로 유지한다. 새 kind의 서술 block은 `RICH_TEXT`, 예약 intent block은 `BOOKING_CTA`이며 아래 형태만 허용한다.

```json
{
  "blockId": "00000000-0000-0000-0000-000000000000",
  "type": "BOOKING_CTA",
  "eyebrow": "예약 안내",
  "title": "숙박을 계획해 보세요",
  "description": "날짜와 인원을 선택하면 실시간 객실을 확인합니다.",
  "label": "객실 검색",
  "hotelId": "00000000-0000-0000-0000-000000000000",
  "roomTypeId": "00000000-0000-0000-0000-000000000000"
}
```

### Task 1: Flyway 모델과 legacy 읽기 호환 기반

**Files:**
- Create: `services/api/src/main/resources/db/migration/V16__unified_resort_content_model.sql`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/ContentKind.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageConnections.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageRelation.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageDocument.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/PublishedWebsitePage.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Consumes:** 기존 `website_page`, `website_page_version`, `website_page_audit`, `room_type`, `hotel` 및 current `CONTENT_PAGE` lifecycle 규칙.

**Produces:** nullable `content_kind`, DRAFT/PUBLISHED 상태별 `website_page_room_type`, `website_page_hotel`, `website_page_relation`, kind 및 연결을 담는 API records.

- [x] **Step 1: 새 schema와 legacy 응답 계약을 고정하는 실패 통합 테스트를 작성한다.**

  `ROOM`을 만들기 전 기존 `/brand/story`와 `/brand/forest-gallery-demo` resolve가 `BRAND` kind로 보이고 기존 block에는 결정적인 `blockId`가 읽기 응답에만 존재하는지를 assert한다. 테스트 fixture에는 설악산 room type UUID를 조회하는 helper를 두고, `WebsitePageDocument.draftConnections()`가 legacy brand에서는 빈 배열임을 assert한다.

  ```java
  assertThat(publicPage.contentKind()).isEqualTo(ContentKind.BRAND);
  assertThat(firstBlock(publicPage.content()).get("blockId")).isInstanceOf(String.class);
  assertThat(staffPage.draftConnections()).isEqualTo(new WebsitePageConnections(List.of(), List.of(), List.of()));
  ```

- [x] **Step 2: focused 통합 테스트가 현재 kind/connection 필드 부재로 실패하는지 확인한다.**

  Run:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#returnsLegacyKindsBlockIdsAndEmptyConnectionsWithoutChangingPageContent' test
  ```

  Expected: compile failure because `ContentKind` 및 확장 document fields가 없다.

- [x] **Step 3: V16 migration을 작성한다.**

  `website_page.content_kind VARCHAR(20)`을 추가해 page type별 check를 둔다. `HOME_PAGE→HOME`, `HOTEL_LANDING→DESTINATION`, 기존 `CONTENT_PAGE→BRAND`를 update하되 draft/published JSON과 version rows는 update하지 않는다. 다음 세 관계 table은 `document_state IN ('DRAFT','PUBLISHED')`, non-negative `display_order`, unique `(page_id, document_state, target)` index와 `ON DELETE RESTRICT` foreign key를 가진다.

  ```sql
  CREATE TABLE website_page_room_type (
      page_id UUID NOT NULL REFERENCES website_page(id) ON DELETE RESTRICT,
      document_state VARCHAR(12) NOT NULL CHECK (document_state IN ('DRAFT', 'PUBLISHED')),
      room_type_id UUID NOT NULL REFERENCES room_type(id) ON DELETE RESTRICT,
      PRIMARY KEY (page_id, document_state, room_type_id)
  );
  ```

  `website_page_hotel`과 `website_page_relation`도 동일한 state key를 사용하고, relation은 `relation_type IN ('RELATED','MANUAL_CARD')` 및 `(page_id, document_state, target_page_id)` unique를 둔다. audit constraint에는 새 action을 추가하지 않는다.

- [x] **Step 4: enum, immutable connection records, document/resolve record mapping을 최소로 구현한다.**

  `ContentKind.requiresHotel()`은 `ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`만 true, `allowsHotel()`은 그 네 종류와 `GUIDE`, `DESTINATION`만 true로 고정한다. 이전 public document mapper는 `contentKind == null`이면 page type에서 legacy kind를 결정하고, block ID는 JSON을 DB에 저장하지 않는 copy에서만 보강한다.

  ```java
  static ContentKind legacyKind(String pageType) {
      return switch (pageType) {
          case "HOME_PAGE" -> ContentKind.HOME;
          case "HOTEL_LANDING" -> ContentKind.DESTINATION;
          default -> ContentKind.BRAND;
      };
  }
  ```

- [x] **Step 5: Task 1 테스트를 통과시키고 Flyway migration이 새 개발 DB에서 적용되는지 확인한다.**

  Run the Step 2 command again. Expected: one passing legacy compatibility test.

### Task 2: kind별 block 및 연결 검증기

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/ContentPageValidator.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteMediaReferenceService.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageConnectionValidator.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Consumes:** `ContentKind`, 안정적인 block IDs, 정규화된 media delivery URL, `room_type(hotel_id)`, active `website_page` rows.

**Produces:** `validate(ContentKind kind, Map<String,Object> content)` 및 `validateDraftConnections(ContentKind kind, UUID hotelId, WebsitePageConnections connections)`; publish에서도 재사용 가능한 `validatePublishedConnections`.

- [x] **Step 1: 각 kind의 최소 문서와 거부 규칙을 assert하는 실패 테스트를 추가한다.**

  `WebsitePageIntegrationTest`에 실제 Spring `ContentPageValidator`와 새 `WebsitePageConnectionValidator`를 주입한다. 유효한 `ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`, `PROMOTION`, `GUIDE`, `BRAND` 문서를 직접 검사하고, 별도 assertions로 다음을 고정한다: ROOM에 `SPEC_TABLE`/`BOOKING_CTA` 누락, PROMOTION target hotel 누락, PROMOTION의 타 지점 room type, DINING의 `OPERATING_HOURS` 누락, unknown block, duplicate blockId, 외부 CTA/image. page ID가 필요한 self/순환 relation은 Task 3의 저장 경로 통합 테스트에서 검사한다.

  ```java
  assertThatThrownBy(() -> contentPages.validate(ContentKind.ROOM, roomDocumentWithoutSpec))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("ROOM").hasMessageContaining("SPEC_TABLE");
  ```

- [x] **Step 2: new contract가 old validator에 거부되는지 focused 테스트를 실행한다.**

  Run:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#validatesKindsBlocksAndConnectionScopes' test
  ```

  Expected: compile failure because typed `validate`, `WebsitePageConnectionValidator`, and new block contracts do not exist.

- [x] **Step 3: validator를 kind-aware contract로 확장한다.**

  모든 새 block에는 `blockId` UUID를 요구한다. 기존 `HERO`, `TEXT`, `CTA`, 기존 다섯 rich block은 legacy `BRAND` 문서에서 missing `blockId`를 읽기만 허용하며, save normalizer가 UUID를 삽입한 후 validator는 이를 요구한다. 추가 block field는 아래 allowlist만 사용한다.

  ```java
  private static final Set<String> OPERATING_HOURS_FIELDS =
      Set.of("blockId", "type", "title", "description", "entries", "exceptions", "location", "phone");
  private static final Set<String> BOOKING_CTA_FIELDS =
      Set.of("blockId", "type", "eyebrow", "title", "description", "label", "hotelId", "roomTypeId");
  ```

  `OPERATING_HOURS.entries`는 `dayLabel`, `opensAt`, `closesAt`, `closed`를 가지며 시간은 `HH:mm`, 배열은 1~7이다. `LOCATION`은 `address`, 선택 `directions`, 선택 내부 `mapHref`만, `PROMOTION_SUMMARY`는 기간·혜택·선택 마케팅 표시 문구만, `RELATED_COLLECTION`은 `kind`, 선택 `targetHotelId`, `maxItems 1..12`만 허용한다. `BOOKING_CTA`는 label과 target IDs만 가지며 href·price 필드를 허용하지 않는다.

- [x] **Step 4: block ID 보강과 media usage path를 구현한다.**

  `WebsiteMediaReferenceService.normalizeStructuredContent(content, pageId, publishedVersion)`가 deep copy에 누락된 current-draft `blockId`를 `UUID.randomUUID()`로 넣고, 읽기 전 legacy snapshot에는 deterministic UUID를 copy에만 넣는다. HERO/galleries의 existing usage path는 유지하고, 새 gallery item도 `blocks[{index}].items[{itemIndex}].imageAssetId`에 기록한다.

- [x] **Step 5: 관계 검증기를 구현한다.**

  page create/save에서 target row를 lock/read해 ROOM은 정확히 하나인 own hotel room type, PROMOTION은 하나 이상 target hotel 및 optional room types가 그 대상 집합에 속함을 확인한다. `relatedPages`는 자기 자신, duplicate, ARCHIVED target을 거부하고 directed DFS로 `RELATED`/`MANUAL_CARD` edge의 cycle을 거부한다. publish 직전 동일 검증을 PUBLISHED target set으로 다시 수행한다.

- [x] **Step 6: Task 2 focused test를 통과시킨다.**

  Run the Step 2 command. Expected: valid drafts pass; every invalid block/relationship response is rejected before a page, usage, or audit row changes.

### Task 3: 관리자 write path, snapshot 및 lifecycle의 관계 정합성

**Files:**
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/CreateWebsitePageRequest.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/SaveWebsitePageRequest.java`
- Create: `services/api/src/main/java/team/hotelchain/webcontent/ContentReferenceCatalog.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageManagementController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Consumes:** Task 1 records/table schema and Task 2 document/connection validation.

**Produces:** atomic page create/save/publish/archive/restore/delete with `contentKind`/`connections`, plus `GET /api/staff/website/content-reference`.

- [x] **Step 1: 관계가 포함된 저장/발행 lifecycle의 실패 통합 테스트를 작성한다.**

  `ROOM` draft에서 room type A를 B로 저장한 뒤 공개 resolve는 발행 전 A를 계속 반환하는지, publish 후 B만 반환하는지, published version snapshot에 `contentKind`와 `connections`가 남는지를 assert한다. 이어 archive가 PUBLISHED relation을 지우고 restore가 DRAFT relation만 유지하며, delete가 두 상태 relation row를 version/audit/media usage와 함께 제거하는지를 assert한다.

  ```java
  assertThat(resolve(roomPath).connections().roomTypeIds()).containsExactly(roomTypeA);
  pages.publishPage(hqToken, roomId, saved.draftVersion(), before.publishedVersion());
  assertThat(resolve(roomPath).connections().roomTypeIds()).containsExactly(roomTypeB);
  ```

- [x] **Step 2: lifecycle 테스트가 request/response 계약 부재로 실패하는지 확인한다.**

  Run:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#keepsDraftAndPublishedConnectionsIsolatedAcrossLifecycle' test
  ```

  Expected: compile failure because create/save requests have neither `contentKind` nor `connections`.

- [x] **Step 3: request records와 page creation rules를 확장한다.**

  `CreateWebsitePageRequest(UUID parentId, ContentKind contentKind, UUID hotelId, WebsitePageDraftMetadata page, Map<String,Object> content, WebsitePageConnections connections)`와 save counterpart를 사용한다. page type은 create endpoint에서 항상 `CONTENT_PAGE`; parent는 `SECTION`; ROOM/DINING/FACILITY/EXPERIENCE는 same-hotel section, PROMOTION/BRAND는 chain section, GUIDE는 chain 또는 same-hotel section만 허용한다. `childPath` 결과는 4 segment 이하인지 확인한다.

- [x] **Step 4: service가 한 transaction에서 content와 관계를 쓴다.**

  create는 page row/audit/DRAFT media usage/DRAFT connections를 모두 쓰고, save는 expected draft version update 성공 뒤에만 DRAFT relations replace 및 usage synchronize를 수행한다. publish는 draft validation 후 `replaceConnections(pageId, "PUBLISHED", draftConnections)`, media published synchronize, `snapshot(page, publishedConnections)` insert 순서로 실행한다. `snapshot`에는 `contentKind`와 serializable `connections`를 넣는다.

  ```java
  private void replaceConnections(UUID pageId, String state, WebsitePageConnections next) {
      jdbc.update("delete from website_page_relation where page_id = ? and document_state = ?", pageId, state);
      jdbc.update("delete from website_page_room_type where page_id = ? and document_state = ?", pageId, state);
      jdbc.update("delete from website_page_hotel where page_id = ? and document_state = ?", pageId, state);
      // validate first, then insert each ordered relation and ID.
  }
  ```

  archive must delete only PUBLISHED relation rows with the existing published document/usage clearing; restore never recreates PUBLISHED relation rows; permanent delete removes all three relation tables before version/audit/page deletes. version-draft restore reads historical `connections` into DRAFT after validating them; pre-V16 snapshots fall back to empty legacy connections.

- [x] **Step 5: add the staff reference catalog endpoint without reservation values.**

  `contentReference(token)` requires headquarters and returns active hotels, their `room_type.id/name/max_occupancy`, and active published candidate pages with ID/path/title/content kind/hotel only. It must not select `rate_plan`, `daily_inventory`, price, or availability. The controller exposes it at `GET /api/staff/website/content-reference`.

- [x] **Step 6: run lifecycle test plus the existing page integration class.**

  Run:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest' test
  ```

  Expected: all existing page/lifecycle/media tests and the new relation isolation test pass.

### Task 4: 공개 resolve와 collection read model

**Files:**
- Create: `services/api/src/main/java/team/hotelchain/webcontent/WebsiteContentCollectionItem.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/WebsitePageService.java`
- Modify: `services/api/src/main/java/team/hotelchain/webcontent/PublicWebsitePageController.java`
- Test: `services/api/src/test/java/team/hotelchain/webcontent/WebsitePageIntegrationTest.java`

**Consumes:** PUBLISHED page/connection state from Task 3.

**Produces:** `GET /api/website/pages/resolve?path=` with content kind/connections and `GET /api/website/collections?hotelSlug=&kind=` card-only response.

- [x] **Step 1: public filtering tests first.**

  Seed/publish one item for every new kind. Assert resolve returns only PUBLISHED `connections`, collection returns cards with `id`, `contentKind`, `path`, `title`, `summary`, `image`, `hotelSlug`, then archive one item and save another as draft. Assert both disappear from collection/resolve. Assert invalid kind, unknown hotel slug, blank hotelSlug for hotel-scoped kind are 400/404; public response JSON has no `draft`, `version`, staff ID, room price, inventory, or audit keys.

- [x] **Step 2: run the new collection test and confirm the endpoint is absent.**

  Run:

  ```powershell
  Set-Location services/api
  & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#servesOnlyPublishedTypedCardsAndConnections' test
  ```

  Expected: FAIL because `/api/website/collections` does not exist.

- [x] **Step 3: make resolve construct `PublishedWebsitePage` from published connections only.**

  Extend the current single-page SQL read with `content_kind`; then load connection tables using `document_state='PUBLISHED'`. Return `ContentKind.legacyKind(pageType)` when DB data is null. `resolvePublished` keeps path regex and ACTIVE/published-document filters unchanged.

- [x] **Step 4: implement a card-only collection query.**

  Resolve `hotelSlug` through active `hotel.slug`, require it for ROOM/DINING/FACILITY/EXPERIENCE, allow omitted slug only for PROMOTION/GUIDE/BRAND. Select no full JSON gallery or version history; extract only HERO title/description/validated image from published content and include canonical path. For a hotel-scoped collection, include own-hotel pages and PROMOTION rows whose PUBLISHED `website_page_hotel` has that hotel.

- [x] **Step 5: rerun the Task 4 test and execute the complete focused server class.**

  Run the Step 2 command followed by the Task 3 Step 6 command. Expected: collection excludes drafts/archives and current page tests remain green.

### Task 5: 고객 parser와 typed booking intent

**Files:**
- Modify: `apps/web/src/lib/content-page.ts`
- Modify: `apps/web/src/lib/content-page.test.ts`
- Modify: `apps/web/src/lib/latest-availability-request.ts`
- Create: `apps/web/src/lib/content-collection.ts`
- Create: `apps/web/src/lib/content-collection.test.ts`

**Consumes:** Task 4 public DTOs and existing availability response whose offer includes `roomTypeId`.

**Produces:** `parseContentPage`, `parseContentCollection`, and `applyBookingIntent(intent, currentSearch)` that contains no price/inventory data.

- [x] **Step 1: write failing TypeScript parser tests for every added contract.**

  Assert valid `RICH_TEXT`, `OPERATING_HOURS`, `LOCATION`, `PROMOTION_SUMMARY`, `RELATED_COLLECTION`, `BOOKING_CTA` parse for their allowed kind; assert each fails for wrong kind, duplicate/missing block ID, unknown field, external/map URL, malformed time, invalid UUID, PRICE field, and room CTA not matching the page hotel/connection. Assert card parser rejects draft-only fields and an invalid image path.

  ```ts
  assert.equal(parseContentPage(roomPayload)?.blocks.at(-1)?.type, 'BOOKING_CTA')
  assert.equal(parseContentPage({ ...roomPayload, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] } }), null)
  assert.equal(parseContentCollection([{ path: '/offers/autumn', price: 1000 }]), null)
  ```

- [x] **Step 2: run parser tests and verify they fail before implementation.**

  Run:

  ```powershell
  Set-Location apps/web
  node --experimental-strip-types src/lib/content-page.test.ts
  node --experimental-strip-types src/lib/content-collection.test.ts
  ```

  Expected: failures because typed blocks, card parser, and booking intent helper are absent.

- [x] **Step 3: implement narrow public DTO unions and validators.**

  Keep the existing `TEXT`, `CTA`, gallery/feature/spec/accordion/notice parsing paths. Add kind-aware union members and exact field allowlists. `safeMediaDeliveryPath` remains the only image path gate. `parseBookingCta` returns `{ hotelId, roomTypeId? }` only after matching the resolve `hotelId` or target hotel IDs and room connection IDs.

  ```ts
  export function applyBookingIntent(intent: BookingIntent, current: SearchState): SearchState {
    return { ...current, hotelId: intent.hotelId, roomTypeId: intent.roomTypeId }
  }
  ```

  Do not introduce `price`, `discount`, `availability`, `ratePlan`, or a booking API call in this helper.

- [x] **Step 4: parse the public card read model separately.**

  `parseContentCollection(value)` accepts only array items with a safe local image, canonical local path, approved content kind, title/summary length limits, optional `hotelSlug`, and no internal IDs other than opaque public page ID. It returns `null` if any server item has an unsafe shape; the page then uses its explicit error/empty state.

- [x] **Step 5: rerun both parser suites and typecheck.**

  Run:

  ```powershell
  Set-Location apps/web
  node --experimental-strip-types src/lib/content-page.test.ts
  node --experimental-strip-types src/lib/content-collection.test.ts
  pnpm.cmd exec tsc -b
  ```

  Expected: all focused parser tests and TypeScript build pass.

### Task 6: 고객 상세·목록 렌더러와 예약 재조회 연결

**Files:**
- Modify: `apps/web/src/components/content-page.tsx`
- Create: `apps/web/src/components/content-collection-page.tsx`
- Modify: `apps/web/src/App.tsx`
- Modify: `apps/web/src/styles.css`
- Test: `apps/web/src/lib/latest-availability-request.test.ts`

**Consumes:** parsed page/card union and `BookingIntent` from Task 5; current customer search/availability flow.

**Produces:** kind label/semantic detailed sections, list→detail navigation, a booking action that reapplies hotel/room type and filters freshly fetched availability offers by `roomTypeId`.

- [x] **Step 1: write failing availability-request tests for the reservation boundary.**

  Add a current search with a ROOM intent, call the request builder, and assert it sends existing hotel/date/occupants/rooms fields only; it must not send `roomTypeId`, CMS price, discount, or content page ID. After a valid availability response, assert `filterOffersForRoomType(offers, roomTypeId)` returns only matching offer IDs and no filter is applied when intent has no room type.

  ```ts
  assert.deepEqual(requestBody, { hotelId, checkIn, checkOut, adults: 2, children: 0, rooms: 1 })
  assert.deepEqual(filterOffersForRoomType(offers, roomTypeId).map((offer) => offer.roomTypeId), [roomTypeId])
  ```

- [x] **Step 2: run the focused test before UI changes.**

  Run:

  ```powershell
  Set-Location apps/web
  node --experimental-strip-types src/lib/latest-availability-request.test.ts
  ```

  Expected: FAIL because the request helper has no booking intent/offer filter functions.

- [x] **Step 3: implement the smallest booking intent integration.**

  Keep the existing availability call unchanged. Store optional selected room type in customer search UI state, reset it on a hotel change, and filter only the fresh API response after `latest-availability-request` rejects stale responses. A ROOM `BOOKING_CTA` scrolls/focuses the existing search form and applies hotel/room type; PROMOTION applies only a target hotel; DINING/FACILITY external reservations are not rendered.

- [x] **Step 4: render all typed blocks with semantic HTML.**

  `ContentPage` renders `RICH_TEXT` as heading/paragraph section, `OPERATING_HOURS` with a table and exception note, `LOCATION` with address/directions and safe internal link, `PROMOTION_SUMMARY` with non-authoritative marketing label, `RELATED_COLLECTION` as cards, and `BOOKING_CTA` as a button. Reuse the existing gallery swipe and accordion instead of duplicating interaction logic. Include page `contentKind` in an accessible visually small category label.

- [x] **Step 5: add collection routing and responsive empty/error states.**

  `content-collection-page.tsx` fetches only `/api/website/collections` then calls `parseContentCollection`. Wire `/stays/:hotelSlug/rooms|dining|facilities|experiences`, `/offers`, `/guides`, `/brand` to it before falling through to detail resolve. Empty collections show the selected hotel name and a safe `/stays/{slug}` link, never a broken grid. Use the existing typography and animation language; at 390px no list/grid/table may horizontally overflow.

- [ ] **Step 6: pass focused tests, TypeScript, and customer production build.**

  Run:

  ```powershell
  Set-Location apps/web
  node --experimental-strip-types src/lib/latest-availability-request.test.ts
  node --experimental-strip-types src/lib/content-page.test.ts
  node --experimental-strip-types src/lib/content-collection.test.ts
  pnpm.cmd exec tsc -b
  pnpm.cmd build
  ```

  Expected: all focused tests pass and build has no route/type error.

### Task 7: 관리자 DTO와 create/reference selection flow

**Files:**
- Create: `SDTPL_ADM/src/components/hotel-admin/content-page-create-dialog.tsx`
- Modify: `SDTPL_ADM/src/app/(dashboard)/dashboard/website/page.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/website-page-tree.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Test: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Consumes:** Task 3 staff document and `content-reference` response.

**Produces:** kind-aware create payload and selected `ContentReferenceCatalog` passed to one editor instance.

- [x] **Step 1: add failing Chromium E2E for kind and scope selection.**

  Mock/reference fixture includes 설악산/제주, one room type each, a chain promo section and a 설악산 rooms section. Open “페이지 추가”, choose ROOM, assert chain section is unavailable, choose 설악산 section, assert only 설악산 room type appears. Choose PROMOTION and assert at least one target hotel is mandatory before create. Intercept POST body and assert it contains `contentKind`, `hotelId`, `connections`, metadata/content; assert it lacks price/inventory.

- [ ] **Step 2: run the E2E to establish the missing UI failure.**

  Run:

  ```powershell
  Set-Location SDTPL_ADM
  pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "creates typed pages with scoped references"
  ```

  Expected: FAIL because the current create dialog only creates generic pages.

- [x] **Step 3: fetch and type the reference catalog once per dashboard session.**

  In the website dashboard, add `ContentReferenceCatalog` and expanded `WebsitePageDocument` TypeScript types. On authenticated CMS load, fetch `/api/staff/website/content-reference` alongside the tree. Render an inline retry message when it fails; do not substitute hard-coded room IDs. Pass catalog to creation/editor components.

- [x] **Step 4: replace the generic create form with `ContentPageCreateDialog`.**

  The dialog order is parent SECTION → kind → owner hotel/targets → slug/menu. Filter parents by kind’s allowed scope, clear dependent IDs when kind/parent changes, and disable submit with field-specific Korean guidance until all required selections exist. Keep `SECTION`, home, landing, existing tree selection, Escape, and focus return behavior unchanged.

- [x] **Step 5: rerun the focused E2E and TypeScript check.**

  Run the Step 2 command, then:

  ```powershell
  pnpm.cmd exec tsc --noEmit
  ```

  Expected: create payload is scoped and no existing dashboard type errors occur.

### Task 8: 관리자 유형별 편집·연결·미리보기

**Files:**
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-editor.tsx`
- Modify: `SDTPL_ADM/src/components/hotel-admin/content-page-preview-dialog.tsx`
- Modify: `SDTPL_ADM/e2e/website-content-editor.spec.ts`

**Consumes:** Task 7 selected catalog and expanded document; Task 2 block contract.

**Produces:** type-specific block add controls, relation pickers, valid PUT/publish payloads, desktop/390px read-only preview.

- [ ] **Step 1: write failing E2E for a ROOM and a PROMOTION edit.**

  For ROOM, change the selected room type, add a `SPEC_TABLE` row and `BOOKING_CTA`, open mobile preview, intercept PUT/publish and assert `connections.roomTypeIds` has one same-hotel ID. For PROMOTION, choose two hotels and one valid room type, add `PROMOTION_SUMMARY`, assert preview shows a marketing display phrase but no real-time price; then select a 제주-only room type after deselecting 제주 and assert an actionable inline error and disabled save.

- [ ] **Step 2: run the focused E2E and verify missing controls cause failure.**

  Run:

  ```powershell
  Set-Location SDTPL_ADM
  pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "edits room and promotion connections with typed preview"
  ```

  Expected: FAIL because editor has no kind/connection block controls.

- [ ] **Step 3: extend editor block unions and default blocks.**

  Add `RICH_TEXT`, `OPERATING_HOURS`, `LOCATION`, `PROMOTION_SUMMARY`, `RELATED_COLLECTION`, `BOOKING_CTA` to the `ContentBlock` union. `addBlock` must only expose `document.contentKind` allowlist; required blocks cannot be removed below minimum. Reuse `MediaField` for all image inputs and never add a raw JSON textarea. Render day/time controls with explicit `label`, closed-day checkbox, target hotel select, related-page select, and `aria-describedby` error text.

- [ ] **Step 4: add connection picker and client-side guardrails.**

  ROOM shows exactly one room type filtered to `document.hotelId`; PROMOTION shows one-or-more checkbox target hotels and room type choices from their union; all other kinds show only their permitted optional relations. The UI is advisory: always send the complete `connections` record and leave server validation authoritative. Clearing a target hotel removes incompatible selected room types in the same React state update.

- [ ] **Step 5: render the new blocks in the existing preview dialog.**

  Keep CTA inert. `BOOKING_CTA` displays its label as a disabled preview button; `OPERATING_HOURS` is a table; `LOCATION` is plain address/directions; `PROMOTION_SUMMARY` receives “표시 정보이며 실제 예약 가격은 선택 조건에서 다시 계산됩니다.”; related collection renders supplied candidate-card placeholders without network requests. Preserve `data-preview-viewport`, `aria-pressed`, 390px width, and reduced-motion safe styling from the existing preview.

- [ ] **Step 6: run both new E2E tests and the existing rich-block/mobile-preview tests.**

  Run:

  ```powershell
  Set-Location SDTPL_ADM
  pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "creates typed pages with scoped references|edits room and promotion connections with typed preview|edits rich content blocks and previews them in customer order|switches the content page preview to a 390px mobile frame"
  pnpm.cmd exec tsc --noEmit
  ```

  Expected: all selected tests pass; desktop/mobile preview never emits save, publish, archive, restore, or delete requests.

### Task 9: focused runtime integration and Korean change record

**Files:**
- Create: `docs/changes/2026-09-12-unified-resort-content-model.md`
- Modify: `docs/overview/current-development-context.md`

**Consumes:** focused green server/customer/admin suites from Tasks 1–8.

**Produces:** reproducible implementation record, live API/browser evidence, and an explicit list of unimplemented scope.

- [ ] **Step 1: record the exact migration and verification boundaries before runtime mutation.**

  In the change record, state V16 schema purpose; draft/published relation behavior; legacy snapshot/block-ID preservation; reservation-domain boundary; commands/results; browser routes checked; and that no existing user page/media lifecycle action was sent. List translation, review/scheduling, redirect/SEO, media variants/file replacement as unimplemented.

- [ ] **Step 2: rebuild only the API after focused server tests are green and verify read-only endpoints.**

  Run:

  ```powershell
  docker compose up --build -d api
  Invoke-RestMethod http://127.0.0.1:4080/actuator/health
  Invoke-RestMethod 'http://127.0.0.1:4080/api/website/pages/resolve?path=/brand/story'
  ```

  Expected: health `UP`; the pre-existing brand page remains public with its canonical path. Do not call staff save/publish/archive/delete endpoints against user data.

- [ ] **Step 3: browser-check isolated development examples.**

  Use only a dedicated, newly created test fixture page through the existing test/dev setup. In customer browser verify one ROOM list→detail path, one PROMOTION list→detail path, gallery keyboard/swipe, room CTA focus to reservation form, fresh availability result filtered to selected `roomTypeId`, and 390×844 no horizontal overflow. In admin verify type creation, invalid cross-hotel choice error, desktop/390px preview, and no preview write request.

- [ ] **Step 4: update current development context and run whitespace validation.**

  Add only completed facts and the focused test counts to `current-development-context.md`; preserve older history. Run:

  ```powershell
  git diff --check
  ```

  Expected: no whitespace errors. Do not stage, commit, reset, clean, or alter unrelated working-tree files.

## Plan Self-Review

### Spec coverage

- All seven new detail kinds, compatibility kinds, direct SECTION parent/scope, 4-segment path validation: Tasks 1–3.
- DRAFT/PUBLISHED room type/hotel/related-page relations, transactional publish/lifecycle/delete, immutable snapshot: Task 3.
- Stable block ID plus legacy snapshot preservation: Tasks 1–2.
- full minimal block set and safe media/internal CTA contract: Task 2, parser Task 5, renderer Task 6, editor Task 8.
- staff reference API with no price/inventory and type/scope admin workflow: Tasks 3, 7, 8.
- public resolve and card-only collection excluding drafts/archives: Task 4.
- ROOM/PROMOTION reservation boundary and real availability filtering: Tasks 5–6.
- server/parser/admin/customer/runtime documentation verification: Tasks 2–9.
- Explicitly excluded translation, review/schedule, redirects/SEO, media variants/replacement: Global Constraints and Task 9.

### Placeholder scan

미완성 표기, 모호한 오류 처리 지시, 다른 작업을 참조만 하는 단계가 없는지 검사했다. 각 task에는 대상 파일, 소비/산출 계약, 실패 확인 명령, 최소 구현 방향, 통과 명령을 명시했다.

### Type consistency

The Java request/document/connection names used in Tasks 3–4 match the shared contracts. The TypeScript `ContentKind`, `ContentPageConnection`, and `BookingIntent` names used in Tasks 5–8 match those contracts. `roomTypeId` occurs only as persisted CMS connection or client-side availability-result filter, never in the availability request payload.
