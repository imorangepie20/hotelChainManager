package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffSessionView;
import team.hotelchain.reservation.BusinessConflictException;

@SpringBootTest
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
