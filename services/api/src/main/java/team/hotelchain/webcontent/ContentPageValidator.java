package team.hotelchain.webcontent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ContentPageValidator {
    private static final Pattern LOCAL_PATH = Pattern.compile("(?:/|/[a-z0-9]+(?:-[a-z0-9]+)*(?:/[a-z0-9]+(?:-[a-z0-9]+)*)*)(?:#[a-z0-9-]+)?");
    private static final Pattern LOCAL_IMAGE = Pattern.compile("/images/[A-Za-z0-9][A-Za-z0-9._/-]*");
    private static final Pattern UPLOADED_IMAGE = Pattern.compile("/api/website/media/([0-9a-fA-F-]{36})/content");
    private static final Set<String> ROOT_FIELDS = Set.of("seo", "blocks");
    private static final Set<String> SEO_FIELDS = Set.of("title", "description");
    private static final Pattern TIME = Pattern.compile("(?:[01][0-9]|2[0-3]):[0-5][0-9]");

    public void validate(Map<String, Object> content) {
        if (content == null) throw invalid("문서가 필요합니다.");
        content = withoutBlockIds(content);
        rejectUnknown(content, ROOT_FIELDS, "문서");
        Map<?, ?> seo = object(content, "seo", "seo");
        rejectUnknown(seo, SEO_FIELDS, "seo");
        text(seo, "title", "seo.title", 60);
        text(seo, "description", "seo.description", 160);
        Object value = content.get("blocks");
        if (!(value instanceof List<?> blocks) || blocks.isEmpty() || blocks.size() > 20) {
            throw invalid("blocks는 1~20개의 배열이어야 합니다.");
        }
        int heroes = 0;
        for (int index = 0; index < blocks.size(); index++) {
            if (!(blocks.get(index) instanceof Map<?, ?> block)) throw invalid("blocks[" + index + "]는 객체여야 합니다.");
            String type = text(block, "type", "blocks[" + index + "].type", 20);
            switch (type) {
                case "HERO" -> {
                    heroes++;
                    hero(block, index);
                }
                case "TEXT" -> textBlock(block, index);
                case "CTA" -> ctaBlock(block, index);
                case "IMAGE_GALLERY" -> imageGallery(block, index);
                case "FEATURE_GRID" -> featureGrid(block, index);
                case "SPEC_TABLE" -> specificationTable(block, index);
                case "ACCORDION" -> accordion(block, index);
                case "NOTICE_LIST" -> noticeList(block, index);
                default -> throw invalid("blocks[" + index + "].type은 HERO, TEXT, CTA, IMAGE_GALLERY, FEATURE_GRID, SPEC_TABLE, ACCORDION, NOTICE_LIST만 사용할 수 있습니다.");
            }
        }
        if (!"HERO".equals(typeOf(blocks.getFirst()))) throw invalid("첫 blocks 항목은 HERO여야 합니다.");
        if (heroes != 1) throw invalid("HERO 블록은 한 번만 사용할 수 있습니다.");
    }

    public void validate(ContentKind kind, Map<String, Object> content) {
        if (kind == null || !ContentKind.contentPageKinds().contains(kind)) throw invalid("상세 콘텐츠 종류가 올바르지 않습니다.");
        if (content == null) throw invalid("문서가 필요합니다.");
        rejectUnknown(content, ROOT_FIELDS, "문서");
        Map<?, ?> seo = object(content, "seo", "seo");
        rejectUnknown(seo, SEO_FIELDS, "seo");
        text(seo, "title", "seo.title", 60);
        text(seo, "description", "seo.description", 160);
        Object value = content.get("blocks");
        if (!(value instanceof List<?> blocks) || blocks.isEmpty() || blocks.size() > 20) {
            throw invalid("blocks는 1~20개의 배열이어야 합니다.");
        }

        Set<String> blockIds = new HashSet<>();
        Set<String> types = new HashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            if (!(blocks.get(index) instanceof Map<?, ?> block)) throw invalid("blocks[" + index + "]는 객체여야 합니다.");
            String path = "blocks[" + index + "]";
            String blockId = text(block, "blockId", path + ".blockId", 36);
            if (!isUuid(blockId) || !blockIds.add(blockId)) throw invalid(path + ".blockId는 중복되지 않는 UUID여야 합니다.");
            String type = text(block, "type", path + ".type", 20);
            if (!allowedTypes(kind).contains(type)) throw invalid(path + ".type은 " + kind + " 유형에서 허용되지 않습니다.");
            validateTypedBlock(type, block, index);
            types.add(type);
        }
        requireTypedBlocks(kind, blocks, types);
    }

    private void validateTypedBlock(String type, Map<?, ?> block, int index) {
        Map<String, Object> legacyBlock = withoutBlockId(block);
        switch (type) {
            case "HERO" -> hero(legacyBlock, index);
            case "TEXT" -> textBlock(legacyBlock, index);
            case "CTA" -> ctaBlock(legacyBlock, index);
            case "IMAGE_GALLERY" -> imageGallery(legacyBlock, index);
            case "FEATURE_GRID" -> featureGrid(legacyBlock, index);
            case "SPEC_TABLE" -> specificationTable(legacyBlock, index);
            case "ACCORDION" -> accordion(legacyBlock, index);
            case "NOTICE_LIST" -> noticeList(legacyBlock, index);
            case "RICH_TEXT" -> richText(block, index);
            case "OPERATING_HOURS" -> operatingHours(block, index);
            case "LOCATION" -> location(block, index);
            case "PROMOTION_SUMMARY" -> promotionSummary(block, index);
            case "RELATED_COLLECTION" -> relatedCollection(block, index);
            case "BOOKING_CTA" -> bookingCta(block, index);
            default -> throw invalid("blocks[" + index + "].type은 허용되지 않습니다.");
        }
    }

    private Set<String> allowedTypes(ContentKind kind) {
        return switch (kind) {
            case ROOM -> Set.of("HERO", "IMAGE_GALLERY", "SPEC_TABLE", "BOOKING_CTA", "FEATURE_GRID", "ACCORDION", "NOTICE_LIST", "RELATED_COLLECTION", "RICH_TEXT");
            case DINING -> Set.of("HERO", "IMAGE_GALLERY", "OPERATING_HOURS", "RICH_TEXT", "LOCATION", "NOTICE_LIST", "ACCORDION");
            case FACILITY -> Set.of("HERO", "IMAGE_GALLERY", "SPEC_TABLE", "OPERATING_HOURS", "LOCATION", "ACCORDION", "NOTICE_LIST", "RICH_TEXT");
            case EXPERIENCE -> Set.of("HERO", "RICH_TEXT", "IMAGE_GALLERY", "SPEC_TABLE", "NOTICE_LIST", "BOOKING_CTA", "RELATED_COLLECTION");
            case PROMOTION -> Set.of("HERO", "PROMOTION_SUMMARY", "BOOKING_CTA", "IMAGE_GALLERY", "FEATURE_GRID", "NOTICE_LIST", "RELATED_COLLECTION");
            case GUIDE -> Set.of("HERO", "RICH_TEXT", "SPEC_TABLE", "LOCATION", "ACCORDION", "NOTICE_LIST");
            case BRAND -> Set.of("HERO", "RICH_TEXT", "TEXT", "CTA", "IMAGE_GALLERY", "FEATURE_GRID", "SPEC_TABLE", "ACCORDION", "NOTICE_LIST", "RELATED_COLLECTION");
            default -> Set.of();
        };
    }

    private void requireTypedBlocks(ContentKind kind, List<?> blocks, Set<String> types) {
        if (types.contains("HERO") && !"HERO".equals(typeOf(blocks.getFirst()))) throw invalid("HERO 블록은 첫 번째여야 합니다.");
        if (types.stream().filter("HERO"::equals).count() > 1) throw invalid("HERO 블록은 한 번만 사용할 수 있습니다.");
        switch (kind) {
            case ROOM -> require(types, kind, "HERO", "IMAGE_GALLERY", "SPEC_TABLE", "BOOKING_CTA");
            case DINING -> require(types, kind, "HERO", "IMAGE_GALLERY", "OPERATING_HOURS");
            case FACILITY -> {
                require(types, kind, "HERO", "IMAGE_GALLERY");
                if (!types.contains("SPEC_TABLE") && !types.contains("OPERATING_HOURS")) {
                    throw invalid("FACILITY 유형에는 SPEC_TABLE 또는 OPERATING_HOURS가 필요합니다.");
                }
            }
            case EXPERIENCE -> require(types, kind, "HERO", "RICH_TEXT");
            case PROMOTION -> require(types, kind, "HERO", "PROMOTION_SUMMARY", "BOOKING_CTA");
            case GUIDE, BRAND -> {
                if (!types.contains("HERO") && !types.contains("RICH_TEXT")) {
                    throw invalid(kind + " 유형에는 HERO 또는 RICH_TEXT가 필요합니다.");
                }
            }
            default -> throw invalid("상세 콘텐츠 종류가 올바르지 않습니다.");
        }
    }

    private void require(Set<String> types, ContentKind kind, String... required) {
        for (String type : required) {
            if (!types.contains(type)) throw invalid(kind + " 유형에는 " + type + " 블록이 필요합니다.");
        }
    }

    private void richText(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("blockId", "type", "eyebrow", "title", "paragraphs"), path);
        Map<String, Object> legacyBlock = withoutBlockId(block);
        legacyBlock.put("type", "TEXT");
        textBlock(legacyBlock, index);
    }

    private void operatingHours(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("blockId", "type", "title", "description", "entries", "exceptions", "location", "phone"), path);
        text(block, "title", path + ".title", 160);
        optionalText(block, "description", path + ".description", 1000);
        optionalText(block, "exceptions", path + ".exceptions", 1000);
        optionalText(block, "location", path + ".location", 200);
        optionalText(block, "phone", path + ".phone", 40);
        if (!(block.get("entries") instanceof List<?> entries) || entries.isEmpty() || entries.size() > 7) {
            throw invalid(path + ".entries는 1~7개의 배열이어야 합니다.");
        }
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            if (!(entries.get(entryIndex) instanceof Map<?, ?> entry)) throw invalid(path + ".entries[" + entryIndex + "]는 객체여야 합니다.");
            String entryPath = path + ".entries[" + entryIndex + "]";
            rejectUnknown(entry, Set.of("dayLabel", "opensAt", "closesAt", "closed"), entryPath);
            text(entry, "dayLabel", entryPath + ".dayLabel", 50);
            if (!(entry.get("closed") instanceof Boolean closed)) throw invalid(entryPath + ".closed는 true 또는 false여야 합니다.");
            if (closed) {
                if (entry.containsKey("opensAt") || entry.containsKey("closesAt")) throw invalid(entryPath + "는 휴무일에 운영 시간을 둘 수 없습니다.");
            } else {
                String opensAt = text(entry, "opensAt", entryPath + ".opensAt", 5);
                String closesAt = text(entry, "closesAt", entryPath + ".closesAt", 5);
                if (!TIME.matcher(opensAt).matches() || !TIME.matcher(closesAt).matches()) throw invalid(entryPath + " 운영 시간은 HH:mm 형식이어야 합니다.");
            }
        }
    }

    private void location(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("blockId", "type", "title", "address", "directions", "mapHref"), path);
        text(block, "title", path + ".title", 160);
        text(block, "address", path + ".address", 300);
        optionalText(block, "directions", path + ".directions", 1000);
        if (block.containsKey("mapHref")) {
            String href = text(block, "mapHref", path + ".mapHref", 255);
            if (!LOCAL_PATH.matcher(href).matches()) throw invalid(path + ".mapHref는 안전한 로컬 경로여야 합니다.");
        }
    }

    private void promotionSummary(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("blockId", "type", "title", "salesPeriod", "stayPeriod", "benefits", "displayPrice", "tags"), path);
        text(block, "title", path + ".title", 160);
        text(block, "salesPeriod", path + ".salesPeriod", 100);
        text(block, "stayPeriod", path + ".stayPeriod", 100);
        optionalText(block, "displayPrice", path + ".displayPrice", 100);
        textList(block, "benefits", path + ".benefits", 1, 6, 200);
        if (block.containsKey("tags")) textList(block, "tags", path + ".tags", 1, 6, 50);
    }

    private void relatedCollection(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("blockId", "type", "title", "kind", "targetHotelId", "maxItems"), path);
        text(block, "title", path + ".title", 160);
        String kind = text(block, "kind", path + ".kind", 20);
        if (!ContentKind.contentPageKinds().contains(parseKind(kind))) throw invalid(path + ".kind은 상세 콘텐츠 종류여야 합니다.");
        if (block.containsKey("targetHotelId") && !isUuid(text(block, "targetHotelId", path + ".targetHotelId", 36))) throw invalid(path + ".targetHotelId는 UUID여야 합니다.");
        if (!(block.get("maxItems") instanceof Integer maxItems) || maxItems < 1 || maxItems > 12) throw invalid(path + ".maxItems는 1~12여야 합니다.");
    }

    private void bookingCta(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("blockId", "type", "eyebrow", "title", "description", "label", "hotelId", "roomTypeId"), path);
        optionalText(block, "eyebrow", path + ".eyebrow", 100);
        text(block, "title", path + ".title", 160);
        text(block, "description", path + ".description", 1000);
        text(block, "label", path + ".label", 100);
        if (!isUuid(text(block, "hotelId", path + ".hotelId", 36))) throw invalid(path + ".hotelId는 UUID여야 합니다.");
        if (block.containsKey("roomTypeId") && !isUuid(text(block, "roomTypeId", path + ".roomTypeId", 36))) throw invalid(path + ".roomTypeId는 UUID여야 합니다.");
    }

    private ContentKind parseKind(String value) {
        try {
            return ContentKind.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("관련 콘텐츠 종류가 올바르지 않습니다.");
        }
    }

    private void textList(Map<?, ?> block, String key, String path, int minimum, int maximum, int textMaximum) {
        if (!(block.get(key) instanceof List<?> entries) || entries.size() < minimum || entries.size() > maximum) {
            throw invalid(path + "는 " + minimum + "~" + maximum + "개의 배열이어야 합니다.");
        }
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof String entry) || entry.isBlank() || entry.length() > textMaximum) {
                throw invalid(path + "[" + index + "]는 1~" + textMaximum + "자여야 합니다.");
            }
        }
    }

    private Map<String, Object> withoutBlockId(Map<?, ?> block) {
        Map<String, Object> copy = new LinkedHashMap<>();
        block.forEach((key, value) -> {
            if (key instanceof String name && !"blockId".equals(name)) copy.put(name, value);
        });
        return copy;
    }

    private Map<String, Object> withoutBlockIds(Map<String, Object> content) {
        Map<String, Object> copy = new LinkedHashMap<>(content);
        if (!(content.get("blocks") instanceof List<?> blocks)) return copy;
        List<Object> stripped = new ArrayList<>();
        for (Object rawBlock : blocks) {
            stripped.add(rawBlock instanceof Map<?, ?> block ? withoutBlockId(block) : rawBlock);
        }
        copy.put("blocks", stripped);
        return copy;
    }

    private void hero(Map<?, ?> block, int index) {
        rejectUnknown(block, Set.of("type", "imageAssetId", "imageSrc", "imageAlt", "eyebrow", "title", "description", "cta"), "blocks[" + index + "]");
        String imageAssetId = text(block, "imageAssetId", "blocks[" + index + "].imageAssetId", 36);
        if (!isUuid(imageAssetId)) throw invalid("blocks[" + index + "].imageAssetId는 미디어 UUID여야 합니다.");
        String imageSrc = text(block, "imageSrc", "blocks[" + index + "].imageSrc", 255);
        if (!isMediaDelivery(imageAssetId, imageSrc)) throw invalid("blocks[" + index + "].imageSrc는 선택한 미디어의 안전한 전달 경로여야 합니다.");
        text(block, "imageAlt", "blocks[" + index + "].imageAlt", 200);
        common(block, index);
        optionalCta(block, index);
    }

    private void textBlock(Map<?, ?> block, int index) {
        rejectUnknown(block, Set.of("type", "eyebrow", "title", "paragraphs"), "blocks[" + index + "]");
        text(block, "eyebrow", "blocks[" + index + "].eyebrow", 100);
        text(block, "title", "blocks[" + index + "].title", 160);
        Object value = block.get("paragraphs");
        if (!(value instanceof List<?> paragraphs) || paragraphs.isEmpty() || paragraphs.size() > 6) {
            throw invalid("blocks[" + index + "].paragraphs는 1~6개의 배열이어야 합니다.");
        }
        for (int paragraph = 0; paragraph < paragraphs.size(); paragraph++) {
            if (!(paragraphs.get(paragraph) instanceof String text) || text.isBlank() || text.length() > 1000) {
                throw invalid("blocks[" + index + "].paragraphs[" + paragraph + "]는 1~1000자여야 합니다.");
            }
        }
    }

    private void ctaBlock(Map<?, ?> block, int index) {
        rejectUnknown(block, Set.of("type", "eyebrow", "title", "description", "cta"), "blocks[" + index + "]");
        common(block, index);
        cta(object(block, "cta", "blocks[" + index + "].cta"), "blocks[" + index + "].cta");
    }

    private void imageGallery(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("type", "eyebrow", "title", "description", "items"), path);
        optionalText(block, "eyebrow", path + ".eyebrow", 100);
        text(block, "title", path + ".title", 160);
        optionalText(block, "description", path + ".description", 1000);
        Object value = block.get("items");
        if (!(value instanceof List<?> items) || items.size() < 2 || items.size() > 12) {
            throw invalid(path + ".items는 2~12개의 배열이어야 합니다.");
        }
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            if (!(items.get(itemIndex) instanceof Map<?, ?> item)) throw invalid(path + ".items[" + itemIndex + "]는 객체여야 합니다.");
            String itemPath = path + ".items[" + itemIndex + "]";
            rejectUnknown(item, Set.of("imageAssetId", "imageSrc", "imageAlt", "caption"), itemPath);
            String imageAssetId = text(item, "imageAssetId", itemPath + ".imageAssetId", 36);
            if (!isUuid(imageAssetId)) throw invalid(itemPath + ".imageAssetId는 미디어 UUID여야 합니다.");
            String imageSrc = text(item, "imageSrc", itemPath + ".imageSrc", 255);
            if (!isMediaDelivery(imageAssetId, imageSrc)) throw invalid(itemPath + ".imageSrc는 선택한 미디어의 안전한 전달 경로여야 합니다.");
            text(item, "imageAlt", itemPath + ".imageAlt", 200);
            optionalText(item, "caption", itemPath + ".caption", 300);
        }
    }

    private void featureGrid(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("type", "eyebrow", "title", "description", "items"), path);
        optionalText(block, "eyebrow", path + ".eyebrow", 100);
        text(block, "title", path + ".title", 160);
        optionalText(block, "description", path + ".description", 1000);
        Object value = block.get("items");
        if (!(value instanceof List<?> items) || items.size() < 2 || items.size() > 6) {
            throw invalid(path + ".items는 2~6개의 배열이어야 합니다.");
        }
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            if (!(items.get(itemIndex) instanceof Map<?, ?> item)) throw invalid(path + ".items[" + itemIndex + "]는 객체여야 합니다.");
            String itemPath = path + ".items[" + itemIndex + "]";
            rejectUnknown(item, Set.of("title", "description"), itemPath);
            text(item, "title", itemPath + ".title", 160);
            text(item, "description", itemPath + ".description", 500);
        }
    }

    private void specificationTable(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("type", "eyebrow", "title", "description", "rows"), path);
        optionalText(block, "eyebrow", path + ".eyebrow", 100);
        text(block, "title", path + ".title", 160);
        optionalText(block, "description", path + ".description", 1000);
        Object value = block.get("rows");
        if (!(value instanceof List<?> rows) || rows.isEmpty() || rows.size() > 12) {
            throw invalid(path + ".rows는 1~12개의 배열이어야 합니다.");
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (!(rows.get(rowIndex) instanceof Map<?, ?> row)) throw invalid(path + ".rows[" + rowIndex + "]는 객체여야 합니다.");
            String rowPath = path + ".rows[" + rowIndex + "]";
            rejectUnknown(row, Set.of("label", "value"), rowPath);
            text(row, "label", rowPath + ".label", 100);
            text(row, "value", rowPath + ".value", 300);
        }
    }

    private void accordion(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("type", "eyebrow", "title", "description", "items"), path);
        optionalText(block, "eyebrow", path + ".eyebrow", 100);
        text(block, "title", path + ".title", 160);
        optionalText(block, "description", path + ".description", 1000);
        Object value = block.get("items");
        if (!(value instanceof List<?> items) || items.isEmpty() || items.size() > 12) {
            throw invalid(path + ".items는 1~12개의 배열이어야 합니다.");
        }
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            if (!(items.get(itemIndex) instanceof Map<?, ?> item)) throw invalid(path + ".items[" + itemIndex + "]는 객체여야 합니다.");
            String itemPath = path + ".items[" + itemIndex + "]";
            rejectUnknown(item, Set.of("title", "content"), itemPath);
            text(item, "title", itemPath + ".title", 160);
            text(item, "content", itemPath + ".content", 2000);
        }
    }

    private void noticeList(Map<?, ?> block, int index) {
        String path = "blocks[" + index + "]";
        rejectUnknown(block, Set.of("type", "eyebrow", "title", "description", "items"), path);
        optionalText(block, "eyebrow", path + ".eyebrow", 100);
        text(block, "title", path + ".title", 160);
        optionalText(block, "description", path + ".description", 1000);
        Object value = block.get("items");
        if (!(value instanceof List<?> items) || items.isEmpty() || items.size() > 20) {
            throw invalid(path + ".items는 1~20개의 배열이어야 합니다.");
        }
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            if (!(items.get(itemIndex) instanceof Map<?, ?> item)) throw invalid(path + ".items[" + itemIndex + "]는 객체여야 합니다.");
            String itemPath = path + ".items[" + itemIndex + "]";
            rejectUnknown(item, Set.of("text", "severity"), itemPath);
            text(item, "text", itemPath + ".text", 1000);
            String severity = text(item, "severity", itemPath + ".severity", 20);
            if (!Set.of("DEFAULT", "IMPORTANT").contains(severity)) {
                throw invalid(itemPath + ".severity는 DEFAULT 또는 IMPORTANT여야 합니다.");
            }
        }
    }

    private void common(Map<?, ?> block, int index) {
        text(block, "eyebrow", "blocks[" + index + "].eyebrow", 100);
        text(block, "title", "blocks[" + index + "].title", 160);
        text(block, "description", "blocks[" + index + "].description", 1000);
    }

    private void optionalText(Map<?, ?> source, String key, String path, int maximumLength) {
        if (source.containsKey(key)) text(source, key, path, maximumLength);
    }

    private void optionalCta(Map<?, ?> block, int index) {
        if (block.containsKey("cta")) cta(object(block, "cta", "blocks[" + index + "].cta"), "blocks[" + index + "].cta");
    }

    private void cta(Map<?, ?> cta, String path) {
        rejectUnknown(cta, Set.of("label", "href"), path);
        text(cta, "label", path + ".label", 100);
        String href = text(cta, "href", path + ".href", 255);
        if (!LOCAL_PATH.matcher(href).matches()) throw invalid(path + ".href는 안전한 로컬 경로여야 합니다.");
    }

    private boolean isLocalImage(String imageSrc) {
        if (!LOCAL_IMAGE.matcher(imageSrc).matches()) return false;
        for (String segment : imageSrc.substring("/images/".length()).split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
        }
        return true;
    }

    private boolean isMediaDelivery(String imageAssetId, String imageSrc) {
        if (isLocalImage(imageSrc)) return true;
        var uploaded = UPLOADED_IMAGE.matcher(imageSrc);
        return uploaded.matches() && uploaded.group(1).equalsIgnoreCase(imageAssetId);
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String typeOf(Object block) {
        return block instanceof Map<?, ?> map && map.get("type") instanceof String type ? type : "";
    }

    private Map<?, ?> object(Map<?, ?> source, String key, String path) {
        if (source.get(key) instanceof Map<?, ?> object) return object;
        throw invalid(path + "는 객체여야 합니다.");
    }

    private String text(Map<?, ?> source, String key, String path, int maximumLength) {
        if (!(source.get(key) instanceof String value) || value.isBlank() || value.length() > maximumLength) {
            throw invalid(path + "는 1~" + maximumLength + "자여야 합니다.");
        }
        return value;
    }

    private void rejectUnknown(Map<?, ?> source, Set<String> allowed, String path) {
        if (source.keySet().stream().anyMatch(key -> !(key instanceof String name) || !allowed.contains(name))) {
            throw invalid(path + "에 허용되지 않은 필드가 있습니다.");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("콘텐츠 형식 오류: " + message);
    }
}
