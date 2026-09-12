package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.web.ApiExceptionHandler;

@SpringBootTest
@Transactional
class WebsiteTranslationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WebsitePageService pages;
    @Autowired PublicWebsitePageController publicPages;
    @Autowired StaffAccessService access;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired WebsiteTranslationService translations;
    @Autowired WebsiteMediaService media;
    @Autowired DataSource dataSource;

    @Test
    void startsNewEnglishTranslationsAsDraftWithNoReviewEvents() {
        String token = headquarters();
        var ko = story(token);
        assertThat(translations.review(token, ko.id()))
                .extracting(WebsiteTranslationReviewState::status,
                        WebsiteTranslationReviewState::reviewedDraftVersion)
                .containsExactly(WebsiteTranslationReviewStatus.DRAFT, null);
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        assertThat(jdbc.queryForList("""
                select page_id, locale, review_status, reviewed_draft_version
                from website_page_translation
                where page_id = ? and locale = 'en'
                """, ko.id())).singleElement().satisfies(row -> assertThat(row)
                        .containsEntry("page_id", ko.id())
                        .containsEntry("locale", "en")
                        .containsEntry("review_status", "DRAFT")
                        .containsEntry("reviewed_draft_version", null));
        var persisted = translations.review(token, ko.id());
        assertThat(persisted)
                .extracting(WebsiteTranslationReviewState::status,
                        WebsiteTranslationReviewState::reviewedDraftVersion)
                .containsExactly(WebsiteTranslationReviewStatus.DRAFT, null);
        assertThat(persisted.events()).isEmpty();
    }

    @Test
    void returnsLatestFiftyReviewEventsWithDeterministicActorMapping() {
        String token = headquarters();
        var ko = story(token);
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        UUID liveActor = access.requireHeadquarters(token).id();
        UUID deletedActor = UUID.randomUUID();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_ADMIN')",
                deletedActor, "deleted-reviewer@example.com", "삭제된 검토자", new BCryptPasswordEncoder().encode("review-test"));
        Instant tiedAt = Instant.parse("2026-09-12T12:00:00Z");
        long approvedId = jdbc.queryForObject("""
                insert into website_translation_review_event
                    (page_id, action, draft_version, actor_id, comment, created_at)
                values (?, 'APPROVED', 1, ?, 'approved comment', ?)
                returning id
                """, Long.class, ko.id(), liveActor, Timestamp.from(tiedAt));
        long rejectedId = jdbc.queryForObject("""
                insert into website_translation_review_event
                    (page_id, action, draft_version, actor_id, comment, created_at)
                values (?, 'REJECTED', 1, ?, null, ?)
                returning id
                """, Long.class, ko.id(), deletedActor, Timestamp.from(tiedAt));
        for (int index = 0; index < 50; index++) {
            jdbc.update("""
                    insert into website_translation_review_event
                        (page_id, action, draft_version, comment, created_at)
                    values (?, 'REVIEW_REQUESTED', 1, ?, ?)
                    """, ko.id(), "filler-" + index, Timestamp.from(tiedAt.minusSeconds(index + 1L)));
        }
        jdbc.update("delete from staff_member where id = ?", deletedActor);

        var events = translations.review(token, ko.id()).events();

        assertThat(events).hasSize(50);
        assertThat(events.get(0)).isEqualTo(new WebsiteTranslationReviewEvent(
                rejectedId, "REJECTED", 1, null, null, tiedAt, null));
        assertThat(events.get(1)).isEqualTo(new WebsiteTranslationReviewEvent(
                approvedId, "APPROVED", 1, liveActor, "다국어 본사", tiedAt, "approved comment"));
        assertThat(events)
                .extracting(WebsiteTranslationReviewEvent::comment)
                .doesNotContain("filler-48", "filler-49");
    }

    @Test
    void requestsAndApprovesTheCurrentEnglishDraft() throws Exception {
        String token = headquarters();
        var ko = story(token);
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String path = "/api/staff/website/pages/" + ko.id() + "/translations/en/review";

        mvc.perform(post(path + "/request").header("X-Staff-Session", token)
                        .contentType("application/json")
                        .content("{\"expectedDraftVersion\":1,\"comment\":\"Please review\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/approve").header("X-Staff-Session", token)
                        .contentType("application/json")
                        .content("{\"expectedDraftVersion\":1,\"comment\":\"Approved\"}"))
                .andExpect(status().isOk());

        assertThat(translations.review(token, ko.id()).status())
                .isEqualTo(WebsiteTranslationReviewStatus.APPROVED);
    }

    @Test
    void requiresCurrentApprovalAndInvalidatesItOnSaveWithoutChangingPublishedContent() throws Exception {
        String token = headquarters();
        UUID actor = access.requireHeadquarters(token).id();
        var created = pages.createContentPage(token, UUID.fromString("12000000-0000-0000-0000-000000000005"),
                new WebsitePageDraftMetadata("locale-story", "Published page", true, 10),
                content("Published title", "Published alt"));
        var ko = pages.publishPage(token, created.id(), created.draftVersion(), created.publishedVersion());
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());

        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String path = "/api/staff/website/pages/" + ko.id() + "/translations/en";
        String directPublish = mvc.perform(post(path + "/publish").header("X-Staff-Session", token)
                        .contentType("application/json")
                        .content("{\"expectedDraftVersion\":1,\"expectedPublishedVersion\":0}"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(read(directPublish).get("code")).isEqualTo("WEBSITE_TRANSLATION_NOT_APPROVED");

        approveEnglish(token, ko.id(), 1);
        translations.publish(token, ko.id(), new PublishWebsitePageRequest(1, 0));
        assertThat(translations.review(token, ko.id()))
                .extracting(WebsiteTranslationReviewState::status,
                        WebsiteTranslationReviewState::reviewedDraftVersion)
                .containsExactly(WebsiteTranslationReviewStatus.PUBLISHED, 1);
        assertThat(translations.review(token, ko.id()).events()).first()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("PUBLISHED");
                    assertThat(event.draftVersion()).isEqualTo(1);
                });
        translations.save(token, ko.id(), new SaveWebsitePageRequest(1,
                new WebsitePageDraftMetadata("locale-story", "Draft page", true, 10),
                content("Draft title", "Draft alt"), null));

        var saved = translations.draft(token, ko.id());
        assertThat(translations.review(token, ko.id()))
                .extracting(WebsiteTranslationReviewState::status,
                        WebsiteTranslationReviewState::reviewedDraftVersion)
                .containsExactly(WebsiteTranslationReviewStatus.DRAFT, null);
        assertThat(saved.publishedVersion()).isEqualTo(1);
        assertThat(saved.publishedMetadata().path()).isEqualTo("/en/brand/locale-story");
        assertThat(saved.publishedMetadata().menuLabel()).isEqualTo("Published page");
        assertThat(saved.publishedMetadata().menuVisible()).isTrue();
        assertThat(saved.publishedMetadata().menuOrder()).isEqualTo(10);
        assertThat(saved.publishedConnections()).isEqualTo(WebsitePageConnections.empty());
        assertThat(jdbc.queryForObject("""
                select published_from_draft_version from website_page_translation
                where page_id = ? and locale = 'en'
                """, Integer.class, ko.id())).isEqualTo(1);
        assertThat(json.writeValueAsString(translations.resolve("/en/brand/locale-story").content()))
                .contains("\"title\":\"Published title\"")
                .doesNotContain("Draft title");
        assertThat(jdbc.queryForObject("""
                select count(*) from website_media_usage
                where page_id = ? and locale = 'en' and document_state = 'PUBLISHED'
                """, Integer.class, ko.id())).isPositive();
        assertThat(translations.review(token, ko.id()).events())
                .filteredOn(event -> event.action().equals("APPROVAL_INVALIDATED"))
                .singleElement().satisfies(event -> {
                    assertThat(event.draftVersion()).isEqualTo(2);
                    assertThat(event.actorId()).isEqualTo(actor);
                });

        jdbc.update("""
                update website_page_translation
                set review_status = 'APPROVED', reviewed_draft_version = 1
                where page_id = ? and locale = 'en'
                """, ko.id());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        translations.publish(token, ko.id(), new PublishWebsitePageRequest(2, 1)))
                .isInstanceOfSatisfying(team.hotelchain.reservation.BusinessConflictException.class,
                        exception -> assertThat(exception.code()).isEqualTo("WEBSITE_TRANSLATION_REVIEW_STALE"));
        assertThat(translations.draft(token, ko.id()).publishedVersion()).isEqualTo(1);
    }

    @Test
    void requiresAReasonAndAuditsRejectedReviews() throws Exception {
        String token = headquarters();
        UUID actor = access.requireHeadquarters(token).id();
        var ko = story(token);
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String path = "/api/staff/website/pages/" + ko.id() + "/translations/en/review";
        mvc.perform(post(path + "/request").header("X-Staff-Session", token)
                        .contentType("application/json")
                        .content("{\"expectedDraftVersion\":1,\"comment\":null}"))
                .andExpect(status().isOk());

        String blankResponse = mvc.perform(post(path + "/reject").header("X-Staff-Session", token)
                        .contentType("application/json")
                        .content("{\"expectedDraftVersion\":1,\"comment\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(read(blankResponse).get("code"))
                .isEqualTo("WEBSITE_TRANSLATION_REJECTION_REASON_REQUIRED");

        mvc.perform(post(path + "/reject").header("X-Staff-Session", token)
                        .contentType("application/json")
                        .content("{\"expectedDraftVersion\":1,\"comment\":\"  Needs a complete alt text  \"}"))
                .andExpect(status().isOk());

        var rejected = translations.review(token, ko.id());
        assertThat(rejected.status()).isEqualTo(WebsiteTranslationReviewStatus.DRAFT);
        assertThat(rejected.reviewedDraftVersion()).isNull();
        assertThat(rejected.events()).first().satisfies(event -> {
            assertThat(event.action()).isEqualTo("REJECTED");
            assertThat(event.draftVersion()).isEqualTo(1);
            assertThat(event.actorId()).isEqualTo(actor);
            assertThat(event.createdAt()).isNotNull();
            assertThat(event.comment()).isEqualTo("Needs a complete alt text");
        });
    }

    @Test
    void migratesOnlyCurrentV20PublicationsToPublishedReviewState() {
        String schema = "translation_review_" + UUID.randomUUID().toString().replace("-", "");
        String translationTable = "\"" + schema + "\".website_page_translation";
        Flyway v20 = isolatedFlyway(schema, MigrationVersion.fromVersion("20"));
        try {
            v20.migrate();
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        insert into %s
                            (page_id, locale, draft_content, published_content, draft_path, published_path,
                             draft_menu_label, published_menu_label, draft_version, published_version,
                             published_from_draft_version)
                        values
                            ('12000000-0000-0000-0000-000000000001', 'en', '{"draft":"current"}'::jsonb,
                             '{"marker":"current-publication"}'::jsonb, '/en/current', '/en/current',
                             'Current', 'Current', 3, 2, 3),
                            ('12000000-0000-0000-0000-000000000005', 'en', '{"draft":"diverged"}'::jsonb,
                             '{"marker":"diverged-publication"}'::jsonb, '/en/diverged', '/en/diverged',
                             'Diverged', 'Diverged', 4, 2, 3)
                        """.formatted(translationTable));
            }

            isolatedFlyway(schema, MigrationVersion.fromVersion("21")).migrate();

            try (var connection = dataSource.getConnection()) {
                var isolatedJdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                var migrated = isolatedJdbc.query("""
                        select page_id, review_status, reviewed_draft_version, draft_version, published_version,
                            published_from_draft_version, published_content ->> 'marker' as marker
                        from %s
                        order by page_id
                        """.formatted(translationTable), (rs, rowNumber) -> new ReviewMigrationRow(
                                rs.getObject("page_id", UUID.class), rs.getString("review_status"),
                                rs.getObject("reviewed_draft_version", Integer.class), rs.getInt("draft_version"),
                                rs.getInt("published_version"), rs.getObject("published_from_draft_version", Integer.class),
                                rs.getString("marker")));
                assertThat(migrated).containsExactly(
                        new ReviewMigrationRow(UUID.fromString("12000000-0000-0000-0000-000000000001"),
                                "PUBLISHED", 3, 3, 2, 3, "current-publication"),
                        new ReviewMigrationRow(UUID.fromString("12000000-0000-0000-0000-000000000005"),
                                "DRAFT", null, 4, 2, 3, "diverged-publication"));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("V20에서 V21 검토 상태로 이관할 수 없습니다.", exception);
        } finally {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("drop schema if exists \"" + schema + "\" cascade");
            } catch (Exception exception) {
                throw new IllegalStateException("격리된 migration test schema를 정리할 수 없습니다.", exception);
            }
        }
    }

    @Test
    void archivesBothLanguagesAndRestoresOnlyDraftsBeforePermanentDeletion() throws Exception {
        String token = headquarters();
        var ko = story(token);
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        approveEnglish(token, ko.id(), 1);
        translations.publish(token, ko.id(), new PublishWebsitePageRequest(1, 0));
        var archived = pages.archiveContentPage(token, ko.id(), new WebsitePageLifecycleRequest(ko.lifecycleVersion(), ko.draftVersion(), ko.publishedVersion()));
        assertThat(translations.review(token, ko.id()))
                .extracting(WebsiteTranslationReviewState::status,
                        WebsiteTranslationReviewState::reviewedDraftVersion)
                .containsExactly(WebsiteTranslationReviewStatus.APPROVED, 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.resolve("/en/brand/locale-story"))
                .isInstanceOf(WebsitePageNotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.publish(token, ko.id(), new PublishWebsitePageRequest(1, 1)))
                .isInstanceOf(team.hotelchain.reservation.BusinessConflictException.class);
        assertThat(jdbc.queryForObject("select count(*) from website_media_usage where page_id = ? and document_state = 'PUBLISHED'", Integer.class, ko.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from website_media_usage where page_id = ? and document_state = 'DRAFT'", Integer.class, ko.id())).isEqualTo(2);
        var restored = pages.restoreContentPage(token, ko.id(), new WebsitePageLifecycleRequest(archived.lifecycleVersion(), archived.draftVersion(), archived.publishedVersion()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.resolve("/en/brand/locale-story"))
                .isInstanceOf(WebsitePageNotFoundException.class);
        translations.publish(token, ko.id(), new PublishWebsitePageRequest(1, 1));
        assertThat(translations.resolve("/en/brand/locale-story").path()).isEqualTo("/en/brand/locale-story");
        archived = pages.archiveContentPage(token, ko.id(), new WebsitePageLifecycleRequest(restored.lifecycleVersion(), restored.draftVersion(), restored.publishedVersion()));
        pages.deleteArchivedContentPage(token, ko.id(), new WebsitePageLifecycleRequest(archived.lifecycleVersion(), archived.draftVersion(), archived.publishedVersion()));
        assertThat(jdbc.queryForObject("select count(*) from website_page_translation where page_id = ?", Integer.class, ko.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from website_page_translation_version where page_id = ?", Integer.class, ko.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from website_media_asset where id = ?", Integer.class, WebsiteMediaService.BUNDLED_ASSET_ID)).isEqualTo(1);
    }

    @Test
    void protectsEnglishReferencesAndReportsLocaleSpecificUsagePaths() throws Exception {
        String token = headquarters();
        var ko = story(token);
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        translations.save(token, ko.id(), new SaveWebsitePageRequest(1,
                new WebsitePageDraftMetadata("locale-story", "Our story", true, 10), content("English title", "English coast"), null));
        assertThat(media.usages(token, WebsiteMediaService.BUNDLED_ASSET_ID)).anySatisfy(usage -> {
            assertThat(usage.locale()).isEqualTo("en");
            assertThat(usage.pagePath()).isEqualTo("/en/brand/locale-story");
            assertThat(usage.pageLabel()).isEqualTo("Our story");
            assertThat(usage.altText()).isEqualTo("English coast");
        });
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> media.archive(token, WebsiteMediaService.BUNDLED_ASSET_ID, new WebsiteMediaVersionRequest(1)))
                .isInstanceOf(team.hotelchain.reservation.BusinessConflictException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.save(token, ko.id(), new SaveWebsitePageRequest(1,
                new WebsitePageDraftMetadata("locale-story", "Stale", true, 10), content("Stale", "Stale alt"), null)))
                .isInstanceOf(team.hotelchain.reservation.BusinessConflictException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion()))
                .isInstanceOf(team.hotelchain.reservation.BusinessConflictException.class);
    }

    @Test
    void rejectsUnauthorizedUnsupportedLocalesAndStaleSourceWithoutCreatingTranslation() throws Exception {
        String token = headquarters();
        var ko = story(token);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String path = "/api/staff/website/pages/" + ko.id() + "/translations/en";
        mvc.perform(get(path).header("X-Staff-Session", "invalid-session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/website/navigation").param("locale", "fr")).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("X-Staff-Session", token).contentType("application/json")
                .content("{\"expectedSourceDraftVersion\":99,\"expectedLifecycleVersion\":1}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from website_page_translation where page_id = ?", Integer.class, ko.id())).isZero();
        UUID hotelId = UUID.randomUUID();
        jdbc.update("insert into hotel values (?, '번역 지점', '속초', 'Asia/Seoul')", hotelId);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, 'locale-branch@example.com', '지점', ?, 'BRANCH_STAFF', ?)",
                UUID.randomUUID(), new BCryptPasswordEncoder().encode("branch-test"), hotelId);
        String branch = access.login("locale-branch@example.com", "branch-test").token();
        mvc.perform(get(path).header("X-Staff-Session", branch)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("X-Staff-Session", branch).contentType("application/json")
                .content("{\"expectedSourceDraftVersion\":1,\"expectedLifecycleVersion\":1}")).andExpect(status().isForbidden());
    }

    @Test
    void exposesOnlyEnglishPublishedMenusAndCollectionCards() throws Exception {
        String token = headquarters();
        var ko = story(token);
        assertThat(translations.navigation()).isEmpty();
        assertThat(translations.collection(null, ContentKind.BRAND)).isEmpty();
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        translations.save(token, ko.id(), new SaveWebsitePageRequest(1,
                new WebsitePageDraftMetadata("locale-story", "Our story", true, 10), content("English title", "English alt"), null));
        assertThat(translations.navigation()).isEmpty();
        approveEnglish(token, ko.id(), 2);
        translations.publish(token, ko.id(), new PublishWebsitePageRequest(2, 0));
        assertThat(translations.navigation()).anySatisfy(item -> {
            assertThat(item.path()).isEqualTo("/en/brand/locale-story");
            assertThat(item.label()).isEqualTo("Our story");
        });
        assertThat(translations.collection(null, ContentKind.BRAND)).singleElement().satisfies(item -> {
            assertThat(item.path()).isEqualTo("/en/brand/locale-story");
            assertThat(item.title()).isEqualTo("English title");
        });
    }

    @Test
    void preservesEnglishPublishedPathAcrossKoreanMoveUntilEnglishIsExplicitlyRepublished() throws Exception {
        String token = headquarters();
        var ko = story(token);
        translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        approveEnglish(token, ko.id(), 1);
        translations.publish(token, ko.id(), new PublishWebsitePageRequest(1, 0));
        var moved = pages.movePublishedContentPage(token, ko.id(), new MoveWebsitePageRequest(
                UUID.fromString("12000000-0000-0000-0000-000000000005"), "locale-next", ko.draftVersion(), ko.lifecycleVersion(), ko.publishedVersion()));
        assertThat(translations.resolve("/en/brand/locale-story").content()).containsKey("blocks");
        translations.save(token, ko.id(), new SaveWebsitePageRequest(1,
                new WebsitePageDraftMetadata("locale-next", "Our story", true, 10), content("English title", "English alt"), null));
        approveEnglish(token, ko.id(), 2);
        translations.publish(token, ko.id(), new PublishWebsitePageRequest(2, 1));
        assertThat(translations.resolve("/en/brand/locale-next").path()).isEqualTo("/en/brand/locale-next");
        assertThat(translations.redirectTarget("/en/brand/locale-story")).isEqualTo("/en/brand/locale-next");
        assertThat(publicPages.resolve("/en/brand/locale-story").getStatusCode().value()).isEqualTo(301);
        assertThat(pages.pageDraft(token, ko.id())).isEqualTo(moved);
    }

    @Test
    void supportsIndependentEnglishHomeAndHotelLandingDocuments() throws Exception {
        String token = headquarters();
        var home = pages.homeDraft(token);
        translations.initialize(token, home.id(), home.draftVersion(), home.lifecycleVersion());
        approveEnglish(token, home.id(), 1);
        translations.publish(token, home.id(), new PublishWebsitePageRequest(1, 0));
        assertThat(translations.resolve("/en").type()).isEqualTo("HOME_PAGE");
        assertThat(pages.homeDraft(token)).isEqualTo(home);
        UUID hotelId = UUID.randomUUID();
        jdbc.update("insert into hotel values (?, '번역 호텔', '속초', 'Asia/Seoul')", hotelId);
        var landing = pages.landingDraft(token, hotelId);
        var content = Map.<String, Object>of("heroAssetId", WebsiteMediaService.BUNDLED_ASSET_ID.toString(), "heroImage", "/images/sokcho-coast-hero.png", "heroAlt", "한국어 해안",
                "eyebrow", "SOKCHO", "title", "한국어 호텔", "description", "호텔 설명", "arrival", Map.of("address", "주소", "checkInOut", "15:00 / 11:00", "highlight", "도착"),
                "experiences", List.of(Map.of("category", "ROOM", "title", "객실", "description", "설명")),
                "offers", List.of(Map.of("title", "오퍼", "detail", "상세", "bookingPeriod", "9월", "stayPeriod", "10월")));
        landing = pages.saveLandingDraft(token, hotelId, landing.draftVersion(), new WebsitePageDraftMetadata("locale-hotel", "호텔", true, 10), content);
        translations.initialize(token, landing.id(), landing.draftVersion(), landing.lifecycleVersion());
        var englishLandingContent = new java.util.LinkedHashMap<>(content);
        englishLandingContent.put("title", "English hotel");
        englishLandingContent.put("heroAlt", "English coast");
        translations.save(token, landing.id(), new SaveWebsitePageRequest(1, new WebsitePageDraftMetadata("locale-hotel", "English hotel", true, 10), englishLandingContent, WebsitePageConnections.empty()));
        approveEnglish(token, landing.id(), 2);
        translations.publish(token, landing.id(), new PublishWebsitePageRequest(2, 0));
        assertThat(translations.resolve("/en/stays/locale-hotel").type()).isEqualTo("HOTEL_LANDING");
        assertThat(translations.resolve("/en/stays/locale-hotel").content()).containsEntry("title", "English hotel").containsEntry("heroAlt", "English coast");
        assertThat(pages.landingDraft(token, hotelId)).isEqualTo(landing);
        landing = pages.saveLandingDraft(token, hotelId, landing.draftVersion(), new WebsitePageDraftMetadata("moved-hotel", "이동", true, 10), content);
        pages.publishPage(token, landing.id(), landing.draftVersion(), landing.publishedVersion());
        assertThat(translations.resolve("/en/stays/locale-hotel").hotelId()).isEqualTo(hotelId);
        assertThat(translations.collection("locale-hotel", ContentKind.FACILITY)).isEmpty();
    }

    @Test
    void savesAndPublishesEnglishWithoutChangingKoreanOrItsMediaUsages() throws Exception {
        String token = headquarters();
        WebsitePageDocument ko = story(token);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String path = "/api/staff/website/pages/" + ko.id() + "/translations/en";
        Map<?, ?> missing = read(mvc.perform(get(path).header("X-Staff-Session", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(missing.get("draftVersion")).isEqualTo(0);
        assertThat(jdbc.queryForObject("select count(*) from website_page_translation where page_id = ?", Integer.class, ko.id())).isZero();

        mvc.perform(post(path).header("X-Staff-Session", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedSourceDraftVersion", ko.draftVersion(), "expectedLifecycleVersion", ko.lifecycleVersion()))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/website/pages/resolve").param("path", "/en/brand/locale-story")).andExpect(status().isNotFound());
        Map<?, ?> saved = saveEnglish(mvc, path, token, 1, "English title", "English alt");
        assertThat(saved.get("draftVersion")).isEqualTo(2);
        mvc.perform(post(path + "/review/request").header("X-Staff-Session", token).contentType("application/json")
                .content("{\"expectedDraftVersion\":2,\"comment\":null}")).andExpect(status().isOk());
        mvc.perform(post(path + "/review/approve").header("X-Staff-Session", token).contentType("application/json")
                .content("{\"expectedDraftVersion\":2,\"comment\":null}")).andExpect(status().isOk());
        mvc.perform(post(path + "/publish").header("X-Staff-Session", token).contentType("application/json")
                .content("{\"expectedDraftVersion\":2,\"expectedPublishedVersion\":0}")).andExpect(status().isOk());
        String publicJson = mvc.perform(get("/api/website/pages/resolve").param("path", "/en/brand/locale-story"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicJson).contains("English title", "English alt").doesNotContain("한국어 제목");
        assertThat(pages.pageDraft(token, ko.id())).isEqualTo(ko);
        assertThat(jdbc.queryForList("select locale, document_state, alt_text from website_media_usage where page_id = ?", ko.id()))
                .hasSize(4).anySatisfy(usage -> assertThat(usage).containsEntry("locale", "en").containsEntry("alt_text", "English alt"));
        var koSaved = pages.saveContentPageDraft(token, ko.id(), ko.draftVersion(),
                new WebsitePageDraftMetadata("locale-story", "한국어 수정", true, 10), content("한국어 수정", "새 한국어 alt"));
        pages.publishPage(token, ko.id(), koSaved.draftVersion(), koSaved.publishedVersion());
        assertThat(mvc.perform(get("/api/website/pages/resolve").param("path", "/en/brand/locale-story"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(publicJson);
        mvc.perform(post(path + "/publish").header("X-Staff-Session", token).contentType("application/json")
                .content("{\"expectedDraftVersion\":2,\"expectedPublishedVersion\":0}")).andExpect(status().isConflict());
        saveEnglish(mvc, path, token, 2, "Next English draft", "Next English alt");
        assertThat(mvc.perform(get("/api/website/pages/resolve").param("path", "/en/brand/locale-story"))
                .andReturn().getResponse().getContentAsString()).isEqualTo(publicJson);
        assertThat(jdbc.queryForObject("select count(*) from website_page_translation_version where page_id = ?", Integer.class, ko.id())).isEqualTo(1);
    }

    @Test
    void validatesRelatedCyclesWithinEnglishOnly() {
        String token = headquarters();
        var a = story(token);
        var b = pages.createContentPage(token, UUID.fromString("12000000-0000-0000-0000-000000000005"),
                new WebsitePageDraftMetadata("second-story", "Second", true, 20), content("Second", "Second alt"));
        var bToA = new WebsitePageConnections(List.of(), List.of(), List.of(new WebsitePageRelation(a.id(), "RELATED", 0)));
        b = pages.saveContentPageDraft(token, b.id(), b.draftVersion(), new WebsitePageDraftMetadata("second-story", "Second", true, 20), b.draftContent(), bToA);
        b = pages.publishPage(token, b.id(), b.draftVersion(), b.publishedVersion());
        translations.initialize(token, a.id(), a.draftVersion(), a.lifecycleVersion());
        translations.initialize(token, b.id(), b.draftVersion(), b.lifecycleVersion());
        translations.save(token, b.id(), new SaveWebsitePageRequest(1, new WebsitePageDraftMetadata("second-story", "Second", true, 20), b.draftContent(), WebsitePageConnections.empty()));
        approveEnglish(token, b.id(), 2);
        translations.publish(token, b.id(), new PublishWebsitePageRequest(2, 0));
        var aToB = new WebsitePageConnections(List.of(), List.of(), List.of(new WebsitePageRelation(b.id(), "RELATED", 0)));
        translations.save(token, a.id(), new SaveWebsitePageRequest(1, new WebsitePageDraftMetadata("locale-story", "First", true, 10), a.draftContent(), aToB));
        approveEnglish(token, a.id(), 2);
        translations.publish(token, a.id(), new PublishWebsitePageRequest(2, 0));
        assertThat(pages.pageDraft(token, b.id()).draftConnections()).isEqualTo(bToA);
        UUID bId = b.id();
        Map<String, Object> bContent = b.draftContent();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.save(token, bId,
                new SaveWebsitePageRequest(2, new WebsitePageDraftMetadata("second-story", "Second", true, 20), bContent, bToA)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("순환");
    }

    @Test
    void rejectsConnectionsForHomeAndInvalidEnglishMediaWithoutMutation() {
        String token = headquarters();
        var home = pages.homeDraft(token);
        var english = translations.initialize(token, home.id(), home.draftVersion(), home.lifecycleVersion());
        var metadata = new WebsitePageDraftMetadata(home.draftMetadata().slug(), "Home", false, 0);
        var invalid = new WebsitePageConnections(List.of(UUID.randomUUID()), List.of(), List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.save(token, home.id(),
                new SaveWebsitePageRequest(1, metadata, english.draftContent(), invalid)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(translations.draft(token, home.id())).isEqualTo(english);
        var ko = story(token);
        var en = translations.initialize(token, ko.id(), ko.draftVersion(), ko.lifecycleVersion());
        var invalidMedia = Map.<String, Object>of("seo", Map.of("title", "Invalid", "description", "Invalid"), "blocks", List.of(
                Map.of("type", "HERO", "imageAssetId", UUID.randomUUID().toString(), "imageSrc", "/images/sokcho-coast-hero.png", "imageAlt", "Invalid", "title", "Invalid", "description", "Invalid")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translations.save(token, ko.id(),
                new SaveWebsitePageRequest(1, new WebsitePageDraftMetadata("locale-story", "Invalid", false, 0), invalidMedia, WebsitePageConnections.empty())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(translations.draft(token, ko.id())).isEqualTo(en);
        assertThat(pages.pageDraft(token, ko.id())).isEqualTo(ko);
    }

    private WebsitePageDocument story(String token) {
        var created = pages.createContentPage(token, UUID.fromString("12000000-0000-0000-0000-000000000005"),
                new WebsitePageDraftMetadata("locale-story", "한국어 이야기", true, 10), content("한국어 제목", "한국어 alt"));
        return pages.publishPage(token, created.id(), created.draftVersion(), created.publishedVersion());
    }

    private Map<?, ?> saveEnglish(MockMvc mvc, String path, String token, int version, String title, String alt) throws Exception {
        return read(mvc.perform(put(path).header("X-Staff-Session", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedDraftVersion", version,
                        "page", Map.of("slug", "locale-story", "menuLabel", "Our story", "menuVisible", true, "menuOrder", 10),
                        "content", content(title, alt)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void approveEnglish(String token, UUID pageId, int draftVersion) {
        translations.requestReview(token, pageId,
                new WebsiteTranslationReviewActionRequest(draftVersion, null));
        translations.approveReview(token, pageId,
                new WebsiteTranslationReviewActionRequest(draftVersion, null));
    }

    private Map<?, ?> read(String value) throws Exception { return json.readValue(value, Map.class); }

    @Test
    void neverFallsBackToKoreanWhenEnglishIsUnpublished() throws Exception {
        String token = headquarters();
        WebsitePageDocument created = pages.createContentPage(token,
                UUID.fromString("12000000-0000-0000-0000-000000000005"),
                new WebsitePageDraftMetadata("locale-story", "한국어 이야기", true, 10), content("한국어 제목", "한국어 alt"));
        pages.publishPage(token, created.id(), created.draftVersion(), created.publishedVersion());
        var mvc = MockMvcBuilders.standaloneSetup(publicPages).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/website/pages/resolve").param("path", "/brand/locale-story").param("locale", "en"))
                .andExpect(status().isNotFound());
        assertThat(pages.resolvePublished("/brand/locale-story").content()).containsKey("blocks");
    }

    private String headquarters() {
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_ADMIN')",
                UUID.randomUUID(), "locales-hq@example.com", "다국어 본사", new BCryptPasswordEncoder().encode("locale-test"));
        return access.login("locales-hq@example.com", "locale-test").token();
    }

    private Map<String, Object> content(String title, String alt) {
        return Map.of("seo", Map.of("title", title, "description", title + " description"), "blocks", List.of(
                Map.of("type", "HERO", "imageAssetId", WebsiteMediaService.BUNDLED_ASSET_ID.toString(),
                        "imageSrc", "/images/sokcho-coast-hero.png", "imageAlt", alt,
                        "eyebrow", "STAY HANEUL", "title", title, "description", title + " introduction")));
    }

    private Flyway isolatedFlyway(String schema, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private record ReviewMigrationRow(
            UUID pageId,
            String status,
            Integer reviewedDraftVersion,
            int draftVersion,
            int publishedVersion,
            Integer publishedFromDraftVersion,
            String publicationMarker) {
    }
}
