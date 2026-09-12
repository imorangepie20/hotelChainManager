package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/website")
public class WebsitePageManagementController {
    private final WebsitePageService pages;

    public WebsitePageManagementController(WebsitePageService pages) {
        this.pages = pages;
    }

    @GetMapping("/home")
    public WebsitePageDocument home(@RequestHeader("X-Staff-Session") String token) {
        return pages.homeDraft(token);
    }

    @PutMapping("/home")
    public WebsitePageDocument saveHome(@RequestBody SaveHomePageRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return pages.saveHomeDraft(token, request.expectedDraftVersion(), request.content());
    }

    @PostMapping("/home/publish")
    public WebsitePageDocument publishHome(@RequestBody PublishWebsitePageRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return pages.publishHome(token, request.expectedDraftVersion(), request.expectedPublishedVersion());
    }

    @GetMapping("/home/versions")
    public List<WebContentVersion> homeVersions(@RequestHeader("X-Staff-Session") String token) {
        return pages.homeVersions(token);
    }

    @GetMapping("/pages")
    public List<WebsitePageTreeItem> pages(@RequestHeader("X-Staff-Session") String token) {
        return pages.staffTree(token);
    }

    @GetMapping("/content-reference")
    public ContentReferenceCatalog contentReference(@RequestHeader("X-Staff-Session") String token) {
        return pages.contentReference(token);
    }

    @PostMapping("/pages")
    public WebsitePageDocument create(@RequestBody CreateWebsitePageRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        if (request.contentKind() == null) {
            return pages.createContentPage(token, request.parentId(), request.page(), request.content());
        }
        return pages.createContentPage(token, request.parentId(), request.contentKind(), request.hotelId(),
                request.page(), request.content(), request.connections());
    }

    @GetMapping("/pages/{pageId}")
    public WebsitePageDocument page(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token) {
        return pages.pageDraft(token, pageId);
    }

    @GetMapping("/pages/{pageId}/move-impact")
    public WebsitePageMoveImpact moveImpact(@PathVariable UUID pageId, @RequestParam UUID parentId, @RequestParam String slug,
            @RequestHeader("X-Staff-Session") String token) {
        return pages.moveImpact(token, pageId, parentId, slug);
    }

    @PostMapping("/pages/{pageId}/move")
    public WebsitePageDocument move(@PathVariable UUID pageId, @RequestBody MoveWebsitePageRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return request.expectedPublishedVersion() > 0
                ? pages.movePublishedContentPage(token, pageId, request)
                : pages.moveContentPage(token, pageId, request);
    }

    @PutMapping("/pages/{pageId}")
    public WebsitePageDocument save(@PathVariable UUID pageId, @RequestBody SaveWebsitePageRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        if (request.connections() == null) {
            return pages.saveContentPageDraft(token, pageId, request.expectedDraftVersion(), request.page(), request.content());
        }
        return pages.saveContentPageDraft(token, pageId, request.expectedDraftVersion(), request.page(), request.content(), request.connections());
    }

    @PostMapping("/pages/{pageId}/publish")
    public WebsitePageDocument publish(@PathVariable UUID pageId, @RequestBody PublishWebsitePageRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return pages.publishPage(token, pageId, request.expectedDraftVersion(), request.expectedPublishedVersion());
    }

    @PostMapping("/pages/{pageId}/archive")
    public WebsitePageDocument archive(@PathVariable UUID pageId, @RequestBody WebsitePageLifecycleRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return pages.archiveContentPage(token, pageId, request);
    }

    @PostMapping("/pages/{pageId}/restore")
    public WebsitePageDocument restore(@PathVariable UUID pageId, @RequestBody WebsitePageLifecycleRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return pages.restoreContentPage(token, pageId, request);
    }

    @DeleteMapping("/pages/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID pageId, @RequestBody WebsitePageLifecycleRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        pages.deleteArchivedContentPage(token, pageId, request);
    }

    @PostMapping("/pages/{pageId}/versions/{sourceVersion}/restore-draft")
    public WebsitePageDocument restoreVersionDraft(@PathVariable UUID pageId, @PathVariable int sourceVersion,
            @RequestBody WebsitePageLifecycleRequest request, @RequestHeader("X-Staff-Session") String token) {
        return pages.restoreContentPageVersionDraft(token, pageId, sourceVersion, request);
    }

    @GetMapping("/pages/{pageId}/versions")
    public List<WebContentVersion> versions(@PathVariable UUID pageId, @RequestHeader("X-Staff-Session") String token) {
        return pages.pageVersions(token, pageId);
    }

    @GetMapping("/pages/{pageId}/versions/compare")
    public WebsitePageVersionComparison compareVersions(@PathVariable UUID pageId, @RequestParam int baseVersion,
            @RequestParam int compareVersion, @RequestHeader("X-Staff-Session") String token) {
        return pages.compareContentPageVersions(token, pageId, baseVersion, compareVersion);
    }
}
