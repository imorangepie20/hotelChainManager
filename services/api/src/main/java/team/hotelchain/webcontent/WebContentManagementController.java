package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/web-content")
public class WebContentManagementController {
    private final WebContentService content;
    public WebContentManagementController(WebContentService content) { this.content = content; }

    @GetMapping("/hotels/{hotelId}")
    public WebContentDocument draft(@PathVariable UUID hotelId, @RequestHeader("X-Staff-Session") String token) { return content.draft(token, hotelId); }

    @PutMapping("/hotels/{hotelId}")
    public WebContentDocument save(@PathVariable UUID hotelId, @RequestBody SaveWebContentRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return content.saveDraft(token, hotelId, request.expectedDraftVersion(), request.content(), request.page());
    }

    @PostMapping("/hotels/{hotelId}/publish")
    public WebContentDocument publish(@PathVariable UUID hotelId, @RequestBody PublishWebContentRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return content.publish(token, hotelId, request.expectedDraftVersion(), request.expectedPublishedVersion());
    }

    @GetMapping("/hotels/{hotelId}/versions")
    public List<WebContentVersion> versions(@PathVariable UUID hotelId, @RequestHeader("X-Staff-Session") String token) { return content.versions(token, hotelId); }
}
