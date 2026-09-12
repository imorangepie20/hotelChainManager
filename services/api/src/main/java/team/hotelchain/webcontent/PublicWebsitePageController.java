package team.hotelchain.webcontent;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/website")
public class PublicWebsitePageController {
    private final WebsitePageService pages;

    public PublicWebsitePageController(WebsitePageService pages) {
        this.pages = pages;
    }

    @GetMapping("/navigation")
    public List<WebsiteNavigationItem> navigation() {
        return pages.navigation();
    }

    @GetMapping("/pages/resolve")
    public PublishedWebsitePage resolve(@RequestParam String path) {
        return pages.resolvePublished(path);
    }

    @GetMapping("/collections")
    public List<WebsiteContentCollectionItem> collections(@RequestParam(required = false) String hotelSlug,
            @RequestParam ContentKind kind) {
        return pages.publishedCollection(hotelSlug, kind);
    }
}
