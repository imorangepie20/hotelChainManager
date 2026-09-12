package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/website/pages/{pageId}/translations/en")
public class WebsiteTranslationController {
    private final WebsiteTranslationService translations;
    public WebsiteTranslationController(WebsiteTranslationService translations) { this.translations = translations; }

    @GetMapping
    public WebsitePageDocument draft(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token) {
        return translations.draft(token, pageId);
    }
    @PostMapping
    public WebsitePageDocument initialize(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token, @RequestBody InitializeTranslationRequest request) {
        return translations.initialize(token, pageId, request.expectedSourceDraftVersion(), request.expectedLifecycleVersion());
    }
    @PutMapping
    public WebsitePageDocument save(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token, @RequestBody SaveWebsitePageRequest request) {
        return translations.save(token, pageId, request);
    }
    @GetMapping("/review")
    public WebsiteTranslationReviewState review(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token) {
        return translations.review(token, pageId);
    }
    @PostMapping("/review/request")
    public WebsiteTranslationReviewState requestReview(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token,
            @RequestBody WebsiteTranslationReviewActionRequest request) {
        return translations.requestReview(token, pageId, request);
    }
    @PostMapping("/review/approve")
    public WebsiteTranslationReviewState approveReview(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token,
            @RequestBody WebsiteTranslationReviewActionRequest request) {
        return translations.approveReview(token, pageId, request);
    }
    @PostMapping("/review/reject")
    public WebsiteTranslationReviewState rejectReview(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token,
            @RequestBody WebsiteTranslationReviewActionRequest request) {
        return translations.rejectReview(token, pageId, request);
    }
    @PostMapping("/publish")
    public WebsitePageDocument publish(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token, @RequestBody PublishWebsitePageRequest request) {
        return translations.publish(token, pageId, request);
    }
    @GetMapping("/versions")
    public List<WebContentVersion> versions(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token) {
        return translations.versions(token, pageId);
    }
    public record InitializeTranslationRequest(int expectedSourceDraftVersion, int expectedLifecycleVersion) {}
}
