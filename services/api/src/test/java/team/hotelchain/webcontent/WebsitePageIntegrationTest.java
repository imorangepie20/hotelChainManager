package team.hotelchain.webcontent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.reservation.BusinessConflictException;

@SpringBootTest
@Transactional
class WebsitePageIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000019");
    private static final UUID SECOND_HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000020");
    private static final UUID BRAND_SECTION = UUID.fromString("12000000-0000-0000-0000-000000000005");
    private static final String BUNDLED_ASSET = WebsiteMediaService.BUNDLED_ASSET_ID.toString();

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebsitePageService pages;
    @Autowired PublicWebsitePageController publicPages;
    @Autowired WebsitePageManagementController pageManagement;
    @Autowired ContentPageValidator contentPages;
    @Autowired WebsitePageConnectionValidator connectionValidator;

    @BeforeEach
    void seed() {
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "페이지 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", SECOND_HOTEL, "두 번째 페이지 테스트 호텔", "제주도", "Asia/Seoul");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_ADMIN')",
                UUID.randomUUID(), "pages-hq@example.com", "페이지 본사 관리자", encoder.encode("hq-password"));
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, 'BRANCH_STAFF', ?)",
                UUID.randomUUID(), "pages-branch@example.com", "페이지 지점 직원", encoder.encode("branch-password"), HOTEL);
    }

    @Test
    void publishesTheLandingPathMenuAndContentAsOneDocument() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument initial = pages.landingDraft(session.token(), HOTEL);

        WebsitePageDraftMetadata metadata = new WebsitePageDraftMetadata("cms-test", "CMS 테스트 호텔", true, 20);
        WebsitePageDocument saved = pages.saveLandingDraft(
                session.token(), HOTEL, initial.draftVersion(), metadata, validContent("페이지 발행 제목"));

        assertThat(saved.draftMetadata().path()).isEqualTo("/stays/cms-test");
        assertThatThrownBy(() -> pages.resolvePublished("/stays/cms-test"))
                .isInstanceOf(WebsitePageNotFoundException.class);

        WebsitePageDocument published = pages.publishLanding(
                session.token(), HOTEL, saved.draftVersion(), saved.publishedVersion());

        PublishedWebsitePage resolved = pages.resolvePublished("/stays/cms-test");
        assertThat(resolved.hotelId()).isEqualTo(HOTEL);
        assertThat(resolved.content()).containsEntry("title", "페이지 발행 제목");
        assertThat(published.publishedMetadata().path()).isEqualTo("/stays/cms-test");
        assertThat(pages.navigation()).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("숙소");
            assertThat(item.children()).anySatisfy(child -> {
                assertThat(child.label()).isEqualTo("CMS 테스트 호텔");
                assertThat(child.path()).isEqualTo("/stays/cms-test");
                assertThat(child.hotelId()).isEqualTo(HOTEL);
            });
        });
        assertThat(pages.staffTree(session.token())).anySatisfy(section -> {
            assertThat(section.label()).isEqualTo("숙소");
            assertThat(section.children()).anySatisfy(landing -> {
                assertThat(landing.hotelId()).isEqualTo(HOTEL);
                assertThat(landing.draftPath()).isEqualTo("/stays/cms-test");
                assertThat(landing.status()).isEqualTo("PUBLISHED");
            });
        });
    }

    @Test
    void exposesOnlyReadyResponsiveVariantsInPublishedAndPreviewResponsesWithoutPersistingThem() throws Exception {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        UUID assetId = UUID.randomUUID();
        String originalUrl = "/api/website/media/" + assetId + "/content";
        jdbc.update("""
                insert into website_media_asset (
                    id, origin, delivery_path, storage_key, display_name, default_alt_text,
                    mime_type, byte_size, width, height, status, version
                ) values (?, 'UPLOADED', ?, ?, '반응형 테스트 이미지', '속초 해안',
                          'image/png', 1000, 1600, 900, 'ACTIVE', 1)
                """, assetId, originalUrl, assetId + ".png");
        jdbc.update("""
                insert into website_media_variant (
                    id, asset_id, format, target_width, status, storage_key,
                    mime_type, byte_size, width, height, attempt_count
                ) values (?, ?, 'WEBP', 640, 'READY', ?, 'image/webp', 500, 640, 360, 1),
                         (?, ?, 'WEBP', 1280, 'PENDING', null, null, null, null, null, 0)
                """, UUID.randomUUID(), assetId, assetId + "-640.webp", UUID.randomUUID(), assetId);

        WebsitePageDocument initial = pages.landingDraft(session.token(), HOTEL);
        Map<String, Object> content = new LinkedHashMap<>(validContent("반응형 이미지 페이지"));
        content.put("heroAssetId", assetId.toString());
        content.put("heroImage", originalUrl);
        WebsitePageDocument saved = pages.saveLandingDraft(session.token(), HOTEL, initial.draftVersion(),
                new WebsitePageDraftMetadata("responsive-media", "반응형 이미지", true, 30), content);
        WebsitePageDocument published = pages.publishLanding(
                session.token(), HOTEL, saved.draftVersion(), saved.publishedVersion());

        JsonNode publicJson = json.valueToTree(pages.resolvePublished("/stays/responsive-media"));
        JsonNode previewJson = json.valueToTree(pages.previewDraft(
                published.id(), published.draftVersion(), published.draftMetadata().path()));
        for (JsonNode response : List.of(publicJson, previewJson)) {
            JsonNode variants = response.path("mediaVariants").path(assetId.toString());
            assertThat(variants).hasSize(1);
            assertThat(variants.get(0).path("targetWidth").asInt()).isEqualTo(640);
            assertThat(variants.get(0).path("deliveryUrl").asText())
                    .isEqualTo("/api/website/media/" + assetId + "/variants/640.webp");
            assertThat(variants.get(0).path("mimeType").asText()).isEqualTo("image/webp");
        }
        assertThat(jdbc.queryForObject("select jsonb_exists(published_content, 'mediaVariants') from website_page where id = ?",
                Boolean.class, published.id())).isFalse();
    }

    @Test
    void createsPublishesAndResolvesAContentPageWithoutHotelData() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");

        WebsitePageDocument created = pages.createContentPage(
                session.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("story", "브랜드 스토리", true, 10), validContentPage("브랜드 스토리"));

        assertThat(created.pageType()).isEqualTo("CONTENT_PAGE");
        assertThat(created.hotelId()).isNull();
        assertThat(created.draftMetadata().path()).isEqualTo("/brand/story");
        assertThat(jdbc.queryForObject("select action from website_page_audit where page_id = ?", String.class, created.id()))
                .isEqualTo("CREATED");
        assertThatThrownBy(() -> pages.resolvePublished("/brand/story"))
                .isInstanceOf(WebsitePageNotFoundException.class);

        WebsitePageDocument published = pages.publishPage(
                session.token(), created.id(), created.draftVersion(), created.publishedVersion());
        PublishedWebsitePage resolved = pages.resolvePublished("/brand/story");

        assertThat(published.publishedMetadata().path()).isEqualTo("/brand/story");
        assertThat(resolved.type()).isEqualTo("CONTENT_PAGE");
        assertThat(resolved.hotelId()).isNull();
        assertThat(pages.pageVersions(session.token(), created.id())).extracting(WebContentVersion::version).containsExactly(2);
        assertThat(pages.navigation()).anySatisfy(section -> {
            assertThat(section.label()).isEqualTo("브랜드");
            assertThat(section.children()).anySatisfy(child -> assertThat(child.path()).isEqualTo("/brand/story"));
        });
    }

    @Test
    void movesUnpublishedPageAfterReadOnlyImpact() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        UUID destination = brandSubsection("collections", "컬렉션");
        WebsitePageDocument created = pages.createContentPage(headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("autumn", "가을 이야기", true, 10), validContentPage("가을 이야기"));
        WebsitePageDocument child = pages.createContentPage(headquarters.token(), created.id(),
                new WebsitePageDraftMetadata("details", "가을 상세", true, 11), validContentPage("가을 상세"));
        int auditCount = jdbc.queryForObject("select count(*) from website_page_audit where page_id = ?", Integer.class, created.id());

        WebsitePageMoveImpact impact = pages.moveImpact(headquarters.token(), created.id(), destination, "autumn");

        assertThat(impact.newRootDraftPath()).isEqualTo("/brand/collections/autumn");
        assertThat(impact.items()).extracting(WebsitePageMoveImpactItem::nextDraftPath)
                .containsExactly("/brand/collections/autumn", "/brand/collections/autumn/details");

        WebsitePageDocument moved = pages.moveContentPage(headquarters.token(), created.id(),
                new MoveWebsitePageRequest(destination, "autumn", created.draftVersion(), created.lifecycleVersion()));

        assertThat(moved.draftMetadata().path()).isEqualTo("/brand/collections/autumn");
        assertThat(moved.draftVersion()).isEqualTo(created.draftVersion() + 1);
        assertThat(moved.publishedMetadata().path()).isEqualTo(created.publishedMetadata().path());
        assertThat(pages.pageDraft(headquarters.token(), child.id()).draftMetadata().path())
                .isEqualTo("/brand/collections/autumn/details");
        assertThat(jdbc.queryForObject("select count(*) from website_page_audit where page_id = ?", Integer.class, created.id()))
                .isEqualTo(auditCount + 1);
        assertThat(jdbc.queryForObject("select count(*) from website_page_audit where page_id = ? and action = 'PAGE_MOVED'", Integer.class, created.id()))
                .isEqualTo(1);
    }

    @Test
    void movesPublishedTreeWithPermanentRedirects() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        UUID destination = brandSubsection("offers", "오퍼");
        WebsitePageDocument root = pages.createContentPage(headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("published-story", "발행 이야기", true, 12), validContentPage("발행 이야기"));
        WebsitePageDocument child = pages.createContentPage(headquarters.token(), root.id(),
                new WebsitePageDraftMetadata("details", "발행 상세", true, 13), validContentPage("발행 상세"));
        root = pages.publishPage(headquarters.token(), root.id(), root.draftVersion(), root.publishedVersion());
        child = pages.publishPage(headquarters.token(), child.id(), child.draftVersion(), child.publishedVersion());

        WebsitePageMoveImpact impact = pages.moveImpact(headquarters.token(), root.id(), destination, "published-story");

        assertThat(impact.items()).extracting(WebsitePageMoveImpactItem::currentPublishedPath)
                .containsExactly("/brand/published-story", "/brand/published-story/details");
        assertThat(impact.items()).extracting(WebsitePageMoveImpactItem::nextPublishedPath)
                .containsExactly("/brand/offers/published-story", "/brand/offers/published-story/details");

        WebsitePageDocument moved = pageManagement.move(root.id(),
                new MoveWebsitePageRequest(destination, "published-story", root.draftVersion(), root.lifecycleVersion(), root.publishedVersion()),
                headquarters.token());

        assertThat(moved.publishedMetadata().path()).isEqualTo("/brand/offers/published-story");
        assertThat(pages.redirectTarget("/brand/published-story")).isEqualTo("/brand/offers/published-story");
        assertThat(pages.redirectTarget("/brand/published-story/details")).isEqualTo("/brand/offers/published-story/details");
        assertThat(jdbc.queryForObject("select page_snapshot ->> 'path' from website_page_version where page_id = ? and version = ?",
                String.class, root.id(), root.publishedVersion())).isEqualTo("/brand/offers/published-story");
        assertThat(jdbc.queryForObject("select page_snapshot ->> 'path' from website_page_version where page_id = ? and version = ?",
                String.class, child.id(), child.publishedVersion())).isEqualTo("/brand/offers/published-story/details");
        assertThat(publicPages.resolve("/brand/published-story").getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(publicPages.resolve("/brand/published-story").getHeaders().getLocation())
                .hasToString("/brand/offers/published-story");
    }

    @Test
    void rejectsPublishedMoveThatWouldCreateARedirectChain() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        UUID firstDestination = brandSubsection("offers", "오퍼");
        UUID secondDestination = brandSubsection("collections", "컬렉션");
        WebsitePageDocument root = pages.createContentPage(headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("stable-story", "안정 경로", true, 14), validContentPage("안정 경로"));
        WebsitePageDocument publishedRoot = pages.publishPage(headquarters.token(), root.id(), root.draftVersion(), root.publishedVersion());
        WebsitePageDocument moved = pages.movePublishedContentPage(headquarters.token(), publishedRoot.id(),
                new MoveWebsitePageRequest(firstDestination, "stable-story", publishedRoot.draftVersion(), publishedRoot.lifecycleVersion(), publishedRoot.publishedVersion()));

        assertThatThrownBy(() -> pages.movePublishedContentPage(headquarters.token(), publishedRoot.id(),
                new MoveWebsitePageRequest(secondDestination, "stable-story", moved.draftVersion(), moved.lifecycleVersion(), moved.publishedVersion())))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("WEBSITE_PAGE_PATH_CONFLICT");
        assertThat(pages.resolvePublished("/brand/offers/stable-story").path())
                .isEqualTo("/brand/offers/stable-story");
        assertThat(pages.redirectTarget("/brand/stable-story")).isEqualTo("/brand/offers/stable-story");
        assertThat(pages.redirectTarget("/brand/offers/stable-story")).isNull();
    }

    @Test
    void rejectsStalePublishedMoveWithoutCreatingARedirect() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        UUID destination = brandSubsection("offers", "오퍼");
        WebsitePageDocument root = pages.createContentPage(headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("stale-story", "오래된 이동", true, 15), validContentPage("오래된 이동"));
        WebsitePageDocument publishedRoot = pages.publishPage(headquarters.token(), root.id(), root.draftVersion(), root.publishedVersion());

        assertThatThrownBy(() -> pages.movePublishedContentPage(headquarters.token(), publishedRoot.id(),
                new MoveWebsitePageRequest(destination, "stale-story", publishedRoot.draftVersion() + 1,
                        publishedRoot.lifecycleVersion(), publishedRoot.publishedVersion())))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("WEB_CONTENT_VERSION_CONFLICT");
        assertThat(pages.resolvePublished("/brand/stale-story").path()).isEqualTo("/brand/stale-story");
        assertThat(pages.redirectTarget("/brand/stale-story")).isNull();
    }

    @Test
    void returnsLegacyKindsBlockIdsAndEmptyConnectionsWithoutChangingPageContent() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument landing = pages.landingDraft(session.token(), HOTEL);
        WebsitePageDocument created = pages.createContentPage(
                session.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("legacy-contract", "레거시 계약", true, 11), validContentPage("레거시 계약"));

        assertThat(landing.contentKind()).isEqualTo(ContentKind.DESTINATION);
        assertThat(created.contentKind()).isEqualTo(ContentKind.BRAND);
        assertThat(created.draftConnections().roomTypeIds()).isEmpty();
        assertThat(created.draftConnections().targetHotelIds()).isEmpty();
        assertThat(created.draftConnections().relatedPages()).isEmpty();
        jdbc.update("""
                update website_page
                   set draft_content = jsonb_set(draft_content, '{blocks,0}', (draft_content #> '{blocks,0}') - 'blockId')
                 where id = ?
                """, created.id());
        WebsitePageDocument legacyDraft = pages.pageDraft(session.token(), created.id());
        @SuppressWarnings("unchecked")
        Map<String, Object> draftHero = (Map<String, Object>) ((List<?>) legacyDraft.draftContent().get("blocks")).getFirst();
        assertThat(draftHero.get("blockId")).isInstanceOf(String.class);
        assertThat(jdbc.queryForObject("select jsonb_extract_path_text(draft_content, 'blocks', '0', 'blockId') is null from website_page where id = ?", Boolean.class,
                created.id())).isTrue();

        WebsitePageDocument published = pages.publishPage(
                session.token(), created.id(), created.draftVersion(), created.publishedVersion());
        jdbc.update("""
                update website_page
                   set published_content = jsonb_set(published_content, '{blocks,0}', (published_content #> '{blocks,0}') - 'blockId')
                 where id = ?
                """, created.id());
        PublishedWebsitePage resolved = pages.resolvePublished("/brand/legacy-contract");

        assertThat(resolved.contentKind()).isEqualTo(ContentKind.BRAND);
        assertThat(resolved.connections().roomTypeIds()).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> publishedHero = (Map<String, Object>) ((List<?>) resolved.content().get("blocks")).getFirst();
        assertThat(publishedHero.get("blockId")).isInstanceOf(String.class);
        assertThat(jdbc.queryForObject("select jsonb_extract_path_text(published_content, 'blocks', '0', 'blockId') is null from website_page where id = ?", Boolean.class,
                created.id())).isTrue();
        assertThat(jdbc.queryForObject("select page_snapshot ->> 'contentKind' from website_page_version where page_id = ? and version = ?",
                String.class, created.id(), published.publishedVersion())).isEqualTo("BRAND");
    }

    @Test
    void writesStableBlockIdsWhenAnExistingContentPageDraftIsSaved() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument created = pages.createContentPage(
                session.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("save-block-id", "초안 블록 ID", true, 12), validContentPage("초안 블록 ID"));

        WebsitePageDocument saved = pages.saveContentPageDraft(
                session.token(), created.id(), created.draftVersion(), draftMetadata(created), validContentPage("초안 블록 ID 수정"));

        assertThat(jdbc.queryForObject("select jsonb_extract_path_text(draft_content, 'blocks', '0', 'blockId') is not null from website_page where id = ?",
                Boolean.class, created.id())).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> savedHero = (Map<String, Object>) ((List<?>) saved.draftContent().get("blocks")).getFirst();
        assertThat(savedHero.get("blockId")).isInstanceOf(String.class);
    }

    @Test
    void validatesRequiredBlocksForEachTypedContentKind() {
        contentPages.validate(ContentKind.ROOM, typedContent(ContentKind.ROOM));
        contentPages.validate(ContentKind.DINING, typedContent(ContentKind.DINING));
        contentPages.validate(ContentKind.FACILITY, typedContent(ContentKind.FACILITY));
        contentPages.validate(ContentKind.EXPERIENCE, typedContent(ContentKind.EXPERIENCE));
        contentPages.validate(ContentKind.PROMOTION, typedContent(ContentKind.PROMOTION));
        contentPages.validate(ContentKind.GUIDE, typedContent(ContentKind.GUIDE));
        contentPages.validate(ContentKind.BRAND, typedContent(ContentKind.BRAND));

        assertThatThrownBy(() -> contentPages.validate(ContentKind.ROOM, roomContent(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SPEC_TABLE");
        assertThatThrownBy(() -> contentPages.validate(ContentKind.DINING, diningContent(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPERATING_HOURS");
        assertThatThrownBy(() -> contentPages.validate(ContentKind.BRAND, document(List.of(
                hero(blockId(), "브랜드", "/images/sokcho-coast-hero.png"),
                Map.of("blockId", blockId(), "type", "SCRIPT")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용");
        String duplicateId = blockId();
        assertThatThrownBy(() -> contentPages.validate(ContentKind.BRAND, document(List.of(
                hero(duplicateId, "브랜드", "/images/sokcho-coast-hero.png"), richText(duplicateId)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blockId");
        assertThatThrownBy(() -> contentPages.validate(ContentKind.BRAND, document(List.of(
                hero(blockId(), "브랜드", "https://example.com/hero.png")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("안전한 전달 경로");
    }

    @Test
    void rejectsRoomTypeConnectionsOutsideTheContentHotelScope() {
        UUID hotelRoomType = UUID.randomUUID();
        UUID secondHotelRoomType = UUID.randomUUID();
        jdbc.update("insert into room_type values (?, ?, ?, ?)", hotelRoomType, HOTEL, "페이지 테스트 객실", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", secondHotelRoomType, SECOND_HOTEL, "두 번째 테스트 객실", 3);

        connectionValidator.validate(ContentKind.ROOM, HOTEL,
                new WebsitePageConnections(List.of(hotelRoomType), List.of(), List.of()));
        connectionValidator.validate(ContentKind.PROMOTION, null,
                new WebsitePageConnections(List.of(hotelRoomType), List.of(HOTEL, SECOND_HOTEL), List.of()));

        assertThatThrownBy(() -> connectionValidator.validate(ContentKind.ROOM, HOTEL,
                new WebsitePageConnections(List.of(secondHotelRoomType), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("객실 유형").hasMessageContaining("지점");
        assertThatThrownBy(() -> connectionValidator.validate(ContentKind.PROMOTION, null,
                new WebsitePageConnections(List.of(hotelRoomType), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("대상 지점");
    }

    @Test
    void keepsDraftAndPublishedConnectionsIsolatedAcrossLifecycle() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        UUID roomTypeA = UUID.randomUUID();
        UUID roomTypeB = UUID.randomUUID();
        jdbc.update("insert into room_type values (?, ?, ?, ?)", roomTypeA, HOTEL, "첫 번째 연결 객실", 2);
        jdbc.update("insert into room_type values (?, ?, ?, ?)", roomTypeB, HOTEL, "두 번째 연결 객실", 3);
        UUID parentId = hotelScopedSection();

        WebsitePageDocument created = pages.createContentPage(
                session.token(), parentId, ContentKind.ROOM, HOTEL,
                new WebsitePageDraftMetadata("ocean-suite", "오션 스위트", true, 10), roomContent(true),
                new WebsitePageConnections(List.of(roomTypeA), List.of(), List.of()));

        assertThat(created.contentKind()).isEqualTo(ContentKind.ROOM);
        assertThat(created.draftConnections().roomTypeIds()).containsExactly(roomTypeA);
        assertThat(created.publishedConnections()).isEqualTo(WebsitePageConnections.empty());
        assertThat(connectionRoomTypes(created.id(), "DRAFT")).containsExactly(roomTypeA);
        assertThat(connectionRoomTypes(created.id(), "PUBLISHED")).isEmpty();

        WebsitePageDocument firstPublished = pages.publishPage(
                session.token(), created.id(), created.draftVersion(), created.publishedVersion());
        assertThat(pages.resolvePublished("/stays/page-test/ocean-suite").connections().roomTypeIds()).containsExactly(roomTypeA);
        assertThat(jdbc.queryForObject("""
                select jsonb_extract_path_text(page_snapshot, 'contentKind')
                  from website_page_version where page_id = ? and version = ?
                """, String.class, created.id(), firstPublished.publishedVersion())).isEqualTo("ROOM");
        assertThat(jdbc.queryForObject("""
                select jsonb_array_length(page_snapshot #> '{connections,roomTypeIds}')
                  from website_page_version where page_id = ? and version = ?
                """, Integer.class, created.id(), firstPublished.publishedVersion())).isEqualTo(1);

        WebsitePageDocument saved = pages.saveContentPageDraft(
                session.token(), created.id(), firstPublished.draftVersion(), draftMetadata(firstPublished), roomContent(true),
                new WebsitePageConnections(List.of(roomTypeB), List.of(), List.of()));
        assertThat(saved.draftConnections().roomTypeIds()).containsExactly(roomTypeB);
        assertThat(saved.publishedConnections().roomTypeIds()).containsExactly(roomTypeA);
        assertThat(pages.resolvePublished("/stays/page-test/ocean-suite").connections().roomTypeIds()).containsExactly(roomTypeA);

        WebsitePageDocument secondPublished = pages.publishPage(
                session.token(), created.id(), saved.draftVersion(), saved.publishedVersion());
        assertThat(secondPublished.publishedConnections().roomTypeIds()).containsExactly(roomTypeB);
        assertThat(pages.resolvePublished("/stays/page-test/ocean-suite").connections().roomTypeIds()).containsExactly(roomTypeB);
        assertThat(pages.compareContentPageVersions(
                session.token(), created.id(), firstPublished.publishedVersion(), secondPublished.publishedVersion())
                .base().content()).isEqualTo(firstPublished.publishedContent());

        WebsitePageDocument restoredVersion = pages.restoreContentPageVersionDraft(
                session.token(), created.id(), firstPublished.publishedVersion(), lifecycleRequest(secondPublished));
        assertThat(restoredVersion.draftConnections().roomTypeIds()).containsExactly(roomTypeA);
        assertThat(restoredVersion.publishedConnections().roomTypeIds()).containsExactly(roomTypeB);

        WebsitePageDocument archived = pages.archiveContentPage(session.token(), created.id(), lifecycleRequest(restoredVersion));
        assertThat(archived.draftConnections().roomTypeIds()).containsExactly(roomTypeA);
        assertThat(archived.publishedConnections()).isEqualTo(WebsitePageConnections.empty());
        assertThat(connectionRoomTypes(created.id(), "PUBLISHED")).isEmpty();

        WebsitePageDocument restored = pages.restoreContentPage(session.token(), created.id(), lifecycleRequest(archived));
        assertThat(restored.draftConnections().roomTypeIds()).containsExactly(roomTypeA);
        assertThat(restored.publishedConnections()).isEqualTo(WebsitePageConnections.empty());

        WebsitePageDocument archivedAgain = pages.archiveContentPage(session.token(), created.id(), lifecycleRequest(restored));
        pages.deleteArchivedContentPage(session.token(), created.id(), lifecycleRequest(archivedAgain));
        assertThat(connectionRoomTypes(created.id(), "DRAFT")).isEmpty();
        assertThat(connectionRoomTypes(created.id(), "PUBLISHED")).isEmpty();
    }

    @Test
    void returnsOnlySafeContentReferencesForHeadquarters() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("pages-branch@example.com", "branch-password");
        UUID roomType = UUID.randomUUID();
        jdbc.update("insert into room_type values (?, ?, ?, ?)", roomType, HOTEL, "참조용 객실", 2);
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("reference-page", "참조 페이지", true, 11), validContentPage("참조 페이지"));
        pages.publishPage(headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());

        ContentReferenceCatalog catalog = pages.contentReference(headquarters.token());

        assertThat(catalog.hotels()).anySatisfy(hotel -> {
            assertThat(hotel.id()).isEqualTo(HOTEL);
            assertThat(hotel.roomTypes()).extracting(ContentReferenceCatalog.RoomType::id).contains(roomType);
        });
        assertThat(catalog.pages()).anySatisfy(page -> {
            assertThat(page.id()).isEqualTo(created.id());
            assertThat(page.contentKind()).isEqualTo(ContentKind.BRAND);
            assertThat(page.path()).isEqualTo("/brand/reference-page");
        });
        assertThatThrownBy(() -> pages.contentReference(branch.token()))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void servesOnlyPublishedTypedCardsAndConnections() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        UUID roomType = UUID.randomUUID();
        jdbc.update("insert into room_type values (?, ?, ?, ?)", roomType, HOTEL, "컬렉션 객실", 2);
        UUID parentId = hotelScopedSection();
        WebsitePageDocument created = pages.createContentPage(
                session.token(), parentId, ContentKind.ROOM, HOTEL,
                new WebsitePageDraftMetadata("collection-suite", "컬렉션 스위트", true, 10), roomContent(true),
                new WebsitePageConnections(List.of(roomType), List.of(), List.of()));
        WebsitePageDocument published = pages.publishPage(
                session.token(), created.id(), created.draftVersion(), created.publishedVersion());

        List<WebsiteContentCollectionItem> cards = pages.publishedCollection(hotelSlug(HOTEL), ContentKind.ROOM);

        assertThat(cards).extracting(WebsiteContentCollectionItem::id).containsExactly(created.id());
        assertThat(cards.getFirst()).extracting(
                WebsiteContentCollectionItem::contentKind,
                WebsiteContentCollectionItem::path,
                WebsiteContentCollectionItem::title,
                WebsiteContentCollectionItem::image,
                WebsiteContentCollectionItem::hotelSlug)
                .containsExactly(ContentKind.ROOM, "/stays/page-test/collection-suite", "객실", "/images/sokcho-coast-hero.png", hotelSlug(HOTEL));
        assertThatThrownBy(() -> pages.publishedCollection("unknown", ContentKind.ROOM))
                .isInstanceOf(WebsitePageNotFoundException.class);
        assertThatThrownBy(() -> pages.publishedCollection(null, ContentKind.ROOM))
                .isInstanceOf(IllegalArgumentException.class);

        jdbc.update("""
                update website_page
                   set published_content = jsonb_set(published_content, '{blocks,0,imageSrc}', '"/images/../unsafe.png"'::jsonb)
                 where id = ?
                """, created.id());
        assertThat(pages.publishedCollection(hotelSlug(HOTEL), ContentKind.ROOM)).isEmpty();

        jdbc.update("""
                update website_page
                   set published_content = jsonb_set(published_content, '{blocks,0,imageSrc}', '"/images/room/../unsafe.png"'::jsonb)
                 where id = ?
                """, created.id());
        assertThat(pages.publishedCollection(hotelSlug(HOTEL), ContentKind.ROOM)).isEmpty();

        pages.archiveContentPage(session.token(), created.id(), lifecycleRequest(published));
        assertThat(pages.publishedCollection(hotelSlug(HOTEL), ContentKind.ROOM)).isEmpty();
    }

    @Test
    void keepsHomeDraftPrivateAndLetsOnlyHeadquartersPublishIt() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("pages-branch@example.com", "branch-password");
        WebsitePageDocument initial = pages.homeDraft(headquarters.token());
        Map<String, Object> publishedBeforeSave = pages.resolvePublished("/").content();

        assertThat(initial.pageType()).isEqualTo("HOME_PAGE");
        assertThat(initial.hotelId()).isNull();
        assertThat(initial.draftMetadata().slug()).isEqualTo("home");
        assertThat(initial.draftMetadata().path()).isEqualTo("/");
        assertThat(initial.draftMetadata().menuVisible()).isFalse();
        assertThatThrownBy(() -> pages.publishPage(
                headquarters.token(), initial.id(), initial.draftVersion(), initial.publishedVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발행할 페이지");

        WebsitePageDocument saved = pages.saveHomeDraft(
                headquarters.token(), initial.draftVersion(), validContentPage("홈 CMS 초안"));

        assertThat(saved.draftContent()).containsEntry("seo",
                Map.of("title", "홈 CMS 초안 | STAY HANEUL", "description", "STAY HANEUL의 브랜드 이야기입니다."));
        assertThat(saved.draftMetadata().slug()).isEqualTo("home");
        assertThat(saved.draftMetadata().path()).isEqualTo("/");
        assertThat(saved.draftMetadata().menuVisible()).isFalse();
        assertThat(pages.resolvePublished("/").content()).isEqualTo(publishedBeforeSave);

        WebsitePageDocument published = pages.publishHome(
                headquarters.token(), saved.draftVersion(), saved.publishedVersion());
        PublishedWebsitePage resolved = pages.resolvePublished("/");

        assertThat(published.publishedMetadata().path()).isEqualTo("/");
        assertThat(resolved.type()).isEqualTo("HOME_PAGE");
        assertThat(resolved.hotelId()).isNull();
        assertThat(resolved.content()).containsEntry("seo",
                Map.of("title", "홈 CMS 초안 | STAY HANEUL", "description", "STAY HANEUL의 브랜드 이야기입니다."));
        assertThat(pages.navigation()).allSatisfy(section -> {
            assertThat(section.path()).isNotEqualTo("/");
            assertThat(section.children()).noneSatisfy(child -> assertThat(child.path()).isEqualTo("/"));
        });
        assertThat(pages.homeVersions(headquarters.token())).extracting(WebContentVersion::version).containsExactly(2, 1);

        assertThatThrownBy(() -> pages.homeDraft(branch.token()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.saveHomeDraft(branch.token(), saved.draftVersion(), validContentPage("지점 홈 초안")))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.publishHome(branch.token(), saved.draftVersion(), saved.publishedVersion()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.homeVersions(branch.token()))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void rejectsUnsafeContentAndNonSectionParentsBeforeCreatingAContentPage() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument landing = pages.landingDraft(session.token(), HOTEL);

        assertThatThrownBy(() -> pages.createContentPage(
                session.token(), landing.id(),
                new WebsitePageDraftMetadata("invalid-parent", "잘못된 상위", true, 10), validContentPage("잘못된 상위")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECTION");

        Map<String, Object> unsafe = new java.util.HashMap<>(validContentPage("안전하지 않은 링크"));
        unsafe.put("blocks", List.of(Map.of(
                "type", "HERO", "imageAssetId", BUNDLED_ASSET, "imageSrc", "https://example.com/hero.png", "imageAlt", "원격 이미지",
                "eyebrow", "STAY HANEUL", "title", "안전하지 않은 링크", "description", "설명")));
        assertThatThrownBy(() -> pages.createContentPage(
                session.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("unsafe", "안전하지 않은 페이지", true, 20), unsafe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미디어 참조 오류");

        Map<String, Object> externalCta = new java.util.HashMap<>(validContentPage("외부 CTA"));
        externalCta.put("blocks", List.of(Map.of(
                "type", "HERO", "imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/sokcho-coast-hero.png", "imageAlt", "지역 풍경",
                "eyebrow", "STAY HANEUL", "title", "외부 CTA", "description", "설명",
                "cta", Map.of("label", "외부 이동", "href", "https://example.com"))));
        assertThatThrownBy(() -> pages.createContentPage(
                session.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("external-cta", "외부 CTA", true, 30), externalCta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("콘텐츠 형식 오류");

        Map<String, Object> traversingImage = new java.util.HashMap<>(validContentPage("경로 이탈 이미지"));
        traversingImage.put("blocks", List.of(Map.of(
                "type", "HERO", "imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/foo/../private.jpg", "imageAlt", "경로 이탈 이미지",
                "eyebrow", "STAY HANEUL", "title", "경로 이탈 이미지", "description", "설명")));
        assertThatThrownBy(() -> pages.createContentPage(
                session.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("traversing-image", "경로 이탈 이미지", true, 40), traversingImage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imageSrc");
    }

    @Test
    void rejectsMissingOrNonPositiveExpectedVersionsBeforeWritingAContentPage() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument created = pages.createContentPage(
                session.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("versioned-story", "버전 페이지", true, 10), validContentPage("버전 페이지"));
        WebsitePageDraftMetadata metadata = new WebsitePageDraftMetadata("versioned-story", "버전 페이지", true, 10);

        assertThatThrownBy(() -> pages.saveContentPageDraft(session.token(), created.id(), 0, metadata, validContentPage("초안")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초안 버전");
        assertThatThrownBy(() -> pages.saveContentPageDraft(session.token(), created.id(), -1, metadata, validContentPage("초안")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초안 버전");
        assertThatThrownBy(() -> pages.publishPage(session.token(), created.id(), 0, created.publishedVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초안 버전");
        assertThatThrownBy(() -> pages.publishPage(session.token(), created.id(), created.draftVersion(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발행 버전");
    }

    @Test
    void keepsChangedContentPageDraftMetadataPrivateAndRejectsBranchAccess() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("pages-branch@example.com", "branch-password");
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("first-story", "첫 브랜드 이야기", true, 10), validContentPage("첫 브랜드 이야기"));
        WebsitePageDocument published = pages.publishPage(
                headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());

        WebsitePageDocument changed = pages.saveContentPageDraft(
                headquarters.token(), created.id(), published.draftVersion(),
                new WebsitePageDraftMetadata("revised-story", "수정 중인 이야기", true, 20), validContentPage("수정 중인 이야기"));

        assertThat(pages.resolvePublished("/brand/first-story").content()).containsEntry("seo",
                Map.of("title", "첫 브랜드 이야기 | STAY HANEUL", "description", "STAY HANEUL의 브랜드 이야기입니다."));
        assertThatThrownBy(() -> pages.resolvePublished("/brand/revised-story"))
                .isInstanceOf(WebsitePageNotFoundException.class);
        assertThat(pages.staffTree(headquarters.token())).anySatisfy(section -> {
            assertThat(section.id()).isEqualTo(BRAND_SECTION);
            assertThat(section.children()).anySatisfy(page -> {
                assertThat(page.id()).isEqualTo(created.id());
                assertThat(page.status()).isEqualTo("CHANGED_AFTER_PUBLISH");
            });
        });
        assertThatThrownBy(() -> pages.pageDraft(branch.token(), created.id()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.createContentPage(branch.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("branch-story", "지점 페이지", true, 30), validContentPage("지점 페이지")))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.saveContentPageDraft(branch.token(), created.id(), changed.draftVersion(),
                new WebsitePageDraftMetadata(changed.draftMetadata().slug(), changed.draftMetadata().menuLabel(),
                        changed.draftMetadata().menuVisible(), changed.draftMetadata().menuOrder()), validContentPage("지점 변경")))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.publishPage(branch.token(), created.id(), changed.draftVersion(), changed.publishedVersion()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.pageVersions(branch.token(), created.id()))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void keepsAChangedDraftPathOutOfThePublicNavigationUntilItIsPublished() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument initial = pages.landingDraft(session.token(), HOTEL);
        WebsitePageDocument firstDraft = pages.saveLandingDraft(
                session.token(), HOTEL, initial.draftVersion(),
                new WebsitePageDraftMetadata("first-path", "첫 지점", true, 10), validContent("첫 발행"));
        WebsitePageDocument firstPublished = pages.publishLanding(
                session.token(), HOTEL, firstDraft.draftVersion(), firstDraft.publishedVersion());

        pages.saveLandingDraft(
                session.token(), HOTEL, firstPublished.draftVersion(),
                new WebsitePageDraftMetadata("unpublished-change", "변경 중인 지점", true, 10), validContent("초안 변경"));

        assertThat(pages.resolvePublished("/stays/first-path").content()).containsEntry("title", "첫 발행");
        assertThatThrownBy(() -> pages.resolvePublished("/stays/unpublished-change"))
                .isInstanceOf(WebsitePageNotFoundException.class);
        assertThat(pages.navigation()).allSatisfy(item ->
                assertThat(item.children()).noneSatisfy(child -> assertThat(child.path()).isEqualTo("/stays/unpublished-change")));
    }

    @Test
    void keepsMigratedPublishedContentPublicWhenItsLegacyDraftWasDifferent() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument initial = pages.landingDraft(session.token(), HOTEL);
        WebsitePageDocument saved = pages.saveLandingDraft(
                session.token(), HOTEL, initial.draftVersion(),
                new WebsitePageDraftMetadata("legacy-published", "기존 발행 지점", true, 10), validContent("기존 발행본"));
        pages.publishLanding(session.token(), HOTEL, saved.draftVersion(), saved.publishedVersion());

        jdbc.update("update website_page set published_from_draft_version = null where hotel_id = ?", HOTEL);

        assertThat(pages.resolvePublished("/stays/legacy-published").content()).containsEntry("title", "기존 발행본");
        assertThat(pages.navigation()).anySatisfy(section ->
                assertThat(section.children()).anySatisfy(child -> assertThat(child.path()).isEqualTo("/stays/legacy-published")));
    }

    @Test
    void rejectsReservedAndDuplicateLandingSlugsBeforeTheyCanBePublished() {
        StaffSessionView session = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument first = pages.landingDraft(session.token(), HOTEL);

        assertThatThrownBy(() -> pages.saveLandingDraft(
                session.token(), HOTEL, first.draftVersion(),
                new WebsitePageDraftMetadata("booking", "예약", true, 10), validContent("예약어")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("예약된");

        WebsitePageDocument firstSaved = pages.saveLandingDraft(
                session.token(), HOTEL, first.draftVersion(),
                new WebsitePageDraftMetadata("shared-path", "첫 지점", true, 10), validContent("첫 경로"));
        WebsitePageDocument firstPublished = pages.publishLanding(
                session.token(), HOTEL, firstSaved.draftVersion(), firstSaved.publishedVersion());
        pages.saveLandingDraft(
                session.token(), HOTEL, firstPublished.draftVersion(),
                new WebsitePageDraftMetadata("next-path", "첫 지점 새 주소", true, 10), validContent("첫 지점 초안 변경"));
        WebsitePageDocument second = pages.landingDraft(session.token(), SECOND_HOTEL);

        assertThatThrownBy(() -> pages.saveLandingDraft(
                session.token(), SECOND_HOTEL, second.draftVersion(),
                new WebsitePageDraftMetadata("shared-path", "두 번째 지점", true, 20), validContent("두 번째 경로")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("이미 사용 중인");
        assertThat(firstSaved.draftMetadata().path()).isEqualTo("/stays/shared-path");
    }

    @Test
    void archivesAndRestoresOnlyAContentPageWithoutDiscardingItsPublishedDocumentOrMediaUsage() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("pages-branch@example.com", "branch-password");
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("archivable-story", "보관할 브랜드 이야기", true, 10), validContentPage("보관할 브랜드 이야기"));
        WebsitePageDocument published = pages.publishPage(
                headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());
        Integer usageCountBeforeArchive = jdbc.queryForObject(
                "select count(*) from website_media_usage where page_id = ?", Integer.class, created.id());

        WebsitePageDocument archived = pages.archiveContentPage(
                headquarters.token(), created.id(), lifecycleRequest(published));

        assertThat(archived.lifecycleStatus()).isEqualTo("ARCHIVED");
        assertThat(archived.lifecycleVersion()).isEqualTo(published.lifecycleVersion() + 1);
        assertThat(archived.draftContent()).isEqualTo(published.draftContent());
        assertThat(archived.publishedContent()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from website_media_usage where page_id = ?", Integer.class, created.id()))
                .isEqualTo(usageCountBeforeArchive - 1);
        assertThatThrownBy(() -> pages.resolvePublished("/brand/archivable-story"))
                .isInstanceOf(WebsitePageNotFoundException.class);
        assertThat(pages.navigation()).allSatisfy(section ->
                assertThat(section.children()).noneSatisfy(child -> assertThat(child.path()).isEqualTo("/brand/archivable-story")));
        assertThat(pages.staffTree(headquarters.token())).anySatisfy(section ->
                assertThat(section.children()).anySatisfy(page -> {
                    if (page.id().equals(created.id())) assertThat(page.lifecycleStatus()).isEqualTo("ARCHIVED");
                }));
        assertThatThrownBy(() -> pages.archiveContentPage(
                headquarters.token(), created.id(), lifecycleRequest(published)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("상태를 변경");
        assertThatThrownBy(() -> pages.saveContentPageDraft(
                headquarters.token(), created.id(), archived.draftVersion(), draftMetadata(archived), validContentPage("보관 중 변경")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("보관");
        assertThatThrownBy(() -> pages.publishPage(
                headquarters.token(), created.id(), archived.draftVersion(), archived.publishedVersion()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("보관");
        assertThatThrownBy(() -> pages.archiveContentPage(branch.token(), created.id(), lifecycleRequest(archived)))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.archiveContentPage(
                headquarters.token(), BRAND_SECTION, lifecycleRequest(archived)))
                .isInstanceOf(WebsitePageNotFoundException.class);

        WebsitePageDocument restored = pages.restoreContentPage(
                headquarters.token(), created.id(), lifecycleRequest(archived));

        assertThat(restored.lifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(restored.lifecycleVersion()).isEqualTo(archived.lifecycleVersion() + 1);
        assertThat(restored.publishedContent()).isEmpty();
        assertThatThrownBy(() -> pages.resolvePublished("/brand/archivable-story"))
                .isInstanceOf(WebsitePageNotFoundException.class);

        pages.publishPage(headquarters.token(), created.id(), restored.draftVersion(), restored.publishedVersion());
        assertThat(pages.resolvePublished("/brand/archivable-story").content()).isEqualTo(published.publishedContent());
        assertThat(pages.navigation()).anySatisfy(section ->
                assertThat(section.children()).anySatisfy(child -> assertThat(child.path()).isEqualTo("/brand/archivable-story")));
    }

    @Test
    void rejectsTheCurrentContentPagePublicationAsADraftRestoreSource() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("current-version", "현재 발행본", true, 10), validContentPage("첫 발행본"));
        WebsitePageDocument first = pages.publishPage(
                headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());
        WebsitePageDocument changed = pages.saveContentPageDraft(
                headquarters.token(), created.id(), first.draftVersion(), draftMetadata(first), validContentPage("현재 발행본"));
        WebsitePageDocument current = pages.publishPage(
                headquarters.token(), created.id(), changed.draftVersion(), changed.publishedVersion());

        assertThatThrownBy(() -> pages.restoreContentPageVersionDraft(
                headquarters.token(), created.id(), current.publishedVersion(), lifecycleRequest(current)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이전 발행본");

        WebsitePageDocument unchanged = pages.pageDraft(headquarters.token(), created.id());
        assertThat(unchanged.draftContent()).isEqualTo(current.draftContent());
        assertThat(unchanged.draftVersion()).isEqualTo(current.draftVersion());
        assertThat(unchanged.publishedContent()).isEqualTo(current.publishedContent());
        assertThat(unchanged.publishedVersion()).isEqualTo(current.publishedVersion());
        assertThat(jdbc.queryForObject("select count(*) from website_page_audit where page_id = ? and action = 'VERSION_RESTORED'",
                Integer.class, created.id())).isZero();
    }

    @Test
    void restoresAnEarlierContentPagePublicationIntoTheDraftWithoutChangingTheCurrentPublicPage() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("pages-branch@example.com", "branch-password");
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("history", "처음 발행한 이야기", true, 10),
                validContentPage("첫 발행본", "첫 발행본 대표 이미지"));
        WebsitePageDocument first = pages.publishPage(
                headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());
        WebsitePageDocument changed = pages.saveContentPageDraft(
                headquarters.token(), created.id(), first.draftVersion(),
                new WebsitePageDraftMetadata("current-history", "현재 발행한 이야기", true, 20),
                validContentPage("변경 발행본", "변경 발행본 대표 이미지"));
        WebsitePageDocument currentPublic = pages.publishPage(
                headquarters.token(), created.id(), changed.draftVersion(), changed.publishedVersion());

        WebsitePageDocument restored = pages.restoreContentPageVersionDraft(
                headquarters.token(), created.id(), first.publishedVersion(), lifecycleRequest(currentPublic));

        assertThat(restored.draftContent()).isEqualTo(first.publishedContent());
        assertThat(restored.draftVersion()).isEqualTo(currentPublic.draftVersion() + 1);
        assertThat(restored.draftMetadata()).isEqualTo(currentPublic.draftMetadata());
        assertThat(restored.publishedContent()).isEqualTo(currentPublic.publishedContent());
        assertThat(restored.publishedVersion()).isEqualTo(currentPublic.publishedVersion());
        assertThat(restored.publishedMetadata()).isEqualTo(currentPublic.publishedMetadata());
        assertThat(pages.resolvePublished("/brand/current-history").content()).isEqualTo(currentPublic.publishedContent());
        WebsitePageDocument republished = pages.publishPage(
                headquarters.token(), created.id(), restored.draftVersion(), restored.publishedVersion());
        assertThat(republished.publishedMetadata()).isEqualTo(currentPublic.publishedMetadata());
        assertThat(pages.resolvePublished("/brand/current-history").content()).isEqualTo(first.publishedContent());
        assertThat(jdbc.queryForObject("""
                select alt_text from website_media_usage
                 where page_id = ? and document_state = 'DRAFT' and field_path = 'blocks[0].imageAssetId'
                """, String.class, created.id())).isEqualTo("첫 발행본 대표 이미지");
        assertThat(jdbc.queryForObject("""
                select details ->> 'sourceVersion' from website_page_audit
                 where page_id = ? and action = 'VERSION_RESTORED'
                 order by id desc limit 1
                """, String.class, created.id())).isEqualTo(Integer.toString(first.publishedVersion()));

        assertThatThrownBy(() -> pages.restoreContentPageVersionDraft(
                headquarters.token(), created.id(), first.publishedVersion(), lifecycleRequest(currentPublic)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("상태를 변경");
        assertThatThrownBy(() -> pages.restoreContentPageVersionDraft(
                headquarters.token(), created.id(), 0, lifecycleRequest(republished)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발행본 버전");

        assertThatThrownBy(() -> pages.restoreContentPageVersionDraft(
                headquarters.token(), created.id(), 1, lifecycleRequest(republished)))
                .isInstanceOf(WebsitePageNotFoundException.class);

        WebsitePageDocument archived = pages.archiveContentPage(headquarters.token(), created.id(), lifecycleRequest(republished));
        assertThatThrownBy(() -> pages.restoreContentPageVersionDraft(
                headquarters.token(), created.id(), first.publishedVersion(), lifecycleRequest(archived)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("상태를 변경");
        assertThatThrownBy(() -> pages.restoreContentPageVersionDraft(
                branch.token(), created.id(), first.publishedVersion(), lifecycleRequest(archived)))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void permanentlyDeletesOnlyAnArchivedContentPageAndItsPageScopedRecords() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("pages-branch@example.com", "branch-password");
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("remove-history", "삭제할 이야기", true, 40), validContentPage("삭제할 이야기"));
        WebsitePageDocument published = pages.publishPage(
                headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());

        assertThatThrownBy(() -> pages.deleteArchivedContentPage(headquarters.token(), created.id(), lifecycleRequest(published)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("보관");

        WebsitePageDocument archived = pages.archiveContentPage(headquarters.token(), created.id(), lifecycleRequest(published));
        assertThat(jdbc.queryForObject("select count(*) from website_page_version where page_id = ?", Integer.class, created.id())).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from website_page_audit where page_id = ?", Integer.class, created.id())).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from website_media_usage where page_id = ?", Integer.class, created.id())).isPositive();

        assertThatThrownBy(() -> pages.deleteArchivedContentPage(headquarters.token(), created.id(), lifecycleRequest(published)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("최신");
        assertThatThrownBy(() -> pages.deleteArchivedContentPage(branch.token(), created.id(), lifecycleRequest(archived)))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> pages.deleteArchivedContentPage(headquarters.token(), BRAND_SECTION, lifecycleRequest(archived)))
                .isInstanceOf(WebsitePageNotFoundException.class);

        pages.deleteArchivedContentPage(headquarters.token(), created.id(), lifecycleRequest(archived));

        assertThat(jdbc.queryForObject("select count(*) from website_page where id = ?", Integer.class, created.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from website_page_version where page_id = ?", Integer.class, created.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from website_page_audit where page_id = ?", Integer.class, created.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from website_media_usage where page_id = ?", Integer.class, created.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from website_media_asset where id = ?", Integer.class, UUID.fromString(BUNDLED_ASSET))).isEqualTo(1);
        assertThatThrownBy(() -> pages.pageDraft(headquarters.token(), created.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("찾을 수 없습니다");
        assertThat(pages.staffTree(headquarters.token()))
                .flatMap(WebsitePageTreeItem::children)
                .noneMatch(page -> page.id().equals(created.id()));
    }

    @Test
    void publishesRichResortDetailBlocksAndTracksGalleryAssetsInBothDocumentStates() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("ocean-suite", "오션 스위트", true, 50), richContentPage("오션 스위트"));

        assertThat(created.draftContent()).containsKey("blocks");
        assertThat(jdbc.queryForObject("""
                select count(*) from website_media_usage
                 where page_id = ? and field_path = 'blocks[1].items[0].imageAssetId'
                """, Integer.class, created.id())).isEqualTo(1);

        WebsitePageDocument published = pages.publishPage(
                headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());

        assertThat(pages.resolvePublished("/brand/ocean-suite").content())
                .isEqualTo(published.publishedContent());
        assertThat(jdbc.queryForObject("""
                select count(*) from website_media_usage
                 where page_id = ? and field_path = 'blocks[1].items[0].imageAssetId'
                """, Integer.class, created.id())).isEqualTo(2);

        Map<String, Object> unsafeGallery = richContentPage("안전하지 않은 갤러리");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unsafeBlocks = (List<Map<String, Object>>) unsafeGallery.get("blocks");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> galleryItems = (List<Map<String, Object>>) unsafeBlocks.get(1).get("items");
        galleryItems.getFirst().put("imageSrc", "https://example.com/private.jpg");
        assertThatThrownBy(() -> pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("unsafe-gallery", "안전하지 않은 갤러리", true, 60), unsafeGallery))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미디어 참조 오류");
    }

    @Test
    void comparesContentPagePublicationsWithoutChangingCurrentWebsiteState() {
        StaffSessionView headquarters = staffAccess.login("pages-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("pages-branch@example.com", "branch-password");
        WebsitePageDocument created = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("compare-history", "비교 첫 발행", true, 10),
                validContentPage("비교 첫 발행", "첫 비교 이미지"));
        WebsitePageDocument first = pages.publishPage(
                headquarters.token(), created.id(), created.draftVersion(), created.publishedVersion());
        WebsitePageDocument changed = pages.saveContentPageDraft(
                headquarters.token(), created.id(), first.draftVersion(),
                new WebsitePageDraftMetadata("compare-history", "비교 두 번째 발행", false, 20),
                validContentPage("비교 두 번째 발행", "두 번째 비교 이미지"));
        WebsitePageDocument current = pages.publishPage(
                headquarters.token(), created.id(), changed.draftVersion(), changed.publishedVersion());

        int auditCount = jdbc.queryForObject("select count(*) from website_page_audit where page_id = ?", Integer.class, created.id());
        int mediaUsageCount = jdbc.queryForObject("select count(*) from website_media_usage where page_id = ?", Integer.class, created.id());
        WebsitePageVersionComparison comparison = pages.compareContentPageVersions(
                headquarters.token(), created.id(), first.publishedVersion(), current.publishedVersion());

        assertThat(comparison.pageId()).isEqualTo(created.id());
        assertThat(comparison.base().version()).isEqualTo(first.publishedVersion());
        assertThat(comparison.base().publishedAt()).isNotNull();
        assertThat(comparison.base().metadata()).isEqualTo(first.publishedMetadata());
        assertThat(comparison.base().publishedFromDraftVersion()).isEqualTo(created.draftVersion());
        assertThat(comparison.base().content()).isEqualTo(first.publishedContent());
        assertThat(comparison.compare().version()).isEqualTo(current.publishedVersion());
        assertThat(comparison.compare().publishedAt()).isNotNull();
        assertThat(comparison.compare().metadata()).isEqualTo(current.publishedMetadata());
        assertThat(comparison.compare().publishedFromDraftVersion()).isEqualTo(changed.draftVersion());
        assertThat(comparison.compare().content()).isEqualTo(current.publishedContent());

        WebsitePageDocument unchanged = pages.pageDraft(headquarters.token(), created.id());
        assertThat(unchanged.draftContent()).isEqualTo(current.draftContent());
        assertThat(unchanged.draftVersion()).isEqualTo(current.draftVersion());
        assertThat(unchanged.publishedContent()).isEqualTo(current.publishedContent());
        assertThat(unchanged.publishedVersion()).isEqualTo(current.publishedVersion());
        assertThat(unchanged.lifecycleStatus()).isEqualTo(current.lifecycleStatus());
        assertThat(unchanged.lifecycleVersion()).isEqualTo(current.lifecycleVersion());
        assertThat(pages.resolvePublished("/brand/compare-history").content()).isEqualTo(current.publishedContent());
        assertThat(jdbc.queryForObject("select count(*) from website_page_audit where page_id = ?", Integer.class, created.id()))
                .isEqualTo(auditCount);
        assertThat(jdbc.queryForObject("select count(*) from website_media_usage where page_id = ?", Integer.class, created.id()))
                .isEqualTo(mediaUsageCount);

        assertThatThrownBy(() -> pages.compareContentPageVersions(headquarters.token(), created.id(), 0, current.publishedVersion()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pages.compareContentPageVersions(
                headquarters.token(), created.id(), first.publishedVersion(), first.publishedVersion()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pages.compareContentPageVersions(
                headquarters.token(), created.id(), current.publishedVersion(), first.publishedVersion()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pages.compareContentPageVersions(headquarters.token(), created.id(), first.publishedVersion(), 99))
                .isInstanceOf(WebsitePageNotFoundException.class);

        WebsitePageDocument anotherCreated = pages.createContentPage(
                headquarters.token(), BRAND_SECTION,
                new WebsitePageDraftMetadata("other-compare-history", "다른 비교 이력", true, 30), validContentPage("다른 비교 첫 발행"));
        WebsitePageDocument anotherFirst = pages.publishPage(
                headquarters.token(), anotherCreated.id(), anotherCreated.draftVersion(), anotherCreated.publishedVersion());
        WebsitePageDocument anotherChanged = pages.saveContentPageDraft(
                headquarters.token(), anotherCreated.id(), anotherFirst.draftVersion(), draftMetadata(anotherFirst), validContentPage("다른 비교 두 번째 발행"));
        WebsitePageDocument anotherSecond = pages.publishPage(
                headquarters.token(), anotherCreated.id(), anotherChanged.draftVersion(), anotherChanged.publishedVersion());
        WebsitePageDocument anotherChangedAgain = pages.saveContentPageDraft(
                headquarters.token(), anotherCreated.id(), anotherSecond.draftVersion(), draftMetadata(anotherSecond), validContentPage("다른 비교 세 번째 발행"));
        WebsitePageDocument anotherThird = pages.publishPage(
                headquarters.token(), anotherCreated.id(), anotherChangedAgain.draftVersion(), anotherChangedAgain.publishedVersion());
        assertThatThrownBy(() -> pages.compareContentPageVersions(
                headquarters.token(), created.id(), first.publishedVersion(), anotherThird.publishedVersion()))
                .isInstanceOf(WebsitePageNotFoundException.class);
        assertThatThrownBy(() -> pages.compareContentPageVersions(
                headquarters.token(), BRAND_SECTION, first.publishedVersion(), current.publishedVersion()))
                .isInstanceOf(WebsitePageNotFoundException.class);
        assertThatThrownBy(() -> pages.compareContentPageVersions(
                branch.token(), created.id(), first.publishedVersion(), current.publishedVersion()))
                .isInstanceOf(StaffAccessDeniedException.class);

        WebsitePageDocument archived = pages.archiveContentPage(headquarters.token(), created.id(), lifecycleRequest(current));
        WebsitePageVersionComparison archivedComparison = pages.compareContentPageVersions(
                headquarters.token(), created.id(), first.publishedVersion(), current.publishedVersion());
        assertThat(archived.lifecycleStatus()).isEqualTo("ARCHIVED");
        assertThat(archivedComparison.base().content()).isEqualTo(first.publishedContent());
        assertThat(archivedComparison.compare().content()).isEqualTo(current.publishedContent());

        jdbc.update("""
                update website_page_version
                   set page_snapshot = jsonb_set(page_snapshot, '{parentId}', '"invalid-parent"'::jsonb)
                 where page_id = ? and version = ?
                """, created.id(), first.publishedVersion());
        assertThatThrownBy(() -> pages.compareContentPageVersions(
                headquarters.token(), created.id(), first.publishedVersion(), current.publishedVersion()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private WebsitePageLifecycleRequest lifecycleRequest(WebsitePageDocument document) {
        return new WebsitePageLifecycleRequest(document.lifecycleVersion(), document.draftVersion(), document.publishedVersion());
    }

    private UUID hotelScopedSection() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into website_page (
                    id, hotel_id, parent_id, page_type, content_kind,
                    draft_slug, published_slug, draft_path, published_path,
                    draft_menu_label, published_menu_label, draft_menu_visible, published_menu_visible,
                    draft_menu_order, published_menu_order, draft_content, published_content,
                    draft_version, published_version, published_from_draft_version
                ) values (?, ?, null, 'SECTION', null, 'page-test', 'page-test', '/stays/page-test', '/stays/page-test',
                          '테스트 상세', '테스트 상세', true, true, 1, 1, '{}'::jsonb, '{}'::jsonb, 1, 1, 1)
                """, id, HOTEL);
        return id;
    }

    private UUID brandSubsection(String slug, String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into website_page (
                    id, hotel_id, parent_id, page_type, content_kind,
                    draft_slug, published_slug, draft_path, published_path,
                    draft_menu_label, published_menu_label, draft_menu_visible, published_menu_visible,
                    draft_menu_order, published_menu_order, draft_content, published_content,
                    draft_version, published_version, published_from_draft_version
                ) values (?, null, ?, 'SECTION', null, ?, ?, ?, ?, ?, ?, true, false, 1, 0, '{}'::jsonb, '{}'::jsonb, 1, 1, null)
                """, id, BRAND_SECTION, slug, slug, "/brand/" + slug, "/brand/" + slug, label, label);
        return id;
    }

    private List<UUID> connectionRoomTypes(UUID pageId, String state) {
        return jdbc.query("""
                select room_type_id from website_page_room_type
                 where page_id = ? and document_state = ? order by room_type_id
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), pageId, state);
    }

    private String hotelSlug(UUID hotelId) {
        return "hotel-" + hotelId.toString().replace("-", "");
    }

    private WebsitePageDraftMetadata draftMetadata(WebsitePageDocument document) {
        return new WebsitePageDraftMetadata(document.draftMetadata().slug(), document.draftMetadata().menuLabel(),
                document.draftMetadata().menuVisible(), document.draftMetadata().menuOrder());
    }

    private Map<String, Object> validContent(String title) {
        return Map.of(
                "heroAssetId", BUNDLED_ASSET,
                "heroImage", "/images/sokcho-coast-hero.png",
                "heroAlt", "속초 해안 호텔",
                "eyebrow", "SOKCHO · EAST SEA",
                "title", title,
                "description", "동해와 설악을 담은 휴식",
                "arrival", Map.of("address", "가상 해안로 186", "checkInOut", "15:00 / 11:00", "highlight", "바다 곁의 하루"),
                "experiences", List.of(Map.of("category", "ROOM", "title", "수평선 객실", "description", "바다를 담은 객실")),
                "offers", List.of(Map.of("title", "푸른 아침", "detail", "조식 포함", "bookingPeriod", "2026.09.01 ~ 2026.12.31", "stayPeriod", "2026.09.15 ~ 2027.02.28")));
    }

    private Map<String, Object> validContentPage(String title) {
        return validContentPage(title, "STAY HANEUL 풍경");
    }

    private Map<String, Object> validContentPage(String title, String imageAlt) {
        return Map.of(
                "seo", Map.of("title", title + " | STAY HANEUL", "description", "STAY HANEUL의 브랜드 이야기입니다."),
                "blocks", List.of(
                        Map.of("type", "HERO", "imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/sokcho-coast-hero.png", "imageAlt", imageAlt,
                                "eyebrow", "STAY HANEUL", "title", title, "description", "머무름의 새로운 기준을 소개합니다.",
                                "cta", Map.of("label", "메인으로", "href", "/")),
                        Map.of("type", "TEXT", "eyebrow", "OUR STORY", "title", "머무름의 기준",
                                "paragraphs", List.of("자연과 일상의 균형을 생각합니다."))));
    }

    private Map<String, Object> typedContent(ContentKind kind) {
        return switch (kind) {
            case ROOM -> roomContent(true);
            case DINING -> diningContent(true);
            case FACILITY -> document(List.of(
                    hero(blockId(), "시설", "/images/sokcho-coast-hero.png"), gallery(), specificationTable()));
            case EXPERIENCE -> document(List.of(
                    hero(blockId(), "경험", "/images/sokcho-coast-hero.png"), richText(blockId())));
            case PROMOTION -> document(List.of(
                    hero(blockId(), "프로모션", "/images/sokcho-coast-hero.png"), promotionSummary(), bookingCta()));
            case GUIDE -> document(List.of(hero(blockId(), "이용 안내", "/images/sokcho-coast-hero.png")));
            case BRAND -> document(List.of(hero(blockId(), "브랜드", "/images/sokcho-coast-hero.png")));
            default -> throw new IllegalArgumentException("상세 콘텐츠 종류가 아닙니다.");
        };
    }

    private Map<String, Object> roomContent(boolean includeSpecification) {
        List<Map<String, Object>> blocks = new java.util.ArrayList<>(List.of(
                hero(blockId(), "객실", "/images/sokcho-coast-hero.png"), gallery(), bookingCta()));
        if (includeSpecification) blocks.add(2, specificationTable());
        return document(blocks);
    }

    private Map<String, Object> diningContent(boolean includeHours) {
        List<Map<String, Object>> blocks = new java.util.ArrayList<>(List.of(
                hero(blockId(), "다이닝", "/images/sokcho-coast-hero.png"), gallery()));
        if (includeHours) blocks.add(operatingHours());
        return document(blocks);
    }

    private Map<String, Object> document(List<Map<String, Object>> blocks) {
        return Map.of("seo", Map.of("title", "통합 콘텐츠 | STAY HANEUL", "description", "통합 콘텐츠 계약을 확인합니다."), "blocks", blocks);
    }

    private Map<String, Object> hero(String id, String title, String imageSrc) {
        return Map.ofEntries(
                Map.entry("blockId", id), Map.entry("type", "HERO"), Map.entry("imageAssetId", BUNDLED_ASSET),
                Map.entry("imageSrc", imageSrc), Map.entry("imageAlt", title + " 대표 이미지"),
                Map.entry("eyebrow", "STAY HANEUL"), Map.entry("title", title), Map.entry("description", title + " 상세 소개"));
    }

    private Map<String, Object> gallery() {
        return Map.of("blockId", blockId(), "type", "IMAGE_GALLERY", "title", "갤러리", "items", List.of(
                Map.of("imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/sokcho-coast-hero.png", "imageAlt", "첫 번째 이미지"),
                Map.of("imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/sokcho-coast-hero.png", "imageAlt", "두 번째 이미지")));
    }

    private Map<String, Object> specificationTable() {
        return Map.of("blockId", blockId(), "type", "SPEC_TABLE", "title", "사양", "rows", List.of(Map.of("label", "정원", "value", "성인 2명")));
    }

    private Map<String, Object> richText(String id) {
        return Map.of("blockId", id, "type", "RICH_TEXT", "eyebrow", "STORY", "title", "자연과 함께", "paragraphs", List.of("자연의 리듬을 따라 머뭅니다."));
    }

    private Map<String, Object> operatingHours() {
        return Map.of("blockId", blockId(), "type", "OPERATING_HOURS", "title", "운영 시간", "entries", List.of(
                Map.of("dayLabel", "매일", "opensAt", "07:00", "closesAt", "22:00", "closed", false)));
    }

    private Map<String, Object> promotionSummary() {
        return Map.of("blockId", blockId(), "type", "PROMOTION_SUMMARY", "title", "가을 휴식", "salesPeriod", "2026.09.01 ~ 2026.10.31",
                "stayPeriod", "2026.09.15 ~ 2026.11.30", "benefits", List.of("조식 혜택"), "displayPrice", "혜택은 예약 조건에서 확인" );
    }

    private Map<String, Object> bookingCta() {
        return Map.of("blockId", blockId(), "type", "BOOKING_CTA", "title", "객실 검색", "description", "날짜와 인원을 선택해 주세요.",
                "label", "객실 검색", "hotelId", HOTEL.toString());
    }

    private String blockId() {
        return UUID.randomUUID().toString();
    }

    private Map<String, Object> richContentPage(String title) {
        List<Map<String, Object>> galleryItems = new java.util.ArrayList<>();
        galleryItems.add(new java.util.LinkedHashMap<>(Map.of(
                "imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/sokcho-coast-hero.png",
                "imageAlt", "오션 스위트 거실", "caption", "수평선을 바라보는 거실")));
        galleryItems.add(new java.util.LinkedHashMap<>(Map.of(
                "imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/sokcho-coast-hero.png",
                "imageAlt", "오션 스위트 침실", "caption", "편안한 침실")));

        List<Map<String, Object>> blocks = new java.util.ArrayList<>();
        blocks.add(new java.util.LinkedHashMap<>(Map.of(
                "type", "HERO", "imageAssetId", BUNDLED_ASSET, "imageSrc", "/images/sokcho-coast-hero.png",
                "imageAlt", "속초 해안", "eyebrow", "SOKCHO", "title", title, "description", "동해를 가장 가까이 누리는 객실")));
        blocks.add(new java.util.LinkedHashMap<>(Map.of(
                "type", "IMAGE_GALLERY", "eyebrow", "GALLERY", "title", "객실 갤러리", "description", "머무는 공간을 미리 만나보세요.",
                "items", galleryItems)));
        blocks.add(new java.util.LinkedHashMap<>(Map.of(
                "type", "FEATURE_GRID", "title", "객실의 특징", "items", List.of(
                        Map.of("title", "오션 뷰", "description", "동해의 수평선을 바라봅니다."),
                        Map.of("title", "넓은 휴식", "description", "여유로운 거실과 침실을 갖췄습니다.")))));
        blocks.add(new java.util.LinkedHashMap<>(Map.of(
                "type", "SPEC_TABLE", "title", "객실 사양", "rows", List.of(
                        Map.of("label", "면적", "value", "68㎡"), Map.of("label", "정원", "value", "성인 2명")))));
        blocks.add(new java.util.LinkedHashMap<>(Map.of(
                "type", "ACCORDION", "title", "이용 안내", "items", List.of(
                        Map.of("title", "체크인", "content", "15:00부터 입실할 수 있습니다.")))));
        blocks.add(new java.util.LinkedHashMap<>(Map.of(
                "type", "NOTICE_LIST", "title", "유의사항", "items", List.of(
                        Map.of("text", "객실은 금연입니다.", "severity", "IMPORTANT")))));
        return new java.util.LinkedHashMap<>(Map.of(
                "seo", Map.of("title", title + " | STAY HANEUL", "description", "STAY HANEUL의 객실 상세 페이지입니다."),
                "blocks", blocks));
    }
}
