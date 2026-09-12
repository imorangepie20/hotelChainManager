package team.hotelchain.webcontent;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/website/media")
public class PublicWebsiteMediaController {
    private final WebsiteMediaService media;

    public PublicWebsiteMediaController(WebsiteMediaService media) {
        this.media = media;
    }

    @GetMapping("/{mediaId}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID mediaId) {
        WebsiteMediaContent content = media.publicContent(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=0, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }
}
