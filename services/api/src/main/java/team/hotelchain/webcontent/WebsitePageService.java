package team.hotelchain.webcontent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class WebsitePageService {
    private static final UUID STAYS_SECTION_ID = UUID.fromString("12000000-0000-0000-0000-000000000001");
    private static final UUID HOME_PAGE_ID = UUID.fromString("12000000-0000-0000-0000-000000000006");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern PATH = Pattern.compile("(?:/|/[a-z0-9]+(?:-[a-z0-9]+)*(?:/[a-z0-9]+(?:-[a-z0-9]+)*)*)");
    private static final List<String> RESERVED_SLUGS = List.of("api", "booking", "reservations", "stays");
    private static final WebsitePageDraftMetadata HOME_METADATA = new WebsitePageDraftMetadata("home", "홈", false, 0);
    private static final String PAGE_COLUMNS = """
            id, hotel_id, parent_id, page_type, content_kind, draft_slug, draft_path, draft_menu_label, draft_menu_visible, draft_menu_order,
            draft_content::text as draft_content, draft_version, published_slug, published_path, published_menu_label,
            published_menu_visible, published_menu_order, published_content::text as published_content, published_version,
            published_from_draft_version, lifecycle_status, lifecycle_version
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StaffAccessService access;
    private final ContentPageValidator contentPages;
    private final WebsiteMediaReferenceService mediaReferences;
    private final WebsitePageConnectionValidator connections;

    public WebsitePageService(JdbcTemplate jdbc, ObjectMapper json, StaffAccessService access, ContentPageValidator contentPages,
            WebsiteMediaReferenceService mediaReferences, WebsitePageConnectionValidator connections) {
        this.jdbc = jdbc;
        this.json = json;
        this.access = access;
        this.contentPages = contentPages;
        this.mediaReferences = mediaReferences;
        this.connections = connections;
    }

    @Transactional
    public WebsitePageDocument landingDraft(String token, UUID hotelId) {
        access.requireHeadquarters(token);
        return document(ensureLanding(hotelId));
    }

    @Transactional
    public WebsitePageDocument saveLandingDraft(String token, UUID hotelId, int expectedDraftVersion,
            WebsitePageDraftMetadata metadata, Map<String, Object> content) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        PageRow current = ensureLanding(hotelId);
        WebsitePageDraftMetadata next = metadata == null ? metadata(current) : metadata;
        Map<String, Object> normalized = mediaReferences.normalizeLandingContent(content);
        WebContentService.validateLandingContent(normalized);
        return saveDraft(actor, current, expectedDraftVersion, next, normalized, landingPath(next.slug()));
    }

    @Transactional
    public WebsitePageDocument publishLanding(String token, UUID hotelId, int expectedDraftVersion, int expectedPublishedVersion) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        return publish(actor, ensureLanding(hotelId), expectedDraftVersion, expectedPublishedVersion);
    }

    @Transactional
    public List<WebContentVersion> landingVersions(String token, UUID hotelId) {
        access.requireHeadquarters(token);
        return versions(ensureLanding(hotelId).id());
    }

    @Transactional(readOnly = true)
    public WebsitePageDocument homeDraft(String token) {
        access.requireHeadquarters(token);
        return document(requiredHome());
    }

    @Transactional
    public WebsitePageDocument saveHomeDraft(String token, int expectedDraftVersion, Map<String, Object> content) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        Map<String, Object> normalized = mediaReferences.normalizeStructuredContent(content);
        contentPages.validate(normalized);
        return saveDraft(actor, requiredHome(), expectedDraftVersion, HOME_METADATA, normalized, "/");
    }

    @Transactional
    public WebsitePageDocument publishHome(String token, int expectedDraftVersion, int expectedPublishedVersion) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        return publish(actor, requiredHome(), expectedDraftVersion, expectedPublishedVersion);
    }

    @Transactional(readOnly = true)
    public List<WebContentVersion> homeVersions(String token) {
        access.requireHeadquarters(token);
        return versions(requiredHome().id());
    }

    @Transactional
    public WebsitePageDocument createContentPage(String token, UUID parentId,
            WebsitePageDraftMetadata metadata, Map<String, Object> content) {
        return createContentPage(token, parentId, ContentKind.BRAND, null, metadata, content, WebsitePageConnections.empty());
    }

    @Transactional
    public WebsitePageDocument createContentPage(String token, UUID parentId, ContentKind kind, UUID hotelId,
            WebsitePageDraftMetadata metadata, Map<String, Object> content, WebsitePageConnections nextConnections) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        validateMetadata(metadata);
        Map<String, Object> normalized = mediaReferences.normalizeStructuredContent(content);
        contentPages.validate(kind, normalized);
        connections.validate(kind, hotelId, nextConnections);
        PageRow parent = lockedPage(parentId);
        validateContentParent(parent, kind, hotelId);
        String path = childPath(parent.draftPath(), metadata.slug());
        validatePathDepth(path);
        rejectPathCollision(null, path);
        UUID id = UUID.randomUUID();
        connections.validateRelatedPages(id, "DRAFT", nextConnections, false);
        try {
            jdbc.update("""
                    insert into website_page (
                        id, hotel_id, parent_id, page_type, content_kind,
                        draft_slug, published_slug, draft_path, published_path,
                        draft_menu_label, published_menu_label, draft_menu_visible, published_menu_visible,
                        draft_menu_order, published_menu_order, draft_content, published_content,
                        draft_version, published_version, published_from_draft_version, updated_by
                    ) values (?, ?, ?, 'CONTENT_PAGE', ?, ?, ?, ?, ?, ?, ?, ?, false, ?, 0, ?::jsonb, '{}'::jsonb, 1, 1, null, ?)
                    """, id, hotelId, parent.id(), kind.name(), metadata.slug(), metadata.slug(), path, path, metadata.menuLabel(), metadata.menuLabel(),
                    metadata.menuVisible(), metadata.menuOrder(), stringify(normalized), actor.id());
        } catch (DuplicateKeyException exception) {
            throw pathConflict();
        }
        jdbc.update("""
                insert into website_page_audit (page_id, action, actor_id, details)
                values (?, 'CREATED', ?, jsonb_build_object('path', ?))
                """, id, actor.id(), path);
        replaceConnections(id, "DRAFT", nextConnections);
        mediaReferences.synchronizeDraft(id, "CONTENT_PAGE", normalized);
        return document(page(id));
    }

    @Transactional(readOnly = true)
    public WebsitePageDocument pageDraft(String token, UUID pageId) {
        access.requireHeadquarters(token);
        PageRow page = page(pageId);
        if (page == null) throw new IllegalArgumentException("페이지를 찾을 수 없습니다.");
        return document(page);
    }

    @Transactional
    public WebsitePageDocument saveContentPageDraft(String token, UUID pageId, int expectedDraftVersion,
            WebsitePageDraftMetadata metadata, Map<String, Object> content) {
        access.requireHeadquarters(token);
        PageRow current = activeContentPage(pageId);
        return saveContentPageDraft(token, current, expectedDraftVersion, metadata, content, pageConnections(current.id(), "DRAFT"));
    }

    @Transactional
    public WebsitePageDocument saveContentPageDraft(String token, UUID pageId, int expectedDraftVersion,
            WebsitePageDraftMetadata metadata, Map<String, Object> content, WebsitePageConnections nextConnections) {
        access.requireHeadquarters(token);
        return saveContentPageDraft(token, activeContentPage(pageId), expectedDraftVersion, metadata, content, nextConnections);
    }

    private WebsitePageDocument saveContentPageDraft(String token, PageRow current, int expectedDraftVersion,
            WebsitePageDraftMetadata metadata, Map<String, Object> content, WebsitePageConnections nextConnections) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        validateMetadata(metadata);
        Map<String, Object> normalized = mediaReferences.normalizeStructuredContent(content);
        ContentKind kind = contentKind(current);
        contentPages.validate(kind, normalized);
        connections.validate(kind, current.hotelId(), nextConnections);
        PageRow parent = lockedPage(current.parentId());
        validateContentParent(parent, kind, current.hotelId());
        String path = childPath(parent.draftPath(), metadata.slug());
        validatePathDepth(path);
        connections.validateRelatedPages(current.id(), "DRAFT", nextConnections, false);
        WebsitePageDocument saved = saveDraft(actor, current, expectedDraftVersion, metadata, normalized, path);
        replaceConnections(current.id(), "DRAFT", nextConnections);
        return document(page(saved.id()));
    }

    @Transactional
    public WebsitePageDocument publishPage(String token, UUID pageId, int expectedDraftVersion, int expectedPublishedVersion) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        PageRow current = lockedPage(pageId);
        if (current == null || !("HOTEL_LANDING".equals(current.pageType()) || "CONTENT_PAGE".equals(current.pageType()))) {
            throw new IllegalArgumentException("발행할 페이지를 찾을 수 없습니다.");
        }
        if ("CONTENT_PAGE".equals(current.pageType()) && !"ACTIVE".equals(current.lifecycleStatus())) throw archivedPage();
        return publish(actor, current, expectedDraftVersion, expectedPublishedVersion);
    }

    @Transactional
    public WebsitePageDocument archiveContentPage(String token, UUID pageId, WebsitePageLifecycleRequest request) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        requireLifecycleRequest(request);
        PageRow current = lockedContentPageForUpdate(pageId);
        if (!"ACTIVE".equals(current.lifecycleStatus()) || !matchesLifecycleRequest(current, request)) {
            throw lifecycleConflict();
        }
        int updated = jdbc.update("""
                update website_page
                   set lifecycle_status = 'ARCHIVED', lifecycle_version = lifecycle_version + 1,
                       archived_at = current_timestamp, archived_by = ?,
                       published_content = '{}'::jsonb, published_menu_visible = false,
                       updated_at = current_timestamp, updated_by = ?
                 where id = ? and page_type = 'CONTENT_PAGE' and lifecycle_status = 'ACTIVE'
                   and lifecycle_version = ? and draft_version = ? and published_version = ?
                """, actor.id(), actor.id(), current.id(), request.expectedLifecycleVersion(),
                request.expectedDraftVersion(), request.expectedPublishedVersion());
        if (updated == 0) throw lifecycleConflict();
        PageRow archived = page(current.id());
        clearConnections(archived.id(), "PUBLISHED");
        mediaReferences.synchronizePublished(archived.id(), archived.pageType(), archived.publishedContent());
        jdbc.update("""
                insert into website_page_audit (page_id, action, actor_id, details)
                values (?, 'ARCHIVED', ?, jsonb_build_object('path', ?, 'lifecycleVersion', ?))
                """, archived.id(), actor.id(), archived.publishedPath(), archived.lifecycleVersion());
        return document(archived);
    }

    @Transactional
    public WebsitePageDocument restoreContentPage(String token, UUID pageId, WebsitePageLifecycleRequest request) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        requireLifecycleRequest(request);
        PageRow current = lockedContentPageForUpdate(pageId);
        if (!"ARCHIVED".equals(current.lifecycleStatus()) || !matchesLifecycleRequest(current, request)) {
            throw lifecycleConflict();
        }
        int updated = jdbc.update("""
                update website_page
                   set lifecycle_status = 'ACTIVE', lifecycle_version = lifecycle_version + 1,
                       archived_at = null, archived_by = null,
                       updated_at = current_timestamp, updated_by = ?
                 where id = ? and page_type = 'CONTENT_PAGE' and lifecycle_status = 'ARCHIVED'
                   and lifecycle_version = ? and draft_version = ? and published_version = ?
                """, actor.id(), current.id(), request.expectedLifecycleVersion(), request.expectedDraftVersion(),
                request.expectedPublishedVersion());
        if (updated == 0) throw lifecycleConflict();
        PageRow restored = page(current.id());
        jdbc.update("""
                insert into website_page_audit (page_id, action, actor_id, details)
                values (?, 'RESTORED', ?, jsonb_build_object('path', ?, 'lifecycleVersion', ?))
                """, restored.id(), actor.id(), restored.draftPath(), restored.lifecycleVersion());
        return document(restored);
    }

    @Transactional
    public void deleteArchivedContentPage(String token, UUID pageId, WebsitePageLifecycleRequest request) {
        access.requireHeadquarters(token);
        requireLifecycleRequest(request);
        PageRow current = lockedContentPageForUpdate(pageId);
        if (!"ARCHIVED".equals(current.lifecycleStatus())) {
            throw pageDeleteConflict("보관된 일반 페이지만 영구 삭제할 수 있습니다.");
        }
        if (!matchesLifecycleRequest(current, request)) throw lifecycleConflict();
        Integer childCount = jdbc.queryForObject("select count(*) from website_page where parent_id = ?", Integer.class, current.id());
        if (childCount != null && childCount > 0) {
            throw pageDeleteConflict("하위 페이지가 있는 일반 페이지는 영구 삭제할 수 없습니다.");
        }

        jdbc.update("delete from website_media_usage where page_id = ?", current.id());
        clearConnections(current.id(), "DRAFT");
        clearConnections(current.id(), "PUBLISHED");
        jdbc.update("delete from website_page_version where page_id = ?", current.id());
        jdbc.update("delete from website_page_audit where page_id = ?", current.id());
        int deleted = jdbc.update("""
                delete from website_page
                 where id = ? and page_type = 'CONTENT_PAGE' and lifecycle_status = 'ARCHIVED'
                   and lifecycle_version = ? and draft_version = ? and published_version = ?
                """, current.id(), request.expectedLifecycleVersion(), request.expectedDraftVersion(),
                request.expectedPublishedVersion());
        if (deleted == 0) throw lifecycleConflict();
    }

    @Transactional
    public WebsitePageDocument restoreContentPageVersionDraft(String token, UUID pageId, int sourceVersion,
            WebsitePageLifecycleRequest request) {
        StaffPrincipal actor = access.requireHeadquarters(token);
        requireSourceVersion(sourceVersion);
        requireLifecycleRequest(request);
        PageRow current = lockedContentPageForUpdate(pageId);
        if (!"ACTIVE".equals(current.lifecycleStatus()) || !matchesLifecycleRequest(current, request)) {
            throw lifecycleConflict();
        }
        if (sourceVersion >= current.publishedVersion()) {
            throw new IllegalArgumentException("이전 발행본만 초안으로 복원할 수 있습니다.");
        }
        VersionDocument source = sourceVersionDocument(current, sourceVersion);
        Map<String, Object> normalized = mediaReferences.normalizeStructuredContent(source.content());
        ContentKind kind = contentKind(current);
        contentPages.validate(kind, normalized);
        connections.validate(kind, current.hotelId(), source.connections());
        connections.validateRelatedPages(current.id(), "DRAFT", source.connections(), false);
        int updated = jdbc.update("""
                update website_page
                   set draft_content = ?::jsonb, draft_version = draft_version + 1,
                       updated_at = current_timestamp, updated_by = ?
                 where id = ? and page_type = 'CONTENT_PAGE' and lifecycle_status = 'ACTIVE'
                   and lifecycle_version = ? and draft_version = ? and published_version = ?
                """, stringify(normalized), actor.id(), current.id(), request.expectedLifecycleVersion(),
                request.expectedDraftVersion(), request.expectedPublishedVersion());
        if (updated == 0) throw lifecycleConflict();
        PageRow restored = page(current.id());
        replaceConnections(restored.id(), "DRAFT", source.connections());
        mediaReferences.synchronizeDraft(restored.id(), restored.pageType(), restored.draftContent());
        jdbc.update("""
                insert into website_page_audit (page_id, action, actor_id, details)
                values (?, 'VERSION_RESTORED', ?, jsonb_build_object('sourceVersion', ?, 'path', ?))
                """, restored.id(), actor.id(), sourceVersion, restored.draftPath());
        return document(restored);
    }

    @Transactional(readOnly = true)
    public List<WebContentVersion> pageVersions(String token, UUID pageId) {
        access.requireHeadquarters(token);
        if (page(pageId) == null) throw new IllegalArgumentException("페이지를 찾을 수 없습니다.");
        return versions(pageId);
    }

    @Transactional(readOnly = true)
    public WebsitePageVersionComparison compareContentPageVersions(String token, UUID pageId, int baseVersion, int compareVersion) {
        access.requireHeadquarters(token);
        requireComparisonVersions(baseVersion, compareVersion);
        PageRow current = page(pageId);
        if (current == null || !"CONTENT_PAGE".equals(current.pageType())) {
            throw new WebsitePageNotFoundException(pageId.toString());
        }
        return new WebsitePageVersionComparison(pageId,
                versionSnapshot(current, baseVersion), versionSnapshot(current, compareVersion));
    }

    public Map<String, Object> publishedLandingContent(UUID hotelId) {
        PageRow page = landing(hotelId);
        return page == null || !"ACTIVE".equals(page.lifecycleStatus()) || page.publishedContent().isEmpty()
                ? Map.of() : page.publishedContent();
    }

    public PublishedWebsitePage resolvePublished(String path) {
        if (path == null || !PATH.matcher(path).matches()) throw new WebsitePageNotFoundException(path == null ? "" : path);
        PageRow page = jdbc.query("select " + PAGE_COLUMNS + " from website_page where published_path = ? "
                + "and lifecycle_status = 'ACTIVE' and page_type in ('HOME_PAGE', 'HOTEL_LANDING', 'CONTENT_PAGE') "
                + "and published_content <> '{}'::jsonb",
                rs -> rs.next() ? row(rs) : null, path);
        if (page == null) throw new WebsitePageNotFoundException(path);
        return new PublishedWebsitePage(page.id(), page.pageType(), contentKind(page), page.publishedPath(), page.hotelId(),
                responseContent(page, page.publishedContent(), page.publishedVersion()), pageConnections(page.id(), "PUBLISHED"));
    }

    public List<WebsiteContentCollectionItem> publishedCollection(String hotelSlug, ContentKind kind) {
        if (kind == null || !ContentKind.contentPageKinds().contains(kind)) {
            throw new IllegalArgumentException("상세 콘텐츠 종류가 올바르지 않습니다.");
        }
        UUID hotelId = hotelIdForSlug(hotelSlug);
        if (kind.requiresHotel() && hotelId == null) {
            throw new IllegalArgumentException(kind + " 목록에는 지점이 필요합니다.");
        }
        List<PageRow> candidates = jdbc.query("select " + PAGE_COLUMNS + " from website_page where page_type = 'CONTENT_PAGE' "
                + "and content_kind = ? and lifecycle_status = 'ACTIVE' and published_content <> '{}'::jsonb order by published_path",
                (rs, rowNum) -> row(rs), kind.name());
        return candidates.stream()
                .filter(page -> belongsToCollection(page, kind, hotelId))
                .map(page -> collectionItem(page, hotelId))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<WebsiteNavigationItem> navigation() {
        List<PageRow> sections = jdbc.query("select " + PAGE_COLUMNS + " from website_page "
                + "where lifecycle_status = 'ACTIVE' and page_type = 'SECTION' and published_menu_visible = true "
                + "order by published_menu_order, published_path",
                (rs, index) -> row(rs));
        return sections.stream().map(section -> new WebsiteNavigationItem(
                section.id(), null, section.publishedMenuLabel(), section.publishedPath(), navigationChildren(section.id()))).toList();
    }

    public List<WebsitePageTreeItem> staffTree(String token) {
        access.requireHeadquarters(token);
        List<PageRow> sections = jdbc.query("select " + PAGE_COLUMNS + " from website_page where page_type = 'SECTION' "
                + "order by draft_menu_order, draft_path", (rs, index) -> row(rs));
        PageRow home = homePage();
        Stream<WebsitePageTreeItem> homeItem = home == null ? Stream.empty() : Stream.of(treeItem(home, List.of()));
        return Stream.concat(homeItem, sections.stream().map(section -> treeItem(section, staffTreeChildren(section.id())))).toList();
    }

    @Transactional(readOnly = true)
    public ContentReferenceCatalog contentReference(String token) {
        access.requireHeadquarters(token);
        List<ContentReferenceCatalog.Hotel> hotels = jdbc.query("select id, name, region from hotel order by name", (rs, rowNum) -> {
            UUID hotelId = rs.getObject("id", UUID.class);
            List<ContentReferenceCatalog.RoomType> roomTypes = jdbc.query("""
                    select id, name, max_occupancy from room_type where hotel_id = ? order by name
                    """, (room, roomRow) -> new ContentReferenceCatalog.RoomType(
                    room.getObject(1, UUID.class), room.getString(2), room.getInt(3)), hotelId);
            return new ContentReferenceCatalog.Hotel(hotelId, rs.getString("name"), rs.getString("region"), roomTypes);
        });
        List<ContentReferenceCatalog.Page> pages = jdbc.query("""
                select id, content_kind, hotel_id, published_menu_label, published_path
                  from website_page
                 where page_type = 'CONTENT_PAGE' and lifecycle_status = 'ACTIVE'
                   and published_content <> '{}'::jsonb
                 order by published_path
                """, (rs, rowNum) -> new ContentReferenceCatalog.Page(
                rs.getObject(1, UUID.class), contentKind(rs.getString(2)), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5)));
        return new ContentReferenceCatalog(hotels, pages);
    }

    private WebsitePageDocument saveDraft(StaffPrincipal actor, PageRow current, int expectedDraftVersion,
            WebsitePageDraftMetadata next, Map<String, Object> content, String draftPath) {
        requireExpectedDraftVersion(expectedDraftVersion);
        validateMetadata(next);
        rejectPathCollision(current.id(), draftPath);
        int updated;
        try {
            updated = jdbc.update("""
                    update website_page
                     set draft_slug = ?, draft_path = ?, draft_menu_label = ?, draft_menu_visible = ?, draft_menu_order = ?,
                           draft_content = ?::jsonb, draft_version = draft_version + 1,
                           updated_at = current_timestamp, updated_by = ?
                     where id = ? and lifecycle_status = 'ACTIVE' and draft_version = ?
                    """, next.slug(), draftPath, next.menuLabel(), next.menuVisible(), next.menuOrder(), stringify(content),
                    actor.id(), current.id(), expectedDraftVersion);
        } catch (DuplicateKeyException exception) {
            throw pathConflict();
        }
        if (updated == 0) throw staleDraft();
        mediaReferences.synchronizeDraft(current.id(), current.pageType(), content);
        jdbc.update("""
                insert into website_page_audit (page_id, action, actor_id, details)
                values (?, 'DRAFT_SAVED', ?, jsonb_build_object('path', ?))
                """, current.id(), actor.id(), draftPath);
        return document(page(current.id()));
    }

    private WebsitePageDocument publish(StaffPrincipal actor, PageRow current, int expectedDraftVersion, int expectedPublishedVersion) {
        requireExpectedDraftVersion(expectedDraftVersion);
        requireExpectedPublishedVersion(expectedPublishedVersion);
        WebsitePageConnections draftConnections = pageConnections(current.id(), "DRAFT");
        if ("CONTENT_PAGE".equals(current.pageType())) {
            ContentKind kind = contentKind(current);
            contentPages.validate(kind, mediaReferences.normalizeStructuredContent(current.draftContent()));
            connections.validate(kind, current.hotelId(), draftConnections);
            connections.validateRelatedPages(current.id(), "PUBLISHED", draftConnections, true);
        }
        int nextPublishedVersion = expectedPublishedVersion + 1;
        int updated = jdbc.update("""
                update website_page
                   set published_slug = draft_slug, published_path = draft_path,
                       published_menu_label = draft_menu_label, published_menu_visible = draft_menu_visible,
                       published_menu_order = draft_menu_order, published_content = draft_content,
                       published_version = ?, published_from_draft_version = draft_version,
                       updated_at = current_timestamp, updated_by = ?
                 where id = ? and lifecycle_status = 'ACTIVE' and draft_version = ? and published_version = ?
                """, nextPublishedVersion, actor.id(), current.id(), expectedDraftVersion, expectedPublishedVersion);
        if (updated == 0) throw staleDraft();
        PageRow published = page(current.id());
        replaceConnections(published.id(), "PUBLISHED", draftConnections);
        mediaReferences.synchronizePublished(published.id(), published.pageType(), published.publishedContent());
        jdbc.update("""
                insert into website_page_version (page_id, version, page_snapshot, published_by)
                values (?, ?, ?::jsonb, ?)
                """, published.id(), nextPublishedVersion, stringify(snapshot(published, pageConnections(published.id(), "PUBLISHED"))), actor.id());
        jdbc.update("""
                insert into website_page_audit (page_id, action, actor_id, details)
                values (?, 'PUBLISHED', ?, jsonb_build_object('path', ?))
                """, published.id(), actor.id(), published.publishedPath());
        return document(published);
    }

    private List<WebContentVersion> versions(UUID pageId) {
        return jdbc.query("""
                select version, published_at from website_page_version where page_id = ? order by version desc
                """, (rs, index) -> new WebContentVersion(rs.getInt(1), rs.getTimestamp(2).toInstant()), pageId);
    }

    private VersionDocument sourceVersionDocument(PageRow current, int sourceVersion) {
        String serialized = jdbc.query("""
                select page_snapshot::text from website_page_version where page_id = ? and version = ?
                """, rs -> rs.next() ? rs.getString(1) : null, current.id(), sourceVersion);
        if (serialized == null) throw new WebsitePageNotFoundException(current.id() + "/versions/" + sourceVersion);
        Map<String, Object> snapshot = read(serialized);
        if (!current.pageType().equals(snapshot.get("pageType"))
                || !sameIdentifier(snapshot.get("hotelId"), current.hotelId())
                || !sameIdentifier(snapshot.get("parentId"), current.parentId())) {
            throw new IllegalArgumentException("발행 이력의 페이지 구조가 현재 일반 페이지와 일치하지 않습니다.");
        }
        Object snapshotKind = snapshot.get("contentKind");
        if (snapshotKind != null && !contentKind(current).name().equals(snapshotKind)) {
            throw new IllegalArgumentException("발행 이력의 콘텐츠 종류가 현재 일반 페이지와 일치하지 않습니다.");
        }
        Object content = snapshot.get("content");
        if (!(content instanceof Map<?, ?>)) throw new IllegalArgumentException("발행 이력의 콘텐츠 형식이 올바르지 않습니다.");
        try {
            return new VersionDocument(json.convertValue(content, new TypeReference<LinkedHashMap<String, Object>>() {}),
                    snapshotConnections(snapshot));
        } catch (RuntimeException exception) {
            throw invalidVersionSnapshot();
        }
    }

    private WebsitePageConnections snapshotConnections(Map<String, Object> snapshot) {
        Object value = snapshot.get("connections");
        if (value == null) return WebsitePageConnections.empty();
        return json.convertValue(value, WebsitePageConnections.class);
    }

    private WebsitePageVersionSnapshot versionSnapshot(PageRow current, int version) {
        VersionRow source = jdbc.query("""
                select page_snapshot::text, published_at
                  from website_page_version
                 where page_id = ? and version = ?
                """, rs -> rs.next() ? new VersionRow(rs.getString(1), rs.getTimestamp(2).toInstant()) : null,
                current.id(), version);
        if (source == null) throw new WebsitePageNotFoundException(current.id() + "/versions/" + version);

        Map<String, Object> snapshot;
        try {
            snapshot = read(source.snapshot());
        } catch (RuntimeException exception) {
            throw invalidVersionSnapshot();
        }
        if (!"CONTENT_PAGE".equals(snapshot.get("pageType"))
                || !sameIdentifier(snapshot.get("hotelId"), current.hotelId())
                || !sameIdentifier(snapshot.get("parentId"), current.parentId())) {
            throw invalidVersionSnapshot();
        }

        ContentKind kind = contentKind(current);
        Object snapshotKind = snapshot.get("contentKind");
        if (snapshotKind != null && !kind.name().equals(snapshotKind)) throw invalidVersionSnapshot();

        WebsitePageMetadata metadata = versionMetadata(snapshot);
        Integer publishedFromDraftVersion = versionPublishedFromDraftVersion(snapshot.get("publishedFromDraftVersion"));
        Object contentValue = snapshot.get("content");
        if (!(contentValue instanceof Map<?, ?>)) throw invalidVersionSnapshot();
        Map<String, Object> content;
        try {
            content = json.convertValue(contentValue, new TypeReference<LinkedHashMap<String, Object>>() {});
            if (snapshotKind == null) contentPages.validate(content);
            else contentPages.validate(kind, content);
            connections.validate(kind, current.hotelId(), snapshotConnections(snapshot));
        } catch (RuntimeException exception) {
            throw invalidVersionSnapshot();
        }
        return new WebsitePageVersionSnapshot(version, source.publishedAt(), metadata, publishedFromDraftVersion, content);
    }

    private WebsitePageMetadata versionMetadata(Map<String, Object> snapshot) {
        Object menuValue = snapshot.get("menu");
        if (!(menuValue instanceof Map<?, ?> menu)) throw invalidVersionSnapshot();
        String slug = versionString(snapshot.get("slug"));
        String path = versionString(snapshot.get("path"));
        String menuLabel = versionString(menu.get("label"));
        Object menuVisible = menu.get("visible");
        if (!(menuVisible instanceof Boolean visible)) throw invalidVersionSnapshot();
        int menuOrder = versionMenuOrder(menu.get("order"));
        if (!SLUG.matcher(slug).matches() || !PATH.matcher(path).matches() || !path.endsWith("/" + slug)) {
            throw invalidVersionSnapshot();
        }
        if (menuLabel.length() > 100) throw invalidVersionSnapshot();
        return new WebsitePageMetadata(slug, path, menuLabel, visible, menuOrder);
    }

    private String versionString(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw invalidVersionSnapshot();
        return text;
    }

    private int versionMenuOrder(Object value) {
        if (!(value instanceof Integer order) || order < 0) throw invalidVersionSnapshot();
        return order;
    }

    private Integer versionPublishedFromDraftVersion(Object value) {
        if (value == null) return null;
        if (!(value instanceof Integer version) || version <= 0) throw invalidVersionSnapshot();
        return version;
    }

    private List<WebsiteNavigationItem> navigationChildren(UUID parentId) {
        return jdbc.query("select " + PAGE_COLUMNS + " from website_page where parent_id = ? and "
                + "lifecycle_status = 'ACTIVE' and page_type in ('HOTEL_LANDING', 'CONTENT_PAGE') and published_menu_visible = true "
                + "and published_content <> '{}'::jsonb order by published_menu_order, published_path",
                (rs, index) -> {
                    PageRow page = row(rs);
                    return new WebsiteNavigationItem(page.id(), page.hotelId(), page.publishedMenuLabel(), page.publishedPath(), List.of());
                }, parentId);
    }

    private List<WebsitePageTreeItem> staffTreeChildren(UUID parentId) {
        return jdbc.query("select " + PAGE_COLUMNS + " from website_page where parent_id = ? order by draft_menu_order, draft_path",
                (rs, index) -> treeItem(row(rs), List.of()), parentId);
    }

    private WebsitePageTreeItem treeItem(PageRow page, List<WebsitePageTreeItem> children) {
        String status = page.publishedContent().isEmpty() ? "DRAFT"
                : Integer.valueOf(page.draftVersion()).equals(page.publishedFromDraftVersion()) ? "PUBLISHED" : "CHANGED_AFTER_PUBLISH";
        return new WebsitePageTreeItem(page.id(), page.hotelId(), page.pageType(), page.draftMenuLabel(),
                page.draftPath(), page.publishedPath(), status, page.lifecycleStatus(), page.lifecycleVersion(), children);
    }

    private PageRow ensureLanding(UUID hotelId) {
        HotelRow hotel = hotel(hotelId);
        PageRow existing = landing(hotelId);
        if (existing != null) return existing;
        String slug = initialSlug(hotelId);
        String path = landingPath(slug);
        jdbc.update("""
                insert into website_page (
                    id, hotel_id, parent_id, page_type, content_kind, draft_slug, published_slug, draft_path, published_path,
                    draft_menu_label, published_menu_label, draft_menu_visible, published_menu_visible,
                    draft_menu_order, published_menu_order, draft_content, published_content,
                    draft_version, published_version, published_from_draft_version
                ) values (?, ?, ?, 'HOTEL_LANDING', 'DESTINATION', ?, ?, ?, ?, ?, ?, true, false, 0, 0,
                          '{}'::jsonb, '{}'::jsonb, 1, 1, null) on conflict do nothing
                """, UUID.randomUUID(), hotelId, STAYS_SECTION_ID, slug, slug, path, path, hotel.name(), hotel.name());
        return landing(hotelId);
    }

    private PageRow activeContentPage(UUID pageId) {
        PageRow page = contentPage(lockedPage(pageId));
        if (!"ACTIVE".equals(page.lifecycleStatus())) throw archivedPage();
        return page;
    }

    private PageRow contentPage(PageRow page) {
        if (page == null || !"CONTENT_PAGE".equals(page.pageType())) throw new IllegalArgumentException("일반 페이지를 찾을 수 없습니다.");
        return page;
    }

    private PageRow lockedContentPageForUpdate(UUID pageId) {
        PageRow page = lockedPageForUpdate(pageId);
        if (page == null || !"CONTENT_PAGE".equals(page.pageType())) throw new WebsitePageNotFoundException(pageId.toString());
        return page;
    }

    private PageRow requiredHome() {
        PageRow home = homePage();
        if (home == null) throw new IllegalStateException("홈페이지 초기화 데이터를 찾을 수 없습니다.");
        return home;
    }

    private PageRow homePage() {
        return jdbc.query("select " + PAGE_COLUMNS + " from website_page where id = ? and page_type = 'HOME_PAGE'",
                rs -> rs.next() ? row(rs) : null, HOME_PAGE_ID);
    }

    private PageRow landing(UUID hotelId) {
        return jdbc.query("select " + PAGE_COLUMNS + " from website_page where hotel_id = ? and page_type = 'HOTEL_LANDING'",
                rs -> rs.next() ? row(rs) : null, hotelId);
    }

    private PageRow page(UUID pageId) {
        return jdbc.query("select " + PAGE_COLUMNS + " from website_page where id = ?", rs -> rs.next() ? row(rs) : null, pageId);
    }

    private UUID hotelIdForSlug(String hotelSlug) {
        if (hotelSlug == null || hotelSlug.isBlank()) return null;
        UUID hotelId = jdbc.query("select id from hotel", (rs, rowNum) -> rs.getObject(1, UUID.class)).stream()
                .filter(id -> initialSlug(id).equals(hotelSlug))
                .findFirst()
                .orElse(null);
        if (hotelId == null) throw new WebsitePageNotFoundException(hotelSlug);
        return hotelId;
    }

    private boolean belongsToCollection(PageRow page, ContentKind kind, UUID hotelId) {
        return switch (kind) {
            case ROOM, DINING, FACILITY, EXPERIENCE -> hotelId.equals(page.hotelId());
            case PROMOTION -> hotelId == null || pageConnections(page.id(), "PUBLISHED").targetHotelIds().contains(hotelId);
            case GUIDE -> hotelId == null ? page.hotelId() == null : page.hotelId() == null || hotelId.equals(page.hotelId());
            case BRAND -> page.hotelId() == null;
            default -> false;
        };
    }

    private WebsiteContentCollectionItem collectionItem(PageRow page, UUID selectedHotelId) {
        Object blocksValue = page.publishedContent().get("blocks");
        if (!(blocksValue instanceof List<?> blocks)) return null;
        for (Object rawBlock : blocks) {
            if (!(rawBlock instanceof Map<?, ?> block) || !"HERO".equals(block.get("type"))) continue;
            Object title = block.get("title");
            Object summary = block.get("description");
            Object image = block.get("imageSrc");
            if (!(title instanceof String titleText) || titleText.isBlank()
                    || !(summary instanceof String summaryText) || summaryText.isBlank()
                    || !(image instanceof String imagePath) || !safeCollectionImage(imagePath)) return null;
            UUID hotelId = page.hotelId() == null ? selectedHotelId : page.hotelId();
            return new WebsiteContentCollectionItem(page.id(), contentKind(page), page.publishedPath(), titleText,
                    summaryText, imagePath, hotelId == null ? null : initialSlug(hotelId));
        }
        return null;
    }

    private boolean safeCollectionImage(String imagePath) {
        if (imagePath.matches("/api/website/media/[0-9a-fA-F-]{36}/content")) return true;
        if (!imagePath.matches("/images/[A-Za-z0-9][A-Za-z0-9._/-]*")) return false;
        return Arrays.stream(imagePath.substring("/images/".length()).split("/"))
                .noneMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."));
    }

    private PageRow lockedPage(UUID pageId) {
        return jdbc.query("select " + PAGE_COLUMNS + " from website_page where id = ? for key share",
                rs -> rs.next() ? row(rs) : null, pageId);
    }

    private PageRow lockedPageForUpdate(UUID pageId) {
        return jdbc.query("select " + PAGE_COLUMNS + " from website_page where id = ? for update",
                rs -> rs.next() ? row(rs) : null, pageId);
    }

    private HotelRow hotel(UUID hotelId) {
        HotelRow hotel = jdbc.query("select id, name from hotel where id = ?", rs ->
                rs.next() ? new HotelRow(rs.getObject(1, UUID.class), rs.getString(2)) : null, hotelId);
        if (hotel == null) throw new IllegalArgumentException("호텔을 찾을 수 없습니다.");
        return hotel;
    }

    private String initialSlug(UUID hotelId) {
        if (hotelId.toString().equals("11000000-0000-0000-0000-000000000001")) return "sokcho";
        if (hotelId.toString().equals("11000000-0000-0000-0000-000000000002")) return "seoraksan";
        if (hotelId.toString().equals("11000000-0000-0000-0000-000000000003")) return "jeju";
        return "hotel-" + hotelId.toString().replace("-", "");
    }

    private String landingPath(String slug) { return "/stays/" + slug; }

    private String childPath(String parentPath, String slug) { return parentPath + "/" + slug; }

    private WebsitePageDraftMetadata metadata(PageRow page) {
        return new WebsitePageDraftMetadata(page.draftSlug(), page.draftMenuLabel(), page.draftMenuVisible(), page.draftMenuOrder());
    }

    private void validateMetadata(WebsitePageDraftMetadata metadata) {
        if (metadata == null || metadata.slug() == null || !SLUG.matcher(metadata.slug()).matches()) {
            throw new IllegalArgumentException("페이지 슬러그는 영문 소문자, 숫자, 하이픈만 사용할 수 있습니다.");
        }
        if (RESERVED_SLUGS.contains(metadata.slug())) throw new IllegalArgumentException("예약된 페이지 슬러그는 사용할 수 없습니다.");
        if (metadata.menuLabel() == null || metadata.menuLabel().isBlank() || metadata.menuLabel().length() > 100) {
            throw new IllegalArgumentException("메뉴명은 1~100자로 입력해 주세요.");
        }
        if (metadata.menuOrder() < 0) throw new IllegalArgumentException("메뉴 순서는 0 이상이어야 합니다.");
    }

    private void validateContentParent(PageRow parent, ContentKind kind, UUID hotelId) {
        if (parent == null || !"SECTION".equals(parent.pageType())) {
            throw new IllegalArgumentException("일반 페이지의 상위는 SECTION이어야 합니다.");
        }
        if (hotelId == null && parent.hotelId() != null) {
            throw new IllegalArgumentException("체인 공통 페이지는 체인 SECTION 아래에 만들어야 합니다.");
        }
        if (hotelId != null && !hotelId.equals(parent.hotelId())) {
            throw new IllegalArgumentException(kind + " 페이지의 소유 지점과 상위 SECTION 범위가 일치해야 합니다.");
        }
    }

    private void validatePathDepth(String path) {
        int segments = (int) Stream.of(path.split("/", -1)).filter(segment -> !segment.isEmpty()).count();
        if (segments > 4) throw new IllegalArgumentException("페이지 경로는 최대 4개 segment까지 사용할 수 있습니다.");
    }

    private WebsitePageConnections pageConnections(UUID pageId, String documentState) {
        List<UUID> roomTypeIds = jdbc.query("""
                select room_type_id from website_page_room_type
                 where page_id = ? and document_state = ? order by room_type_id
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), pageId, documentState);
        List<UUID> targetHotelIds = jdbc.query("""
                select hotel_id from website_page_hotel
                 where page_id = ? and document_state = ? order by hotel_id
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), pageId, documentState);
        List<WebsitePageRelation> relatedPages = jdbc.query("""
                select target_page_id, relation_type, display_order from website_page_relation
                 where page_id = ? and document_state = ? order by relation_type, display_order
                """, (rs, rowNum) -> new WebsitePageRelation(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3)),
                pageId, documentState);
        return new WebsitePageConnections(roomTypeIds, targetHotelIds, relatedPages);
    }

    private void replaceConnections(UUID pageId, String documentState, WebsitePageConnections nextConnections) {
        WebsitePageConnections next = nextConnections == null ? WebsitePageConnections.empty() : nextConnections;
        clearConnections(pageId, documentState);
        for (UUID roomTypeId : next.roomTypeIds()) {
            jdbc.update("insert into website_page_room_type (page_id, document_state, room_type_id) values (?, ?, ?)",
                    pageId, documentState, roomTypeId);
        }
        for (UUID hotelId : next.targetHotelIds()) {
            jdbc.update("insert into website_page_hotel (page_id, document_state, hotel_id) values (?, ?, ?)",
                    pageId, documentState, hotelId);
        }
        for (WebsitePageRelation relation : next.relatedPages()) {
            jdbc.update("""
                    insert into website_page_relation (page_id, document_state, target_page_id, relation_type, display_order)
                    values (?, ?, ?, ?, ?)
                    """, pageId, documentState, relation.targetPageId(), relation.relationType(), relation.displayOrder());
        }
    }

    private void clearConnections(UUID pageId, String documentState) {
        jdbc.update("delete from website_page_relation where page_id = ? and document_state = ?", pageId, documentState);
        jdbc.update("delete from website_page_room_type where page_id = ? and document_state = ?", pageId, documentState);
        jdbc.update("delete from website_page_hotel where page_id = ? and document_state = ?", pageId, documentState);
    }

    private void rejectPathCollision(UUID pageId, String path) {
        Integer count = pageId == null
                ? jdbc.queryForObject("select count(*) from website_page where draft_path = ? or published_path = ?",
                        Integer.class, path, path)
                : jdbc.queryForObject("select count(*) from website_page where (draft_path = ? or published_path = ?) and id <> ?",
                        Integer.class, path, path, pageId);
        if (count != null && count > 0) throw pathConflict();
    }

    private void requireExpectedDraftVersion(int expectedDraftVersion) {
        if (expectedDraftVersion <= 0) throw new IllegalArgumentException("초안 버전은 1 이상이어야 합니다.");
    }

    private void requireExpectedPublishedVersion(int expectedPublishedVersion) {
        if (expectedPublishedVersion <= 0) throw new IllegalArgumentException("발행 버전은 1 이상이어야 합니다.");
    }

    private void requireSourceVersion(int sourceVersion) {
        if (sourceVersion <= 0) throw new IllegalArgumentException("발행본 버전은 1 이상이어야 합니다.");
    }

    private void requireComparisonVersions(int baseVersion, int compareVersion) {
        requireSourceVersion(baseVersion);
        requireSourceVersion(compareVersion);
        if (baseVersion >= compareVersion) {
            throw new IllegalArgumentException("기준 발행본은 비교 발행본보다 이전이어야 합니다.");
        }
    }

    private void requireLifecycleRequest(WebsitePageLifecycleRequest request) {
        if (request == null) throw new IllegalArgumentException("페이지 상태 버전이 필요합니다.");
        if (request.expectedLifecycleVersion() <= 0) throw new IllegalArgumentException("페이지 상태 버전은 1 이상이어야 합니다.");
        requireExpectedDraftVersion(request.expectedDraftVersion());
        requireExpectedPublishedVersion(request.expectedPublishedVersion());
    }

    private boolean matchesLifecycleRequest(PageRow page, WebsitePageLifecycleRequest request) {
        return page.lifecycleVersion() == request.expectedLifecycleVersion()
                && page.draftVersion() == request.expectedDraftVersion()
                && page.publishedVersion() == request.expectedPublishedVersion();
    }

    private boolean sameIdentifier(Object value, UUID expected) {
        return expected == null ? value == null : expected.toString().equals(value);
    }

    private BusinessConflictException pathConflict() {
        return new BusinessConflictException("WEBSITE_PAGE_PATH_CONFLICT", "이미 사용 중인 페이지 경로입니다.");
    }

    private BusinessConflictException archivedPage() {
        return new BusinessConflictException("WEBSITE_PAGE_ARCHIVED", "보관된 페이지는 초안 저장이나 발행을 할 수 없습니다. 먼저 초안으로 복원해 주세요.");
    }

    private BusinessConflictException lifecycleConflict() {
        return new BusinessConflictException("WEBSITE_PAGE_LIFECYCLE_CONFLICT", "페이지가 보관되었거나 다른 사용자가 상태를 변경했습니다. 최신 페이지를 다시 불러와 주세요.");
    }

    private BusinessConflictException pageDeleteConflict(String message) {
        return new BusinessConflictException("WEBSITE_PAGE_DELETE_CONFLICT", message);
    }

    private IllegalArgumentException invalidVersionSnapshot() {
        return new IllegalArgumentException("발행 이력의 스냅샷 형식이 올바르지 않습니다.");
    }

    private WebsitePageDocument document(PageRow page) {
        return new WebsitePageDocument(page.id(), page.pageType(), contentKind(page), page.hotelId(),
                responseContent(page, page.draftContent(), page.draftVersion()), pageConnections(page.id(), "DRAFT"), page.draftVersion(),
                new WebsitePageMetadata(page.draftSlug(), page.draftPath(), page.draftMenuLabel(), page.draftMenuVisible(), page.draftMenuOrder()),
                responseContent(page, page.publishedContent(), page.publishedVersion()), pageConnections(page.id(), "PUBLISHED"), page.publishedVersion(),
                new WebsitePageMetadata(page.publishedSlug(), page.publishedPath(), page.publishedMenuLabel(), page.publishedMenuVisible(), page.publishedMenuOrder()),
                page.lifecycleStatus(), page.lifecycleVersion());
    }

    private Map<String, Object> snapshot(PageRow page, WebsitePageConnections pageConnections) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pageType", page.pageType());
        snapshot.put("contentKind", contentKind(page).name());
        snapshot.put("hotelId", page.hotelId());
        snapshot.put("parentId", page.parentId());
        snapshot.put("slug", page.publishedSlug());
        snapshot.put("path", page.publishedPath());
        snapshot.put("menu", Map.of("label", page.publishedMenuLabel(), "visible", page.publishedMenuVisible(), "order", page.publishedMenuOrder()));
        snapshot.put("publishedFromDraftVersion", page.publishedFromDraftVersion());
        snapshot.put("content", page.publishedContent());
        snapshot.put("connections", pageConnections);
        return snapshot;
    }

    private PageRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PageRow(rs.getObject("id", UUID.class), rs.getObject("hotel_id", UUID.class), rs.getObject("parent_id", UUID.class),
                rs.getString("page_type"), contentKind(rs.getString("content_kind")), rs.getString("draft_slug"), rs.getString("draft_path"), rs.getString("draft_menu_label"),
                rs.getBoolean("draft_menu_visible"), rs.getInt("draft_menu_order"), read(rs.getString("draft_content")), rs.getInt("draft_version"),
                rs.getString("published_slug"), rs.getString("published_path"), rs.getString("published_menu_label"),
                rs.getBoolean("published_menu_visible"), rs.getInt("published_menu_order"), read(rs.getString("published_content")),
                rs.getInt("published_version"), rs.getObject("published_from_draft_version", Integer.class),
                rs.getString("lifecycle_status"), rs.getInt("lifecycle_version"));
    }

    private Map<String, Object> read(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private String stringify(Map<String, Object> value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception error) { throw new IllegalArgumentException("페이지 콘텐츠 형식이 올바르지 않습니다.", error); }
    }

    private BusinessConflictException staleDraft() {
        return new BusinessConflictException("WEB_CONTENT_VERSION_CONFLICT", "다른 사용자가 콘텐츠를 변경했습니다. 최신 초안을 다시 불러와 주세요.");
    }

    private ContentKind contentKind(PageRow page) {
        return page.contentKind() == null ? ContentKind.legacyForPageType(page.pageType()) : page.contentKind();
    }

    private ContentKind contentKind(String value) {
        return value == null ? null : ContentKind.valueOf(value);
    }

    private Map<String, Object> responseContent(PageRow page, Map<String, Object> content, int version) {
        Map<String, Object> copy = read(stringify(content));
        if (!(copy.get("blocks") instanceof List<?> blocks)) return copy;
        for (int index = 0; index < blocks.size(); index++) {
            Object rawBlock = blocks.get(index);
            if (!(rawBlock instanceof Map<?, ?> block) || block.containsKey("blockId")) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> writableBlock = (Map<String, Object>) block;
            UUID blockId = UUID.nameUUIDFromBytes((page.id() + ":" + version + ":" + index).getBytes(StandardCharsets.UTF_8));
            writableBlock.put("blockId", blockId.toString());
        }
        return copy;
    }

    private record HotelRow(UUID id, String name) { }

    private record VersionRow(String snapshot, Instant publishedAt) { }

    private record VersionDocument(Map<String, Object> content, WebsitePageConnections connections) { }

    private record PageRow(UUID id, UUID hotelId, UUID parentId, String pageType, ContentKind contentKind,
            String draftSlug, String draftPath, String draftMenuLabel, boolean draftMenuVisible, int draftMenuOrder,
            Map<String, Object> draftContent, int draftVersion, String publishedSlug, String publishedPath,
            String publishedMenuLabel, boolean publishedMenuVisible, int publishedMenuOrder, Map<String, Object> publishedContent,
            int publishedVersion, Integer publishedFromDraftVersion, String lifecycleStatus, int lifecycleVersion) { }
}
