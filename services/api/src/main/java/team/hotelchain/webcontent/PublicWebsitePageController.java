package team.hotelchain.webcontent;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/website")
public class PublicWebsitePageController {
    private final WebsitePageService pages;
    private final WebsiteTranslationService translations;

    public PublicWebsitePageController(WebsitePageService pages, WebsiteTranslationService translations) {
        this.pages = pages;
        this.translations = translations;
    }

    public List<WebsiteNavigationItem> navigation() {
        return pages.navigation();
    }

    @GetMapping("/navigation")
    public List<WebsiteNavigationItem> navigation(@RequestParam(required = false) String locale) {
        return WebsiteTranslationService.locale(locale, null).equals("en") ? translations.navigation() : pages.navigation();
    }

    public ResponseEntity<PublishedWebsitePage> resolve(String path) { return resolve(path, null); }

    @GetMapping("/pages/resolve")
    public ResponseEntity<PublishedWebsitePage> resolve(@RequestParam String path, @RequestParam(required = false) String locale) {
        boolean english = WebsiteTranslationService.locale(locale, path).equals("en");
        if (!english && (path.equals("/en") || path.startsWith("/en/"))) throw new WebsitePageNotFoundException(path);
        try {
            return ResponseEntity.ok(english ? translations.resolve(path) : pages.resolvePublished(path));
        } catch (WebsitePageNotFoundException exception) {
            String redirectTarget = english ? translations.redirectTarget(path.startsWith("/en/") ? path : WebsiteTranslationService.englishPath(path)) : pages.redirectTarget(path);
            if (redirectTarget == null) throw exception;
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .location(URI.create(redirectTarget))
                    .build();
        }
    }

    public List<WebsiteContentCollectionItem> collections(String hotelSlug, ContentKind kind) {
        return pages.publishedCollection(hotelSlug, kind);
    }

    @GetMapping("/collections")
    public List<WebsiteContentCollectionItem> collections(@RequestParam(required = false) String hotelSlug,
            @RequestParam ContentKind kind, @RequestParam(required = false) String locale) {
        return WebsiteTranslationService.locale(locale, null).equals("en") ? translations.collection(hotelSlug, kind) : pages.publishedCollection(hotelSlug, kind);
    }
}
