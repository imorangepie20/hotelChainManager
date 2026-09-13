package team.hotelchain.webcontent;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/website/pages/preview")
public class PublicWebsitePreviewController {
    private final WebsitePreviewGrantService previews;
    private final WebsitePreviewTransport transport;

    public PublicWebsitePreviewController(WebsitePreviewGrantService previews, WebsitePreviewTransport transport) {
        this.previews = previews;
        this.transport = transport;
    }

    @GetMapping
    public ResponseEntity<PublishedWebsitePage> preview(
            @RequestParam String path, @RequestParam String locale,
            @RequestHeader(value = "X-Website-Preview", required = false) String token, HttpServletRequest request) {
        transport.requireSecure(request);
        WebsitePreviewResult result = previews.resolve(token, path, locale);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Website-Preview-Expires-At", result.expiresAt().toString())
                .body(result.page());
    }
}
