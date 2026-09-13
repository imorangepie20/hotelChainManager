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
    private final WebsiteMediaVariantService variants;

    public PublicWebsiteMediaController(WebsiteMediaService media, WebsiteMediaVariantService variants) {
        this.media = media;
        this.variants = variants;
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

    @GetMapping("/{mediaId}/variants/{targetWidth}.webp")
    public ResponseEntity<byte[]> variant(@PathVariable UUID mediaId, @PathVariable int targetWidth) {
        WebsiteMediaContent content = variants.publicContent(mediaId, targetWidth);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }
}
