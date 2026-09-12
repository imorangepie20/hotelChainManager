package team.hotelchain.webcontent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessService;

/** Additive English storage; legacy page APIs remain the Korean authority. */
@Service
public class WebsiteTranslationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final WebsitePageService pages;
    private final StaffAccessService access;
    private final WebsiteMediaReferenceService media;
    private final ContentPageValidator contentValidator;
    private final WebsitePageConnectionValidator connectionValidator;

    public WebsiteTranslationService(JdbcTemplate jdbc, ObjectMapper json, WebsitePageService pages,
            StaffAccessService access, WebsiteMediaReferenceService media, ContentPageValidator contentValidator,
            WebsitePageConnectionValidator connectionValidator) {
        this.jdbc = jdbc; this.json = json; this.pages = pages; this.access = access;
        this.media = media; this.contentValidator = contentValidator; this.connectionValidator = connectionValidator;
    }

    public static String locale(String requested, String path) {
        String result = requested == null ? (path != null && (path.equals("/en") || path.startsWith("/en/")) ? "en" : "ko") : requested;
        if (!List.of("ko", "en").contains(result)) throw new IllegalArgumentException("지원 언어는 ko 또는 en입니다.");
        return result;
    }

    public static String englishPath(String path) { return path.equals("/") ? "/en" : "/en" + path; }

    @Transactional(readOnly = true)
    public WebsitePageDocument draft(String token, UUID pageId) {
        WebsitePageDocument source = pages.pageDraft(token, pageId);
        requireEditableType(source);
        return document(source, translation(pageId));
    }

    @Transactional(readOnly = true)
    public WebsiteTranslationReviewState review(String token, UUID pageId) {
        WebsitePageDocument source = pages.pageDraft(token, pageId);
        requireEditableType(source);
        return jdbc.query("""
                select review_status, reviewed_draft_version
                from website_page_translation
                where page_id = ? and locale = 'en'
                """, rs -> {
                    if (!rs.next()) {
                        return new WebsiteTranslationReviewState(WebsiteTranslationReviewStatus.DRAFT, null, List.of());
                    }
                    var events = jdbc.query("""
                            select event.id, event.action, event.draft_version, event.actor_id,
                                staff.display_name, event.created_at, event.comment
                            from website_translation_review_event event
                            left join staff_member staff on staff.id = event.actor_id
                            where event.page_id = ? and event.locale = 'en'
                            order by event.created_at desc, event.id desc
                            limit 50
                            """, (eventRow, rowNumber) -> new WebsiteTranslationReviewEvent(
                                    eventRow.getLong("id"), eventRow.getString("action"),
                                    eventRow.getInt("draft_version"), eventRow.getObject("actor_id", UUID.class),
                                    eventRow.getString("display_name"), eventRow.getTimestamp("created_at").toInstant(),
                                    eventRow.getString("comment")), pageId);
                    return new WebsiteTranslationReviewState(
                            WebsiteTranslationReviewStatus.valueOf(rs.getString("review_status")),
                            rs.getObject("reviewed_draft_version", Integer.class), events);
                }, pageId);
    }

    @Transactional
    public WebsiteTranslationReviewState requestReview(String token, UUID pageId,
            WebsiteTranslationReviewActionRequest request) {
        UUID actor = access.requireHeadquarters(token).id();
        WebsitePageDocument source = lockedSource(token, pageId);
        Translation current = requiredLockedTranslation(pageId);
        String comment = optionalReviewComment(request);
        requireCurrentReviewVersion(request.expectedDraftVersion(), current);
        requireReviewStatus(current, WebsiteTranslationReviewStatus.DRAFT);
        normalize(source, current.draftContent(), current.draftConnections(), true);
        rejectCollision(pageId, current.draftMetadata().path());
        jdbc.update("""
                update website_page_translation
                set review_status = 'IN_REVIEW', reviewed_draft_version = draft_version,
                    updated_at = current_timestamp, updated_by = ?
                where page_id = ? and locale = 'en'
                """, actor, pageId);
        reviewEvent(pageId, actor, "REVIEW_REQUESTED", current.draftVersion(), comment);
        return review(token, pageId);
    }

    @Transactional
    public WebsiteTranslationReviewState approveReview(String token, UUID pageId,
            WebsiteTranslationReviewActionRequest request) {
        UUID actor = access.requireHeadquarters(token).id();
        lockedSource(token, pageId);
        Translation current = requiredLockedTranslation(pageId);
        String comment = optionalReviewComment(request);
        requireCurrentReviewVersion(request.expectedDraftVersion(), current);
        requireReviewStatus(current, WebsiteTranslationReviewStatus.IN_REVIEW);
        if (!Integer.valueOf(current.draftVersion()).equals(current.reviewedDraftVersion())) {
            throw reviewStale();
        }
        jdbc.update("""
                update website_page_translation
                set review_status = 'APPROVED', updated_at = current_timestamp, updated_by = ?
                where page_id = ? and locale = 'en'
                """, actor, pageId);
        reviewEvent(pageId, actor, "APPROVED", current.draftVersion(), comment);
        return review(token, pageId);
    }

    @Transactional
    public WebsiteTranslationReviewState rejectReview(String token, UUID pageId,
            WebsiteTranslationReviewActionRequest request) {
        UUID actor = access.requireHeadquarters(token).id();
        lockedSource(token, pageId);
        Translation current = requiredLockedTranslation(pageId);
        String comment = requiredRejectionComment(request);
        requireCurrentReviewVersion(request.expectedDraftVersion(), current);
        requireReviewStatus(current, WebsiteTranslationReviewStatus.IN_REVIEW);
        if (!Integer.valueOf(current.draftVersion()).equals(current.reviewedDraftVersion())) {
            throw reviewStale();
        }
        jdbc.update("""
                update website_page_translation
                set review_status = 'DRAFT', reviewed_draft_version = null,
                    updated_at = current_timestamp, updated_by = ?
                where page_id = ? and locale = 'en'
                """, actor, pageId);
        reviewEvent(pageId, actor, "REJECTED", current.draftVersion(), comment);
        return review(token, pageId);
    }

    @Transactional
    public WebsitePageDocument initialize(String token, UUID pageId, int sourceVersion, int lifecycleVersion) {
        UUID actor = access.requireHeadquarters(token).id();
        WebsitePageDocument source = lockedSource(token, pageId);
        if (sourceVersion <= 0 || lifecycleVersion <= 0) throw new IllegalArgumentException("원본 초안과 상태 버전이 필요합니다.");
        if (source.draftVersion() != sourceVersion || source.lifecycleVersion() != lifecycleVersion || translation(pageId) != null) throw stale();
        Map<String, Object> content = normalize(source, source.draftContent(), source.draftConnections(), false);
        String path = englishPath(source.draftMetadata().path());
        rejectCollision(pageId, path);
        try {
            jdbc.update("""
                    insert into website_page_translation (page_id, locale, draft_content, draft_connections, draft_path,
                        draft_menu_label, draft_menu_visible, draft_menu_order, updated_by)
                    values (?, 'en', ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                    """, pageId, stringify(content), stringify(source.draftConnections()), path,
                    source.draftMetadata().menuLabel(), source.draftMetadata().menuVisible(), source.draftMetadata().menuOrder(), actor);
        } catch (DuplicateKeyException exception) { throw stale(); }
        media.synchronize(pageId, source.pageType(), "en", "DRAFT", content);
        audit(pageId, actor, "DRAFT_SAVED", Map.of("locale", "en", "sourceLocale", "ko", "sourceDraftVersion", sourceVersion));
        return document(source, translation(pageId));
    }

    @Transactional
    public WebsitePageDocument save(String token, UUID pageId, SaveWebsitePageRequest request) {
        UUID actor = access.requireHeadquarters(token).id();
        WebsitePageDocument source = lockedSource(token, pageId);
        Translation current = requiredLockedTranslation(pageId);
        if (request.expectedDraftVersion() <= 0) throw new IllegalArgumentException("초안 버전은 1 이상이어야 합니다.");
        if (request.expectedDraftVersion() != current.draftVersion()) throw stale();
        WebsitePageDraftMetadata metadata = request.page();
        if (metadata == null || !source.draftMetadata().slug().equals(metadata.slug())) {
            throw new IllegalArgumentException("영어 구조 슬러그는 한국어 페이지 구조를 사용합니다.");
        }
        if (metadata.menuLabel() == null || metadata.menuLabel().isBlank() || metadata.menuLabel().length() > 100 || metadata.menuOrder() < 0) {
            throw new IllegalArgumentException("메뉴명은 1~100자, 메뉴 순서는 0 이상이어야 합니다.");
        }
        WebsitePageConnections connections = request.connections() == null ? current.draftConnections() : request.connections();
        Map<String, Object> content = normalize(source, request.content(), connections, false);
        String path = englishPath(source.draftMetadata().path());
        rejectCollision(pageId, path);
        jdbc.update("""
                update website_page_translation set draft_content = ?::jsonb, draft_connections = ?::jsonb,
                    draft_path = ?, draft_menu_label = ?, draft_menu_visible = ?, draft_menu_order = ?,
                    draft_version = draft_version + 1, review_status = 'DRAFT', reviewed_draft_version = null,
                    updated_at = current_timestamp, updated_by = ?
                where page_id = ? and locale = 'en'
                """, stringify(content), stringify(connections), path, metadata.menuLabel(),
                !"HOME_PAGE".equals(source.pageType()) && metadata.menuVisible(), metadata.menuOrder(), actor, pageId);
        if (List.of(WebsiteTranslationReviewStatus.IN_REVIEW, WebsiteTranslationReviewStatus.APPROVED,
                WebsiteTranslationReviewStatus.PUBLISHED).contains(current.reviewStatus())) {
            reviewEvent(pageId, actor, "APPROVAL_INVALIDATED", current.draftVersion() + 1, null);
        }
        media.synchronize(pageId, source.pageType(), "en", "DRAFT", content);
        audit(pageId, actor, "DRAFT_SAVED", Map.of("locale", "en", "path", path));
        return document(source, translation(pageId));
    }

    @Transactional
    public WebsitePageDocument publish(String token, UUID pageId, PublishWebsitePageRequest request) {
        UUID actor = access.requireHeadquarters(token).id();
        WebsitePageDocument source = lockedSource(token, pageId);
        Translation current = requiredLockedTranslation(pageId);
        if (request.expectedDraftVersion() <= 0 || request.expectedPublishedVersion() < 0) throw new IllegalArgumentException("언어별 초안·발행 버전이 올바르지 않습니다.");
        if (request.expectedDraftVersion() != current.draftVersion() || request.expectedPublishedVersion() != current.publishedVersion()) throw stale();
        if (current.reviewStatus() != WebsiteTranslationReviewStatus.APPROVED) {
            throw new BusinessConflictException("WEBSITE_TRANSLATION_NOT_APPROVED",
                    "현재 영어 초안을 승인한 뒤 발행해 주세요.");
        }
        if (!Integer.valueOf(current.draftVersion()).equals(current.reviewedDraftVersion())) {
            throw new BusinessConflictException("WEBSITE_TRANSLATION_REVIEW_STALE",
                    "승인된 초안이 변경되었습니다. 다시 검토를 요청해 주세요.");
        }
        normalize(source, current.draftContent(), current.draftConnections(), true);
        rejectCollision(pageId, current.draftMetadata().path());
        if (current.publishedMetadata() != null && !current.publishedMetadata().path().equals(current.draftMetadata().path())) {
            createRedirect(pageId, actor, current.publishedMetadata().path(), current.draftMetadata().path());
        }
        jdbc.update("""
                update website_page_translation set published_content = draft_content, published_connections = draft_connections,
                    published_path = draft_path, published_menu_label = draft_menu_label,
                    published_menu_visible = draft_menu_visible, published_menu_order = draft_menu_order,
                    published_version = published_version + 1, published_from_draft_version = draft_version,
                    review_status = 'PUBLISHED',
                    updated_at = current_timestamp, updated_by = ? where page_id = ? and locale = 'en'
                """, actor, pageId);
        Translation published = translation(pageId);
        media.synchronize(pageId, source.pageType(), "en", "PUBLISHED", published.publishedContent());
        WebsitePageDocument result = document(source, published);
        jdbc.update("insert into website_page_translation_version (page_id, locale, version, page_snapshot, published_by) values (?, 'en', ?, ?::jsonb, ?)",
                pageId, published.publishedVersion(), stringify(result), actor);
        reviewEvent(pageId, actor, "PUBLISHED", current.draftVersion(), null);
        audit(pageId, actor, "PUBLISHED", Map.of("locale", "en", "path", published.publishedMetadata().path()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<WebContentVersion> versions(String token, UUID pageId) {
        draft(token, pageId);
        return jdbc.query("select version, published_at from website_page_translation_version where page_id = ? and locale = 'en' order by version desc",
                (rs, rowNum) -> new WebContentVersion(rs.getInt(1), rs.getTimestamp(2).toInstant()), pageId);
    }

    @Transactional(readOnly = true)
    public PublishedWebsitePage resolve(String path) {
        String canonical = path != null && (path.equals("/en") || path.startsWith("/en/")) ? path : path == null ? "" : englishPath(path);
        if (!canonical.matches("/en(?:/[a-z0-9]+(?:-[a-z0-9]+)*){0,4}")) throw new WebsitePageNotFoundException(canonical);
        List<PublishedRow> matches = publishedRows().stream().filter(row -> canonical.equals(row.translation().publishedMetadata().path())).toList();
        if (matches.isEmpty()) throw new WebsitePageNotFoundException(canonical);
        PublishedRow row = matches.getFirst();
        return new PublishedWebsitePage(row.id(), row.type(), row.kind(), canonical, row.hotelId(),
                row.translation().publishedContent(), row.translation().publishedConnections());
    }

    @Transactional(readOnly = true)
    public String redirectTarget(String source) {
        return jdbc.query("""
                select redirect.target_path from website_translation_redirect redirect
                join website_page page on page.id = redirect.page_id
                join website_page_translation translation on translation.page_id = page.id
                where redirect.source_path = ? and page.lifecycle_status = 'ACTIVE'
                    and translation.published_content <> '{}'::jsonb and translation.published_path = redirect.target_path
                """, rs -> rs.next() ? rs.getString(1) : null, source);
    }

    @Transactional(readOnly = true)
    public List<WebsiteNavigationItem> navigation() {
        List<PublishedRow> rows = publishedRows().stream().filter(row -> row.translation().publishedMetadata().menuVisible() && !row.type().equals("HOME_PAGE")).toList();
        HashSet<UUID> ids = new HashSet<>(rows.stream().map(PublishedRow::id).toList());
        // Untranslated sections are not fabricated from Korean labels; publishable children become roots.
        return rows.stream().filter(row -> !ids.contains(row.parentId())).map(row -> navigationItem(row, rows)).toList();
    }

    @Transactional(readOnly = true)
    public List<WebsiteContentCollectionItem> collection(String hotelSlug, ContentKind kind) {
        if (kind == null || !ContentKind.contentPageKinds().contains(kind)) throw new IllegalArgumentException("상세 콘텐츠 종류가 올바르지 않습니다.");
        UUID hotelId = hotelSlug == null || hotelSlug.isBlank() ? null : hotelId(hotelSlug);
        if (kind.requiresHotel() && hotelId == null) throw new IllegalArgumentException("지점이 필요합니다.");
        List<WebsiteContentCollectionItem> result = new ArrayList<>();
        for (PublishedRow row : publishedRows()) {
            if (row.kind() != kind || !row.type().equals("CONTENT_PAGE")) continue;
            if (kind.requiresHotel() && !hotelId.equals(row.hotelId())) continue;
            if (kind == ContentKind.PROMOTION && hotelId != null && !row.translation().publishedConnections().targetHotelIds().contains(hotelId)) continue;
            if (kind == ContentKind.GUIDE && row.hotelId() != null && (hotelId == null || !hotelId.equals(row.hotelId()))) continue;
            if (!(row.translation().publishedContent().get("blocks") instanceof List<?> blocks)) continue;
            for (Object value : blocks) {
                if (!(value instanceof Map<?, ?> block) || !"HERO".equals(block.get("type"))) continue;
                UUID selectedHotel = row.hotelId() == null ? hotelId : row.hotelId();
                result.add(new WebsiteContentCollectionItem(row.id(), kind, row.translation().publishedMetadata().path(),
                        (String) block.get("title"), (String) block.get("description"), (String) block.get("imageSrc"),
                        selectedHotel == null ? null : hotelSlug(selectedHotel)));
            }
        }
        return result;
    }

    private WebsiteNavigationItem navigationItem(PublishedRow row, List<PublishedRow> rows) {
        var metadata = row.translation().publishedMetadata();
        return new WebsiteNavigationItem(row.id(), row.hotelId(), metadata.menuLabel(), metadata.path(),
                rows.stream().filter(child -> row.id().equals(child.parentId())).map(child -> navigationItem(child, rows)).toList());
    }

    private List<PublishedRow> publishedRows() {
        return jdbc.query("""
                select page.id, page.parent_id, page.page_type, page.content_kind, page.hotel_id, translation.*
                from website_page_translation translation join website_page page on page.id = translation.page_id
                where translation.locale = 'en' and page.lifecycle_status = 'ACTIVE' and translation.published_content <> '{}'::jsonb
                order by translation.published_menu_order, translation.published_path
                """, (rs, rowNum) -> new PublishedRow(rs.getObject("id", UUID.class), rs.getObject("parent_id", UUID.class),
                rs.getString("page_type"), ContentKind.valueOf(rs.getString("content_kind")), rs.getObject("hotel_id", UUID.class), row(rs)));
    }

    private UUID hotelId(String slug) {
        UUID result = jdbc.query("""
                select page.hotel_id from website_page page join website_page_translation translation on translation.page_id = page.id
                where page.page_type = 'HOTEL_LANDING' and page.lifecycle_status = 'ACTIVE' and translation.locale = 'en'
                    and translation.published_content <> '{}'::jsonb and translation.published_path = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, "/en/stays/" + slug);
        if (result == null) throw new WebsitePageNotFoundException(slug);
        return result;
    }

    private String hotelSlug(UUID id) {
        return jdbc.query("""
                select translation.published_path from website_page page join website_page_translation translation on translation.page_id = page.id
                where page.page_type = 'HOTEL_LANDING' and page.hotel_id = ? and page.lifecycle_status = 'ACTIVE'
                    and translation.locale = 'en' and translation.published_content <> '{}'::jsonb
                """, rs -> { if (!rs.next()) return null; String path = rs.getString(1); return path.substring(path.lastIndexOf('/') + 1); }, id);
    }

    private WebsitePageDocument lockedSource(String token, UUID id) {
        access.requireHeadquarters(token);
        UUID found = jdbc.query("select id from website_page where id = ? for update", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, id);
        if (found == null) throw new WebsitePageNotFoundException(id.toString());
        var source = pages.pageDraft(token, id);
        requireEditableType(source);
        if (!source.lifecycleStatus().equals("ACTIVE")) throw new BusinessConflictException("WEBSITE_PAGE_ARCHIVED", "보관된 페이지는 번역을 저장·발행할 수 없습니다.");
        return source;
    }

    private void requireEditableType(WebsitePageDocument source) {
        if (!List.of("HOME_PAGE", "HOTEL_LANDING", "CONTENT_PAGE").contains(source.pageType())) throw new IllegalArgumentException("번역할 콘텐츠 페이지가 아닙니다.");
    }

    private Map<String, Object> normalize(WebsitePageDocument source, Map<String, Object> content, WebsitePageConnections connections, boolean publishing) {
        if (!source.pageType().equals("CONTENT_PAGE") && !WebsitePageConnections.empty().equals(connections)) {
            throw new IllegalArgumentException("홈과 지점 랜딩에는 콘텐츠 연결을 둘 수 없습니다.");
        }
        Map<String, Object> normalized = source.pageType().equals("HOTEL_LANDING") ? media.normalizeLandingContent(content) : media.normalizeStructuredContent(content);
        if (source.pageType().equals("HOTEL_LANDING")) WebContentService.validateLandingContent(normalized);
        else if (source.pageType().equals("HOME_PAGE")) contentValidator.validate(normalized);
        else {
            contentValidator.validate(source.contentKind(), normalized);
            connectionValidator.validate(source.contentKind(), source.hotelId(), connections);
            connectionValidator.validateRelatedPageTargets(source.id(), connections, false);
            for (WebsitePageRelation relation : connections.relatedPages()) {
                if (publishing && publishedRows().stream().noneMatch(row -> row.id().equals(relation.targetPageId()))) {
                    throw new IllegalArgumentException("관련 페이지의 영어 발행본이 필요합니다.");
                }
                if (hasTranslationPath(source.id(), relation.targetPageId(), publishing, new HashSet<>())) throw new IllegalArgumentException("영어 관련 페이지 연결은 순환할 수 없습니다.");
            }
        }
        return normalized;
    }

    private boolean hasTranslationPath(UUID target, UUID current, boolean published, HashSet<UUID> visited) {
        if (target.equals(current)) return true;
        if (!visited.add(current)) return false;
        Translation translation = translation(current);
        if (translation == null) return false;
        WebsitePageConnections connections = published ? translation.publishedConnections() : translation.draftConnections();
        return connections.relatedPages().stream().anyMatch(relation -> hasTranslationPath(target, relation.targetPageId(), published, visited));
    }

    private void rejectCollision(UUID pageId, String path) {
        Integer count = jdbc.queryForObject("select count(*) from website_page_translation where page_id <> ? and (draft_path = ? or published_path = ?)", Integer.class, pageId, path, path);
        Integer redirect = jdbc.queryForObject("select count(*) from website_translation_redirect where source_path = ?", Integer.class, path);
        if (count > 0 || redirect > 0) throw new BusinessConflictException("WEBSITE_PAGE_PATH_CONFLICT", "이미 사용 중인 영어 경로입니다.");
    }

    private void createRedirect(UUID id, UUID actor, String source, String target) {
        Integer count = jdbc.queryForObject("select count(*) from website_translation_redirect where source_path = ? or target_path = ? or source_path = ?", Integer.class, source, source, target);
        if (count > 0) throw new BusinessConflictException("WEBSITE_PAGE_PATH_CONFLICT", "영어 redirect 순환·다단계 연결은 허용하지 않습니다.");
        jdbc.update("insert into website_translation_redirect (source_path, target_path, page_id, created_by) values (?, ?, ?, ?)", source, target, id, actor);
    }

    private WebsitePageDocument document(WebsitePageDocument source, Translation translation) {
        if (translation == null) return new WebsitePageDocument(source.id(), source.pageType(), source.contentKind(), source.hotelId(),
                Map.of(), source.draftConnections(), 0,
                new WebsitePageMetadata(source.draftMetadata().slug(), englishPath(source.draftMetadata().path()), "", false, 0),
                Map.of(), WebsitePageConnections.empty(), 0, null, source.lifecycleStatus(), source.lifecycleVersion());
        return new WebsitePageDocument(source.id(), source.pageType(), source.contentKind(), source.hotelId(),
                translation.draftContent(), translation.draftConnections(), translation.draftVersion(),
                new WebsitePageMetadata(source.draftMetadata().slug(), translation.draftMetadata().path(), translation.draftMetadata().menuLabel(), translation.draftMetadata().menuVisible(), translation.draftMetadata().menuOrder()),
                translation.publishedContent(), translation.publishedConnections(), translation.publishedVersion(), translation.publishedMetadata(), source.lifecycleStatus(), source.lifecycleVersion());
    }

    private Translation requiredLockedTranslation(UUID id) {
        Translation current = jdbc.query("select * from website_page_translation where page_id = ? and locale = 'en' for update",
                rs -> rs.next() ? row(rs) : null, id);
        if (current == null) throw new BusinessConflictException("WEBSITE_TRANSLATION_MISSING", "먼저 영어 번역 초안을 가져와 주세요.");
        return current;
    }

    private Translation translation(UUID id) {
        return jdbc.query("select * from website_page_translation where page_id = ? and locale = 'en'", rs -> rs.next() ? row(rs) : null, id);
    }

    private Translation row(java.sql.ResultSet rs) throws java.sql.SQLException {
        String draftPath = rs.getString("draft_path"), publishedPath = rs.getString("published_path");
        return new Translation(read(rs.getString("draft_content")), read(rs.getString("published_content")),
                readConnections(rs.getString("draft_connections")), readConnections(rs.getString("published_connections")),
                rs.getInt("draft_version"), rs.getInt("published_version"),
                new WebsitePageMetadata(draftPath.substring(draftPath.lastIndexOf('/') + 1), draftPath, rs.getString("draft_menu_label"), rs.getBoolean("draft_menu_visible"), rs.getInt("draft_menu_order")),
                publishedPath == null ? null : new WebsitePageMetadata(publishedPath.substring(publishedPath.lastIndexOf('/') + 1), publishedPath, rs.getString("published_menu_label"), rs.getBoolean("published_menu_visible"), rs.getInt("published_menu_order")),
                WebsiteTranslationReviewStatus.valueOf(rs.getString("review_status")),
                rs.getObject("reviewed_draft_version", Integer.class));
    }

    private Map<String, Object> read(String value) {
        try { return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { throw new IllegalStateException("번역 문서를 읽을 수 없습니다.", exception); }
    }

    private WebsitePageConnections readConnections(String value) {
        try { return json.readValue(value, WebsitePageConnections.class); }
        catch (Exception exception) { throw new IllegalStateException("번역 연결을 읽을 수 없습니다.", exception); }
    }

    private String stringify(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("번역 문서를 저장할 수 없습니다.", exception); }
    }

    private void audit(UUID id, UUID actor, String action, Map<String, Object> details) {
        jdbc.update("insert into website_page_audit (page_id, action, actor_id, details) values (?, ?, ?, ?::jsonb)", id, action, actor, stringify(details));
    }

    private String optionalReviewComment(WebsiteTranslationReviewActionRequest request) {
        if (request == null || request.expectedDraftVersion() <= 0) {
            throw new IllegalArgumentException("초안 버전은 1 이상이어야 합니다.");
        }
        String comment = request.comment() == null ? null : request.comment().trim();
        if (comment != null && comment.length() > 2000) {
            throw new IllegalArgumentException("검토 코멘트는 2,000자 이하여야 합니다.");
        }
        return comment == null || comment.isBlank() ? null : comment;
    }

    private String requiredRejectionComment(WebsiteTranslationReviewActionRequest request) {
        String comment = optionalReviewComment(request);
        if (comment == null) {
            throw new WebsiteTranslationReviewValidationException(
                    "WEBSITE_TRANSLATION_REJECTION_REASON_REQUIRED", "반려 사유를 1~2,000자로 입력해 주세요.");
        }
        return comment;
    }

    private void requireCurrentReviewVersion(int expectedDraftVersion, Translation current) {
        if (expectedDraftVersion != current.draftVersion()) throw reviewStale();
    }

    private void requireReviewStatus(Translation current, WebsiteTranslationReviewStatus expected) {
        if (current.reviewStatus() != expected) {
            throw new BusinessConflictException("WEBSITE_TRANSLATION_REVIEW_STATE_CONFLICT",
                    "현재 검토 상태에서는 요청한 작업을 수행할 수 없습니다. 최신 상태를 확인해 주세요.");
        }
    }

    private void reviewEvent(UUID pageId, UUID actor, String action, int draftVersion, String comment) {
        jdbc.update("""
                insert into website_translation_review_event
                    (page_id, locale, action, draft_version, actor_id, comment)
                values (?, 'en', ?, ?, ?, ?)
                """, pageId, action, draftVersion, actor, comment);
    }

    private BusinessConflictException reviewStale() {
        return new BusinessConflictException("WEBSITE_TRANSLATION_REVIEW_STALE",
                "번역 초안 version이 변경되었습니다. 최신 페이지를 다시 불러와 주세요.");
    }

    private BusinessConflictException stale() { return new BusinessConflictException("WEBSITE_TRANSLATION_CONFLICT", "번역 또는 원본이 변경되었습니다. 최신 페이지를 다시 불러와 주세요."); }

    private record Translation(Map<String, Object> draftContent, Map<String, Object> publishedContent,
            WebsitePageConnections draftConnections, WebsitePageConnections publishedConnections, int draftVersion, int publishedVersion,
            WebsitePageMetadata draftMetadata, WebsitePageMetadata publishedMetadata,
            WebsiteTranslationReviewStatus reviewStatus, Integer reviewedDraftVersion) {}
    private record PublishedRow(UUID id, UUID parentId, String type, ContentKind kind, UUID hotelId, Translation translation) {}
}
