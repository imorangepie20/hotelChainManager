package team.hotelchain.webcontent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.hotelchain.staff.StaffAccessService;

@Service
public class WebsiteMediaDraftReplacementService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final WebsiteMediaService media;
    private final WebsitePageService pages;
    private final WebsiteTranslationService translations;

    public WebsiteMediaDraftReplacementService(
            JdbcTemplate jdbc,
            StaffAccessService access,
            WebsiteMediaService media,
            WebsitePageService pages,
            WebsiteTranslationService translations) {
        this.jdbc = jdbc;
        this.access = access;
        this.media = media;
        this.pages = pages;
        this.translations = translations;
    }

    @Transactional(readOnly = true)
    public WebsiteMediaDraftReplacementImpact impact(String token, UUID sourceMediaId, UUID targetMediaId) {
        access.requireHeadquarters(token);
        requireDifferentAssets(sourceMediaId, targetMediaId);
        media.requireReplacementSource(sourceMediaId, "");
        media.requireReplacementTarget(targetMediaId, "");

        List<UsageRow> rows = usageRows(sourceMediaId);
        List<WebsiteMediaDraftReplacementUsage> replaceable = new ArrayList<>();
        int published = 0;
        int archivedDraft = 0;
        for (UsageRow row : rows) {
            if ("PUBLISHED".equals(row.documentState())) {
                published++;
            } else if (!"ACTIVE".equals(row.lifecycleStatus())) {
                archivedDraft++;
            } else {
                replaceable.add(new WebsiteMediaDraftReplacementUsage(
                        row.pageId(), row.pageLabel(), row.pagePath(), row.pageType(), row.locale(),
                        row.fieldPath(), row.draftVersion()));
            }
        }
        return new WebsiteMediaDraftReplacementImpact(
                media.catalogAssetForReplacement(sourceMediaId),
                media.catalogAssetForReplacement(targetMediaId),
                List.copyOf(replaceable), published, archivedDraft);
    }

    @Transactional
    public WebsiteMediaDraftReplacementResult replace(
            String token,
            UUID sourceMediaId,
            WebsiteMediaDraftReplacementRequest request) {
        access.requireHeadquarters(token);
        validateRequest(sourceMediaId, request);
        Map<UUID, WebsiteMediaService.MediaAssetRow> lockedAssets = lockAssets(sourceMediaId, request.targetMediaId());
        WebsiteMediaService.MediaAssetRow source = lockedAssets.get(sourceMediaId);
        WebsiteMediaService.MediaAssetRow target = lockedAssets.get(request.targetMediaId());
        if (!"UPLOADED".equals(target.origin())) {
            throw new IllegalArgumentException("교체 대상은 새로 업로드한 미디어여야 합니다.");
        }
        if (source.version() != request.expectedSourceVersion() || target.version() != request.expectedTargetVersion()) {
            throw conflict();
        }

        WebsiteMediaDraftReplacementImpact current = impact(token, sourceMediaId, request.targetMediaId());
        List<TargetKey> expectedTargets = canonicalTargets(request.targets());
        List<TargetKey> currentTargets = current.replaceableUsages().stream()
                .map(usage -> new TargetKey(usage.pageId(), usage.locale(), usage.fieldPath(), usage.expectedDraftVersion()))
                .sorted(TargetKey.ORDER)
                .toList();
        if (currentTargets.isEmpty()) throw empty();
        if (!currentTargets.equals(expectedTargets)) throw conflict();

        Map<DraftKey, List<String>> drafts = new LinkedHashMap<>();
        for (TargetKey targetKey : currentTargets) {
            DraftKey draft = new DraftKey(targetKey.pageId(), targetKey.locale(), targetKey.expectedDraftVersion());
            drafts.computeIfAbsent(draft, ignored -> new ArrayList<>()).add(targetKey.fieldPath());
        }
        int replaced = 0;
        for (Map.Entry<DraftKey, List<String>> entry : drafts.entrySet()) {
            DraftKey draft = entry.getKey();
            replaced += "en".equals(draft.locale())
                    ? translations.replaceDraftMediaReferences(token, draft.pageId(), draft.expectedDraftVersion(),
                            source, target, entry.getValue())
                    : pages.replaceDraftMediaReferences(token, draft.pageId(), draft.expectedDraftVersion(),
                            source, target, entry.getValue());
        }
        return new WebsiteMediaDraftReplacementResult(
                sourceMediaId, request.targetMediaId(), replaced, drafts.size());
    }

    private List<UsageRow> usageRows(UUID sourceMediaId) {
        return jdbc.query("""
                select usage.page_id,
                       case when usage.locale = 'en' then translation.draft_menu_label else page.draft_menu_label end as page_label,
                       case when usage.locale = 'en' then translation.draft_path else page.draft_path end as page_path,
                       page.page_type, page.lifecycle_status, usage.document_state, usage.locale, usage.field_path,
                       case when usage.locale = 'en' then translation.draft_version else page.draft_version end as draft_version
                  from website_media_usage usage
                  join website_page page on page.id = usage.page_id
                  left join website_page_translation translation
                    on translation.page_id = usage.page_id and translation.locale = usage.locale
                 where usage.asset_id = ?
                 order by page_path, usage.locale, usage.field_path
                """, (rs, rowNumber) -> new UsageRow(
                rs.getObject("page_id", UUID.class), rs.getString("page_label"), rs.getString("page_path"),
                rs.getString("page_type"), rs.getString("lifecycle_status"), rs.getString("document_state"),
                rs.getString("locale"), rs.getString("field_path"), rs.getInt("draft_version")), sourceMediaId);
    }

    private void requireDifferentAssets(UUID sourceMediaId, UUID targetMediaId) {
        if (sourceMediaId == null || targetMediaId == null) {
            throw new IllegalArgumentException("원본과 교체 대상 미디어가 필요합니다.");
        }
        if (Objects.equals(sourceMediaId, targetMediaId)) {
            throw new IllegalArgumentException("원본과 교체 대상 미디어는 달라야 합니다.");
        }
    }

    private void validateRequest(UUID sourceMediaId, WebsiteMediaDraftReplacementRequest request) {
        if (request == null) throw new IllegalArgumentException("미디어 교체 요청이 필요합니다.");
        requireDifferentAssets(sourceMediaId, request.targetMediaId());
        if (request.expectedSourceVersion() <= 0 || request.expectedTargetVersion() <= 0) {
            throw new IllegalArgumentException("미디어 버전은 1 이상이어야 합니다.");
        }
        if (request.targets() == null || request.targets().isEmpty()) {
            throw new IllegalArgumentException("확인한 초안 사용 위치가 필요합니다.");
        }
    }

    private Map<UUID, WebsiteMediaService.MediaAssetRow> lockAssets(UUID sourceMediaId, UUID targetMediaId) {
        Map<UUID, WebsiteMediaService.MediaAssetRow> result = new HashMap<>();
        List.of(sourceMediaId, targetMediaId).stream().sorted().forEach(mediaId ->
                result.put(mediaId, media.requireReplacementSource(mediaId, " for update")));
        return result;
    }

    private List<TargetKey> canonicalTargets(List<WebsiteMediaDraftReplacementTarget> targets) {
        List<TargetKey> result = new ArrayList<>();
        Set<TargetKey> unique = new HashSet<>();
        for (WebsiteMediaDraftReplacementTarget target : targets) {
            if (target == null || target.pageId() == null || !List.of("ko", "en").contains(target.locale())
                    || target.fieldPath() == null || target.fieldPath().isBlank()
                    || target.expectedDraftVersion() <= 0) {
                throw new IllegalArgumentException("확인한 초안 사용 위치가 올바르지 않습니다.");
            }
            TargetKey key = new TargetKey(target.pageId(), target.locale(), target.fieldPath(), target.expectedDraftVersion());
            if (!unique.add(key)) throw new IllegalArgumentException("초안 사용 위치를 중복해서 보낼 수 없습니다.");
            result.add(key);
        }
        return result.stream().sorted(TargetKey.ORDER).toList();
    }

    static WebsiteMediaConflictException conflict() {
        return new WebsiteMediaConflictException("WEBSITE_MEDIA_REPLACEMENT_CONFLICT",
                "미디어 사용 위치가 변경되었습니다. 영향 범위를 다시 확인해 주세요.");
    }

    private WebsiteMediaConflictException empty() {
        return new WebsiteMediaConflictException("WEBSITE_MEDIA_REPLACEMENT_EMPTY",
                "교체할 활성 초안 사용 위치가 없습니다.");
    }

    private record UsageRow(
            UUID pageId,
            String pageLabel,
            String pagePath,
            String pageType,
            String lifecycleStatus,
            String documentState,
            String locale,
            String fieldPath,
            int draftVersion) {
    }

    private record DraftKey(UUID pageId, String locale, int expectedDraftVersion) {
    }

    private record TargetKey(UUID pageId, String locale, String fieldPath, int expectedDraftVersion) {
        private static final Comparator<TargetKey> ORDER = Comparator
                .comparing((TargetKey target) -> target.pageId().toString())
                .thenComparing(TargetKey::locale)
                .thenComparing(TargetKey::fieldPath)
                .thenComparingInt(TargetKey::expectedDraftVersion);
    }
}
