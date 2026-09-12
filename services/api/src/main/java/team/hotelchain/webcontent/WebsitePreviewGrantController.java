package team.hotelchain.webcontent;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/website")
public class WebsitePreviewGrantController {
    private final WebsitePreviewGrantService previews;
    private final WebsitePreviewTransport transport;

    public WebsitePreviewGrantController(WebsitePreviewGrantService previews, WebsitePreviewTransport transport) {
        this.previews = previews;
        this.transport = transport;
    }

    @PostMapping("/pages/{pageId}/preview-grants")
    public WebsitePreviewGrantResponse issue(
            @PathVariable UUID pageId,
            @RequestBody WebsitePreviewGrantRequest request,
            @RequestHeader("X-Staff-Session") String token, HttpServletRequest httpRequest) {
        transport.requireSecure(httpRequest);
        return previews.issue(token, pageId, request);
    }

    @DeleteMapping("/preview-grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable UUID grantId,
            @RequestHeader("X-Staff-Session") String token, HttpServletRequest httpRequest) {
        transport.requireSecure(httpRequest);
        previews.revoke(token, grantId);
    }
}
