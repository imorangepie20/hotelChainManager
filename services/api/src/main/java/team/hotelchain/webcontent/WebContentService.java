package team.hotelchain.webcontent;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WebContentService {
    private static final int SEO_TITLE_MAX_LENGTH = 60;
    private static final int SEO_DESCRIPTION_MAX_LENGTH = 160;

    private final WebsitePageService pages;

    public WebContentService(WebsitePageService pages) {
        this.pages = pages;
    }

    public Map<String, Object> published(java.util.UUID hotelId) {
        return pages.publishedLandingContent(hotelId);
    }

    public WebContentDocument draft(String token, java.util.UUID hotelId) {
        return document(pages.landingDraft(token, hotelId));
    }

    public WebContentDocument saveDraft(String token, java.util.UUID hotelId, int expectedDraftVersion,
            Map<String, Object> content) {
        return saveDraft(token, hotelId, expectedDraftVersion, content, null);
    }

    public WebContentDocument saveDraft(String token, java.util.UUID hotelId, int expectedDraftVersion,
            Map<String, Object> content, WebsitePageDraftMetadata metadata) {
        return document(pages.saveLandingDraft(token, hotelId, expectedDraftVersion, metadata, content));
    }

    public WebContentDocument publish(String token, java.util.UUID hotelId, int expectedDraftVersion,
            int expectedPublishedVersion) {
        return document(pages.publishLanding(token, hotelId, expectedDraftVersion, expectedPublishedVersion));
    }

    public List<WebContentVersion> versions(String token, java.util.UUID hotelId) {
        return pages.landingVersions(token, hotelId);
    }

    private WebContentDocument document(WebsitePageDocument page) {
        return new WebContentDocument(page.draftContent(), page.draftVersion(), page.publishedContent(), page.publishedVersion(),
                page.draftMetadata(), page.publishedMetadata());
    }

    static void validateLandingContent(Map<String, Object> content) {
        if (content == null) throw invalid("콘텐츠 문서가 필요합니다.");
        requireText(content, "heroImage", "heroImage");
        requireText(content, "heroAlt", "heroAlt");
        requireText(content, "eyebrow", "eyebrow");
        requireText(content, "title", "title");
        requireText(content, "description", "description");

        Map<?, ?> arrival = requireObject(content, "arrival", "arrival");
        requireText(arrival, "address", "arrival.address");
        requireText(arrival, "checkInOut", "arrival.checkInOut");
        requireText(arrival, "highlight", "arrival.highlight");

        validateCards(content, "experiences", List.of("category", "title", "description"));
        validateCards(content, "offers", List.of("title", "detail", "bookingPeriod", "stayPeriod"));
        if (content.containsKey("seo")) validateSeo(content);
    }

    private static void validateSeo(Map<String, Object> content) {
        Map<?, ?> seo = requireObject(content, "seo", "seo");
        requireTextWithinLimit(seo, "title", "seo.title", SEO_TITLE_MAX_LENGTH);
        requireTextWithinLimit(seo, "description", "seo.description", SEO_DESCRIPTION_MAX_LENGTH);
    }

    private static void validateCards(Map<String, Object> content, String key, List<String> fields) {
        Object value = content.get(key);
        if (!(value instanceof List<?> cards)) throw invalid(key + "는 배열이어야 합니다.");
        for (int index = 0; index < cards.size(); index++) {
            if (!(cards.get(index) instanceof Map<?, ?> card)) {
                throw invalid(key + "[" + index + "]는 객체여야 합니다.");
            }
            for (String field : fields) requireText(card, field, key + "[" + index + "]." + field);
        }
    }

    private static Map<?, ?> requireObject(Map<String, Object> content, String key, String path) {
        if (content.get(key) instanceof Map<?, ?> value) return value;
        throw invalid(path + "는 객체여야 합니다.");
    }

    private static void requireText(Map<?, ?> content, String key, String path) {
        if (!(content.get(key) instanceof String value) || value.isBlank()) {
            throw invalid(path + "는 비어 있을 수 없습니다.");
        }
    }

    private static void requireTextWithinLimit(Map<?, ?> content, String key, String path, int maximumLength) {
        requireText(content, key, path);
        String value = (String) content.get(key);
        if (value.length() > maximumLength) {
            throw invalid(path + "는 " + maximumLength + "자를 넘을 수 없습니다.");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("콘텐츠 형식 오류: " + message);
    }
}
