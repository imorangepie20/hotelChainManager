package team.hotelchain.webcontent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class WebsitePreviewGrantService {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration CLEANUP_RETENTION = Duration.ofHours(24);
    private static final Set<String> PREVIEWABLE_TYPES = Set.of("HOME_PAGE", "HOTEL_LANDING", "CONTENT_PAGE");

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;
    private final WebsitePageService pages;
    private final WebsiteTranslationService translations;
    private final SecureRandom random = new SecureRandom();

    public WebsitePreviewGrantService(JdbcTemplate jdbc, StaffAccessService access, Clock clock,
            WebsitePageService pages, WebsiteTranslationService translations) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.pages = pages;
        this.translations = translations;
    }

    @Transactional
    public WebsitePreviewGrantResponse issue(
            String staffToken, UUID pageId, WebsitePreviewGrantRequest request) {
        StaffPrincipal actor = access.requireContentStaff(staffToken);
        if (request == null || request.expectedDraftVersion() <= 0) {
            throw new IllegalArgumentException("초안 버전은 1 이상이어야 합니다.");
        }
        String locale = WebsiteTranslationService.locale(request.locale(), null);
        DraftTarget target = lockedTarget(pageId, locale);
        if (!"ACTIVE".equals(target.lifecycleStatus()) || !PREVIEWABLE_TYPES.contains(target.pageType())) {
            throw new WebsitePageNotFoundException(pageId.toString());
        }
        if (target.draftVersion() != request.expectedDraftVersion()) {
            throw new BusinessConflictException(
                    "WEBSITE_PAGE_VERSION_CONFLICT",
                    "다른 사용자가 콘텐츠를 변경했습니다. 최신 초안을 다시 불러와 주세요.");
        }

        Instant now = clock.instant();
        cleanup(now.minus(CLEANUP_RETENTION));
        jdbc.update("""
                update website_preview_grant
                   set revoked_at = ?, revoked_by = ?
                 where issued_by = ? and page_id = ? and locale = ?
                   and revoked_at is null and expires_at > ?
                """, timestamp(now), actor.id(), actor.id(), pageId, locale, timestamp(now));

        UUID grantId = UUID.randomUUID();
        String rawToken = newToken();
        Instant expiresAt = now.plus(TTL);
        jdbc.update("""
                insert into website_preview_grant
                    (id, token_hash, page_id, locale, draft_version, preview_path,
                     issued_by, issued_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, grantId, hash(rawToken), pageId, locale, target.draftVersion(), target.previewPath(),
                actor.id(), timestamp(now), timestamp(expiresAt));
        return new WebsitePreviewGrantResponse(grantId, rawToken, target.previewPath(), expiresAt);
    }

    @Transactional
    public void revoke(String staffToken, UUID grantId) {
        StaffPrincipal actor = access.requireContentStaff(staffToken);
        GrantOwner owner = jdbc.query("""
                select issued_by, expires_at, revoked_at
                  from website_preview_grant
                 where id = ?
                 for update
                """, rs -> rs.next()
                        ? new GrantOwner(
                                rs.getObject("issued_by", UUID.class),
                                rs.getTimestamp("expires_at").toInstant(),
                                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant())
                        : null,
                grantId);
        if (owner == null) {
            throw new WebsitePreviewNotFoundException();
        }
        if (!owner.issuedBy().equals(actor.id()) && !"HQ_ADMIN".equals(actor.role())) {
            throw new StaffAccessDeniedException();
        }
        Instant now = clock.instant();
        if (owner.revokedAt() != null || !owner.expiresAt().isAfter(now)) {
            return;
        }
        jdbc.update("""
                update website_preview_grant
                   set revoked_at = ?, revoked_by = ?
                 where id = ? and revoked_at is null and expires_at > ?
                """, timestamp(now), actor.id(), grantId, timestamp(now));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WebsitePreviewResult resolve(String rawToken, String path, String locale) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")
                || locale == null || !Set.of("ko", "en").contains(locale)) {
            throw new WebsitePreviewNotFoundException();
        }
        Grant grant = jdbc.query("""
                select page_id, locale, draft_version, preview_path, expires_at, revoked_at
                  from website_preview_grant where token_hash = ?
                """, rs -> rs.next() ? new Grant(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getInt(3), rs.getString(4), rs.getTimestamp(5).toInstant(),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()) : null,
                hash(rawToken));
        if (grant == null || !grant.locale().equals(locale) || !grant.path().equals(path)) {
            throw new WebsitePreviewNotFoundException();
        }
        if (grant.revokedAt() != null || !grant.expiresAt().isAfter(clock.instant())) {
            throw new WebsitePreviewUnavailableException();
        }
        PublishedWebsitePage page = "en".equals(locale)
                ? translations.previewDraft(grant.pageId(), grant.version(), grant.path())
                : pages.previewDraft(grant.pageId(), grant.version(), grant.path());
        return new WebsitePreviewResult(page, grant.expiresAt());
    }

    private DraftTarget lockedTarget(UUID pageId, String locale) {
        if ("en".equals(locale)) {
            DraftTarget target = jdbc.query("""
                    select page.page_type, page.lifecycle_status,
                           translation.draft_version, translation.draft_path
                      from website_page page
                      join website_page_translation translation
                        on translation.page_id = page.id and translation.locale = 'en'
                     where page.id = ?
                     for update of page, translation
                    """, rs -> rs.next()
                            ? new DraftTarget(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4))
                            : null,
                    pageId);
            if (target == null) {
                throw new WebsitePageNotFoundException(pageId.toString());
            }
            return target;
        }
        DraftTarget target = jdbc.query("""
                select page_type, lifecycle_status, draft_version, draft_path
                  from website_page
                 where id = ?
                 for update
                """, rs -> rs.next()
                        ? new DraftTarget(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4))
                        : null,
                pageId);
        if (target == null) {
            throw new WebsitePageNotFoundException(pageId.toString());
        }
        return target;
    }

    private void cleanup(Instant cutoff) {
        jdbc.update("""
                delete from website_preview_grant
                 where expires_at < ? or revoked_at < ?
                """, timestamp(cutoff), timestamp(cutoff));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record DraftTarget(String pageType, String lifecycleStatus, int draftVersion, String previewPath) {
    }

    private record GrantOwner(UUID issuedBy, Instant expiresAt, Instant revokedAt) {
    }

    private record Grant(UUID pageId, String locale, int version, String path,
            Instant expiresAt, Instant revokedAt) {}
}
