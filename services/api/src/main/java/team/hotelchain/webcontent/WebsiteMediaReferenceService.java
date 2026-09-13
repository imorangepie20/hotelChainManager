package team.hotelchain.webcontent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebsiteMediaReferenceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final WebsiteMediaService media;

    public WebsiteMediaReferenceService(JdbcTemplate jdbc, ObjectMapper json, WebsiteMediaService media) {
        this.jdbc = jdbc;
        this.json = json;
        this.media = media;
    }

    public Map<String, Object> normalizeStructuredContent(Map<String, Object> content) {
        Map<String, Object> normalized = copy(content);
        Object blocksValue = normalized.get("blocks");
        if (!(blocksValue instanceof List<?> blocks)) return normalized;
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            Object value = blocks.get(blockIndex);
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> block = asWritableMap(raw);
            block.putIfAbsent("blockId", UUID.randomUUID().toString());
            if ("HERO".equals(raw.get("type"))) {
                normalizeHero(block, "imageAssetId", "imageSrc", "blocks[" + blockIndex + "]");
            } else if ("IMAGE_GALLERY".equals(raw.get("type"))) {
                normalizeGallery(block, blockIndex);
            }
        }
        return normalized;
    }

    public Map<String, Object> normalizeLandingContent(Map<String, Object> content) {
        Map<String, Object> normalized = copy(content);
        normalizeHero(normalized, "heroAssetId", "heroImage", "hero");
        return normalized;
    }

    public void synchronizeDraft(UUID pageId, String pageType, Map<String, Object> content) {
        synchronize(pageId, pageType, "ko", "DRAFT", content);
    }

    public void synchronizePublished(UUID pageId, String pageType, Map<String, Object> content) {
        synchronize(pageId, pageType, "ko", "PUBLISHED", content);
    }

    public void synchronize(UUID pageId, String pageType, String locale, String state, Map<String, Object> content) {
        jdbc.update("delete from website_media_usage where page_id = ? and locale = ? and document_state = ?", pageId, locale, state);
        if (content == null || content.isEmpty()) return;
        if ("HOTEL_LANDING".equals(pageType)) {
            insertUsage(pageId, locale, state, "heroAssetId", assetId(content.get("heroAssetId"), "heroAssetId"),
                    text(content.get("heroAlt"), "heroAlt"));
            return;
        }
        if (!"HOME_PAGE".equals(pageType) && !"CONTENT_PAGE".equals(pageType)) return;
        Object blocksValue = content.get("blocks");
        if (!(blocksValue instanceof List<?> blocks)) return;
        for (int index = 0; index < blocks.size(); index++) {
            if (!(blocks.get(index) instanceof Map<?, ?> block)) continue;
            if ("HERO".equals(block.get("type"))) {
                insertUsage(pageId, locale, state, "blocks[" + index + "].imageAssetId",
                        assetId(block.get("imageAssetId"), "blocks[" + index + "].imageAssetId"),
                        text(block.get("imageAlt"), "blocks[" + index + "].imageAlt"));
            } else if ("IMAGE_GALLERY".equals(block.get("type"))) {
                synchronizeGalleryUsages(pageId, locale, state, block, index);
            }
        }
    }

    private void normalizeGallery(Map<String, Object> block, int blockIndex) {
        Object value = block.get("items");
        if (!(value instanceof List<?> items)) return;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            if (!(items.get(itemIndex) instanceof Map<?, ?> item)) continue;
            normalizeHero(asWritableMap(item), "imageAssetId", "imageSrc",
                    "blocks[" + blockIndex + "].items[" + itemIndex + "]");
        }
    }

    private void synchronizeGalleryUsages(UUID pageId, String locale, String state, Map<?, ?> block, int blockIndex) {
        Object value = block.get("items");
        if (!(value instanceof List<?> items)) return;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            if (!(items.get(itemIndex) instanceof Map<?, ?> item)) continue;
            String path = "blocks[" + blockIndex + "].items[" + itemIndex + "]";
            insertUsage(pageId, locale, state, path + ".imageAssetId", assetId(item.get("imageAssetId"), path + ".imageAssetId"),
                    text(item.get("imageAlt"), path + ".imageAlt"));
        }
    }

    private void insertUsage(UUID pageId, String locale, String state, String fieldPath, UUID assetId, String altText) {
        jdbc.update("""
                insert into website_media_usage (asset_id, page_id, locale, document_state, field_path, alt_text)
                values (?, ?, ?, ?, ?, ?)
                """, assetId, pageId, locale, state, fieldPath, altText);
    }

    private void normalizeHero(Map<String, Object> target, String assetKey, String pathKey, String path) {
        UUID assetId = assetId(target.get(assetKey), path + "." + assetKey);
        WebsiteMediaService.MediaAssetRow asset = media.requireActiveAsset(assetId);
        String submittedPath = text(target.get(pathKey), path + "." + pathKey);
        if (!asset.deliveryPath().equals(submittedPath)) {
            throw invalid(path + "." + pathKey + "는 선택한 미디어의 전달 경로와 일치해야 합니다.");
        }
        target.put(assetKey, asset.id().toString());
        target.put(pathKey, asset.deliveryPath());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asWritableMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> copy(Map<String, Object> content) {
        if (content == null) throw invalid("콘텐츠 문서가 필요합니다.");
        return json.convertValue(content, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private UUID assetId(Object value, String path) {
        if (!(value instanceof String raw) || raw.isBlank()) throw invalid(path + "는 미디어 UUID여야 합니다.");
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + "는 미디어 UUID여야 합니다.");
        }
    }

    private String text(Object value, String path) {
        if (!(value instanceof String raw) || raw.isBlank() || raw.trim().length() > 200) {
            throw invalid(path + "는 1~200자여야 합니다.");
        }
        return raw.trim();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("미디어 참조 오류: " + message);
    }
}
