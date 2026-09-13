package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffSessionView;
import team.hotelchain.reservation.BusinessConflictException;

@SpringBootTest(properties = "website.preview.require-https=false")
@Transactional
@Import(WebsitePreviewGrantIntegrationTest.ClockConfiguration.class)
class WebsitePreviewGrantIntegrationTest {
    private static final Instant START = Instant.parse("2026-09-13T01:00:00Z");
    private static final UUID BRANCH_HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000091");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebsitePageService pages;
    @Autowired WebsitePreviewGrantService previewGrants;
    @Autowired TestClock clock;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void seed() {
        clock.set(START);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", BRANCH_HOTEL, "미리보기 지점", "서울", "Asia/Seoul");
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role)
                values (?, ?, ?, ?, 'HQ_EDITOR')
                """, UUID.randomUUID(), "preview-editor@example.com", "미리보기 편집자", encoder.encode("editor-password"));
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role)
                values (?, ?, ?, ?, 'HQ_EDITOR')
                """, UUID.randomUUID(), "preview-other@example.com", "다른 미리보기 편집자", encoder.encode("other-password"));
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role)
                values (?, ?, ?, ?, 'HQ_ADMIN')
                """, UUID.randomUUID(), "preview-admin@example.com", "미리보기 관리자", encoder.encode("admin-password"));
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role)
                values (?, ?, ?, ?, 'HQ_PUBLISHER')
                """, UUID.randomUUID(), "preview-publisher@example.com", "미리보기 게시자", encoder.encode("publisher-password"));
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, ?, ?, ?, 'BRANCH_STAFF', ?)
                """, UUID.randomUUID(), "preview-branch@example.com", "미리보기 지점 직원",
                encoder.encode("branch-password"), BRANCH_HOTEL);
    }

    @Test
    void issuesHashedVersionBoundGrantAndRevokesItWithContentRoles() {
        StaffSessionView editor = staffAccess.login("preview-editor@example.com", "editor-password");
        StaffSessionView admin = staffAccess.login("preview-admin@example.com", "admin-password");
        WebsitePageDocument home = pages.homeDraft(editor.token());

        WebsitePreviewGrantResponse issued = previewGrants.issue(
                editor.token(),
                home.id(),
                new WebsitePreviewGrantRequest("ko", home.draftVersion()));

        assertThat(issued.previewPath()).isEqualTo("/");
        assertThat(issued.previewToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.expiresAt()).isEqualTo(START.plusSeconds(600));
        assertThat(jdbc.queryForObject(
                "select token_hash from website_preview_grant where id = ?",
                String.class,
                issued.grantId())).matches("[0-9a-f]{64}").isNotEqualTo(issued.previewToken());

        previewGrants.revoke(editor.token(), issued.grantId());
        assertThat(jdbc.queryForObject(
                "select revoked_at from website_preview_grant where id = ?",
                Instant.class,
                issued.grantId())).isEqualTo(START);

        previewGrants.revoke(admin.token(), issued.grantId());
        assertThat(jdbc.queryForObject(
                "select revoked_by from website_preview_grant where id = ?",
                UUID.class,
                issued.grantId())).isEqualTo(editor.staff().id());
    }

    @Test
    void rejectsBranchStaleAndMissingEnglishGrantRequests() {
        StaffSessionView editor = staffAccess.login("preview-editor@example.com", "editor-password");
        StaffSessionView other = staffAccess.login("preview-other@example.com", "other-password");
        StaffSessionView admin = staffAccess.login("preview-admin@example.com", "admin-password");
        StaffSessionView publisher = staffAccess.login("preview-publisher@example.com", "publisher-password");
        StaffSessionView branch = staffAccess.login("preview-branch@example.com", "branch-password");
        WebsitePageDocument home = pages.homeDraft(editor.token());

        assertThatThrownBy(() -> previewGrants.issue(
                branch.token(), home.id(), new WebsitePreviewGrantRequest("ko", home.draftVersion())))
                .isInstanceOf(StaffAccessDeniedException.class);

        assertThatThrownBy(() -> previewGrants.issue(
                editor.token(), home.id(), new WebsitePreviewGrantRequest("ko", home.draftVersion() + 1)))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        exception -> assertThat(exception.code()).isEqualTo("WEBSITE_PAGE_VERSION_CONFLICT"));

        jdbc.update("delete from website_page_translation where page_id = ?", home.id());
        assertThatThrownBy(() -> previewGrants.issue(
                editor.token(), home.id(), new WebsitePreviewGrantRequest("en", home.draftVersion())))
                .isInstanceOf(WebsitePageNotFoundException.class);

        WebsitePreviewGrantResponse issued = previewGrants.issue(
                publisher.token(), home.id(), new WebsitePreviewGrantRequest("ko", home.draftVersion()));
        assertThatThrownBy(() -> previewGrants.revoke(other.token(), issued.grantId()))
                .isInstanceOf(StaffAccessDeniedException.class);

        previewGrants.revoke(admin.token(), issued.grantId());
        assertThat(jdbc.queryForObject(
                "select revoked_by from website_preview_grant where id = ?", UUID.class, issued.grantId()))
                .isEqualTo(admin.staff().id());
    }

    @Test
    void replacesOnlyTheSameIssuersActiveGrantAndCleansOldRows() {
        StaffSessionView editor = staffAccess.login("preview-editor@example.com", "editor-password");
        StaffSessionView other = staffAccess.login("preview-other@example.com", "other-password");
        StaffSessionView admin = staffAccess.login("preview-admin@example.com", "admin-password");
        WebsitePageDocument home = pages.homeDraft(editor.token());
        WebsitePreviewGrantRequest request = new WebsitePreviewGrantRequest("ko", home.draftVersion());

        WebsitePreviewGrantResponse first = previewGrants.issue(editor.token(), home.id(), request);
        WebsitePreviewGrantResponse replacement = previewGrants.issue(editor.token(), home.id(), request);
        WebsitePreviewGrantResponse otherEditors = previewGrants.issue(other.token(), home.id(), request);

        assertThat(revokedAt(first.grantId())).isEqualTo(START);
        assertThat(revokedAt(replacement.grantId())).isNull();
        assertThat(revokedAt(otherEditors.grantId())).isNull();

        clock.advance(Duration.ofHours(25));
        Instant now = clock.instant();
        UUID recentExpired = insertGrant(home.id(), admin.staff().id(), now.minus(Duration.ofHours(2)), now.minusSeconds(60), null, 1);
        UUID recentRevoked = insertGrant(home.id(), admin.staff().id(), now.minus(Duration.ofHours(2)), now.plus(Duration.ofHours(1)), now.minusSeconds(60), 2);

        StaffSessionView renewedEditor = staffAccess.login("preview-editor@example.com", "editor-password");
        WebsitePreviewGrantResponse current = previewGrants.issue(renewedEditor.token(), home.id(), request);

        assertThat(countGrant(first.grantId())).isZero();
        assertThat(countGrant(replacement.grantId())).isZero();
        assertThat(countGrant(otherEditors.grantId())).isZero();
        assertThat(countGrant(recentExpired)).isOne();
        assertThat(countGrant(recentRevoked)).isOne();
        assertThat(countGrant(current.grantId())).isOne();
    }

    @Test
    void resolvesTheSavedKoreanDraftWithoutChangingTheGrant() throws Exception {
        StaffSessionView editor = staffAccess.login("preview-editor@example.com", "editor-password");
        WebsitePageDocument home = pages.homeDraft(editor.token());
        jdbc.update("""
                update website_page
                   set draft_content = '{"seo":{"title":"저장 초안 제목"},"blocks":[]}'::jsonb,
                       draft_version = draft_version + 1
                 where id = ?
                """, home.id());
        WebsitePageDocument saved = pages.homeDraft(editor.token());
        WebsitePreviewGrantResponse grant = previewGrants.issue(
                editor.token(), home.id(), new WebsitePreviewGrantRequest("ko", saved.draftVersion()));
        int rowsBefore = countGrant(grant.grantId());

        MockMvcBuilders.webAppContextSetup(context).build().perform(get("/api/website/pages/preview")
                        .param("path", grant.previewPath())
                        .param("locale", "ko")
                        .header("X-Website-Preview", grant.previewToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Website-Preview-Expires-At", grant.expiresAt().toString()))
                .andExpect(jsonPath("$.path").value("/"))
                .andExpect(jsonPath("$.content.seo.title").value("저장 초안 제목"));

        assertThat(countGrant(grant.grantId())).isEqualTo(rowsBefore);
        assertThat(revokedAt(grant.grantId())).isNull();
    }

    @Test
    void rendersSavedKoreanAndEnglishDraftsWithoutChangingPublishedState() throws Exception {
        var editor = staffAccess.login("preview-editor@example.com", "editor-password");
        UUID home = pages.homeDraft(editor.token()).id();
        UUID landing = insertPage("HOTEL_LANDING", "DESTINATION", "/stays/preview-hotel", BRANCH_HOTEL);
        UUID content = insertPage("CONTENT_PAGE", "BRAND", "/brand/preview-story", null);
        jdbc.update("update website_page set draft_content = ?::jsonb, published_content = ?::jsonb where id = ?",
                documentJson("저장된 한국어 초안"), documentJson("한국어 공개본"), home);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (UUID pageId : List.of(home, landing, content)) {
            String path = jdbc.queryForObject("select draft_path from website_page where id = ?", String.class, pageId);
            String englishPath = path.equals("/") ? "/en" : "/en" + path;
            jdbc.update("""
                    insert into website_page_translation
                        (page_id, locale, draft_content, published_content, draft_path, published_path,
                         draft_menu_label, published_menu_label, published_version)
                    values (?, 'en', ?::jsonb, ?::jsonb, ?, ?, 'Preview', 'Published', 1)
                    on conflict (page_id, locale) do update set draft_content = excluded.draft_content,
                        published_content = excluded.published_content, draft_path = excluded.draft_path,
                        published_path = excluded.published_path
                    """, pageId, documentJson("Saved English draft"), documentJson("English published"), englishPath, englishPath);
            for (String locale : List.of("ko", "en")) {
                int version = jdbc.queryForObject("select draft_version from "
                        + (locale.equals("ko") ? "website_page where id = ?" : "website_page_translation where page_id = ?"),
                        Integer.class, pageId);
                var grant = previewGrants.issue(editor.token(), pageId, new WebsitePreviewGrantRequest(locale, version));
                var before = databaseSnapshot();
                for (int read = 0; read < 2; read++) {
                    mvc.perform(get("/api/website/pages/preview").param("path", grant.previewPath()).param("locale", locale)
                                    .header("X-Website-Preview", grant.previewToken()))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.content.blocks[0].title")
                                    .value(locale.equals("ko") ? "저장된 한국어 초안" : "Saved English draft"));
                }
                assertThat(databaseSnapshot()).isEqualTo(before);
                mvc.perform(get("/api/website/pages/resolve").param("path", grant.previewPath()).param("locale", locale))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content.blocks[0].title")
                                .value(locale.equals("ko") ? "한국어 공개본" : "English published"));
            }
        }
    }

    @Test
    void concealsMismatchedGrantsAndRejectsKnownInvalidGrantsAtTheExpiryBoundary() throws Exception {
        var editor = staffAccess.login("preview-editor@example.com", "editor-password");
        var home = pages.homeDraft(editor.token());
        var grant = previewGrants.issue(editor.token(), home.id(), new WebsitePreviewGrantRequest("ko", home.draftVersion()));
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String token : List.of("bad", "A".repeat(43))) {
            mvc.perform(get("/api/website/pages/preview").param("path", "/").param("locale", "ko")
                            .header("X-Website-Preview", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WEBSITE_PREVIEW_NOT_FOUND"));
        }
        mvc.perform(get("/api/website/pages/preview").param("path", "/brand/other").param("locale", "ko")
                        .header("X-Website-Preview", grant.previewToken()))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("WEBSITE_PREVIEW_NOT_FOUND"));
        mvc.perform(get("/api/website/pages/preview").param("path", "/").param("locale", "en")
                        .header("X-Website-Preview", grant.previewToken()))
                .andExpect(status().isNotFound());
        clock.set(grant.expiresAt().minusNanos(1));
        assertThat(previewGrants.resolve(grant.previewToken(), "/", "ko").page().id()).isEqualTo(home.id());
        clock.set(grant.expiresAt());
        mvc.perform(get("/api/website/pages/preview").param("path", "/").param("locale", "ko")
                        .header("X-Website-Preview", grant.previewToken()))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("WEBSITE_PREVIEW_UNAVAILABLE"));
        clock.set(START);
        previewGrants.revoke(editor.token(), grant.grantId());
        assertThatThrownBy(() -> previewGrants.resolve(grant.previewToken(), "/", "ko"))
                .isInstanceOf(WebsitePreviewUnavailableException.class);
    }

    @Test
    void invalidatesSavedVersionPathAndArchivedPageWithoutMutatingOnReads() {
        var editor = staffAccess.login("preview-editor@example.com", "editor-password");
        UUID pageId = insertPage("CONTENT_PAGE", "BRAND", "/brand/preview-changing", null);
        var versionGrant = previewGrants.issue(editor.token(), pageId, new WebsitePreviewGrantRequest("ko", 1));
        jdbc.update("update website_page set draft_version = 2 where id = ?", pageId);
        assertUnavailableRead(versionGrant);
        var pathGrant = previewGrants.issue(editor.token(), pageId, new WebsitePreviewGrantRequest("ko", 2));
        jdbc.update("update website_page set draft_path = '/brand/preview-moved' where id = ?", pageId);
        assertUnavailableRead(pathGrant);
        var archivedGrant = previewGrants.issue(editor.token(), pageId, new WebsitePreviewGrantRequest("ko", 2));
        jdbc.update("update website_page set lifecycle_status = 'ARCHIVED', archived_at = ? where id = ?", Timestamp.from(START), pageId);
        assertUnavailableRead(archivedGrant);
        assertThatThrownBy(() -> previewGrants.issue(editor.token(), pageId, new WebsitePreviewGrantRequest("ko", 2)))
                .isInstanceOf(WebsitePageNotFoundException.class);
    }

    private void assertUnavailableRead(WebsitePreviewGrantResponse grant) {
        var before = databaseSnapshot();
        assertThatThrownBy(() -> previewGrants.resolve(grant.previewToken(), grant.previewPath(), "ko"))
                .isInstanceOf(WebsitePreviewUnavailableException.class);
        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void concealsAbsentTokensAndInvalidLocales() throws Exception {
        var editor = staffAccess.login("preview-editor@example.com", "editor-password");
        var home = pages.homeDraft(editor.token());
        var grant = previewGrants.issue(editor.token(), home.id(), new WebsitePreviewGrantRequest("ko", home.draftVersion()));
        assertThatThrownBy(() -> previewGrants.resolve(grant.previewToken(), "/", null))
                .isInstanceOf(WebsitePreviewNotFoundException.class);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(get("/api/website/pages/preview").param("path", "/").param("locale", "ko"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WEBSITE_PREVIEW_NOT_FOUND"));
        mvc.perform(get("/api/website/pages/preview").param("path", "/").param("locale", "fr")
                        .header("X-Website-Preview", grant.previewToken()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WEBSITE_PREVIEW_NOT_FOUND"));
    }

    @Test
    void invalidatesChangedEnglishVersionAndPathWithoutWritingOnRead() {
        var editor = staffAccess.login("preview-editor@example.com", "editor-password");
        var home = pages.homeDraft(editor.token());
        jdbc.update("""
                insert into website_page_translation (page_id, locale, draft_path, draft_menu_label, draft_content)
                values (?, 'en', '/en', 'Home', ?::jsonb)
                on conflict (page_id, locale) do update set draft_path = '/en', draft_version = 1
                """, home.id(), documentJson("English saved draft"));
        var grant = previewGrants.issue(editor.token(), home.id(), new WebsitePreviewGrantRequest("en", 1));
        jdbc.update("update website_page_translation set draft_version = 2 where page_id = ? and locale = 'en'", home.id());
        var before = databaseSnapshot();
        assertThatThrownBy(() -> previewGrants.resolve(grant.previewToken(), "/en", "en"))
                .isInstanceOf(WebsitePreviewUnavailableException.class);
        assertThat(databaseSnapshot()).isEqualTo(before);
        var changedPathGrant = previewGrants.issue(editor.token(), home.id(), new WebsitePreviewGrantRequest("en", 2));
        jdbc.update("update website_page_translation set draft_path = '/en/changed' where page_id = ? and locale = 'en'", home.id());
        before = databaseSnapshot();
        assertThatThrownBy(() -> previewGrants.resolve(changedPathGrant.previewToken(), "/en", "en"))
                .isInstanceOf(WebsitePreviewUnavailableException.class);
        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    private List<String> databaseSnapshot() {
        return List.of("website_preview_grant", "website_page", "website_page_translation", "website_page_audit").stream()
                .map(table -> jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(row) order by to_jsonb(row)::text)::text, '[]') from "
                        + table + " row", String.class)).toList();
    }

    private String documentJson(String title) {
        return "{\"blocks\":[{\"type\":\"HERO\",\"title\":\"" + title + "\"}]}";
    }

    private UUID insertPage(String type, String kind, String path, UUID hotelId) {
        UUID id = UUID.randomUUID();
        UUID parent = UUID.fromString(type.equals("HOTEL_LANDING")
                ? "12000000-0000-0000-0000-000000000001" : "12000000-0000-0000-0000-000000000005");
        jdbc.update("""
                insert into website_page
                    (id, page_type, content_kind, hotel_id, parent_id, draft_slug, published_slug,
                     draft_path, published_path, draft_menu_label, published_menu_label, draft_content, published_content)
                values (?, ?, ?, ?, ?, 'preview', 'preview', ?, ?, '미리보기', '공개본', ?::jsonb, ?::jsonb)
                """, id, type, kind, hotelId, parent, path, path,
                documentJson("저장된 한국어 초안"), documentJson("한국어 공개본"));
        return id;
    }

    private Instant revokedAt(UUID grantId) {
        return jdbc.queryForObject(
                "select revoked_at from website_preview_grant where id = ?",
                (rs, rowNum) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                grantId);
    }

    private int countGrant(UUID grantId) {
        return jdbc.queryForObject(
                "select count(*) from website_preview_grant where id = ?", Integer.class, grantId);
    }

    private UUID insertGrant(UUID pageId, UUID actorId, Instant issuedAt, Instant expiresAt, Instant revokedAt, int hashSeed) {
        UUID grantId = UUID.randomUUID();
        jdbc.update("""
                insert into website_preview_grant
                    (id, token_hash, page_id, locale, draft_version, preview_path,
                     issued_by, issued_at, expires_at, revoked_at, revoked_by)
                values (?, ?, ?, 'ko', 1, '/', ?, ?, ?, ?, ?)
                """, grantId, "%064x".formatted(hashSeed), pageId, actorId,
                Timestamp.from(issuedAt), Timestamp.from(expiresAt),
                revokedAt == null ? null : Timestamp.from(revokedAt), revokedAt == null ? null : actorId);
        return grantId;
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(START);
        }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> instant;

        TestClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            instant.set(value);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
