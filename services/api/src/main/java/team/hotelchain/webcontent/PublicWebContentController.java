package team.hotelchain.webcontent;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotels")
public class PublicWebContentController {
    private final WebContentService content;

    public PublicWebContentController(WebContentService content) { this.content = content; }

    @GetMapping("/{hotelId}/content")
    public Map<String, Object> published(@PathVariable UUID hotelId) { return content.published(hotelId); }
}
