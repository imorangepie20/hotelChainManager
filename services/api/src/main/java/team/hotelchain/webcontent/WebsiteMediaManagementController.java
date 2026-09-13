package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/staff/website/media")
public class WebsiteMediaManagementController {
    private final WebsiteMediaService media;
    private final WebsiteMediaVariantService variants;
    private final WebsiteMediaDraftReplacementService replacements;
    private final WebsiteMediaStorageAuditService storageAudit;

    public WebsiteMediaManagementController(
            WebsiteMediaService media,
            WebsiteMediaVariantService variants,
            WebsiteMediaDraftReplacementService replacements,
            WebsiteMediaStorageAuditService storageAudit) {
        this.media = media;
        this.variants = variants;
        this.replacements = replacements;
        this.storageAudit = storageAudit;
    }

    @GetMapping
    public List<WebsiteMediaAsset> catalog(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestHeader("X-Staff-Session") String token) {
        return media.catalog(token, includeArchived);
    }

    @GetMapping("/storage-audit")
    public WebsiteMediaStorageAudit storageAudit(@RequestHeader("X-Staff-Session") String token) {
        return storageAudit.audit(token);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WebsiteMediaAsset upload(
            @RequestParam MultipartFile file,
            @RequestParam String displayName,
            @RequestParam String defaultAltText,
            @RequestHeader("X-Staff-Session") String token) {
        return media.upload(token, file, displayName, defaultAltText);
    }

    @GetMapping("/{mediaId}/usages")
    public List<WebsiteMediaUsage> usages(@PathVariable UUID mediaId, @RequestHeader("X-Staff-Session") String token) {
        return media.usages(token, mediaId);
    }

    @GetMapping("/{mediaId}/draft-replacement-impact")
    public WebsiteMediaDraftReplacementImpact replacementImpact(
            @PathVariable UUID mediaId,
            @RequestParam UUID targetMediaId,
            @RequestHeader("X-Staff-Session") String token) {
        return replacements.impact(token, mediaId, targetMediaId);
    }

    @PostMapping("/{mediaId}/draft-replacements")
    public WebsiteMediaDraftReplacementResult replaceDraftUsages(
            @PathVariable UUID mediaId,
            @RequestBody WebsiteMediaDraftReplacementRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return replacements.replace(token, mediaId, request);
    }

    @PatchMapping("/{mediaId}")
    public WebsiteMediaAsset updateMetadata(
            @PathVariable UUID mediaId,
            @RequestBody WebsiteMediaMetadataRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return media.updateMetadata(token, mediaId, request);
    }

    @PostMapping("/{mediaId}/archive")
    public WebsiteMediaAsset archive(
            @PathVariable UUID mediaId,
            @RequestBody WebsiteMediaVersionRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return media.archive(token, mediaId, request);
    }

    @PostMapping("/{mediaId}/restore")
    public WebsiteMediaAsset restore(
            @PathVariable UUID mediaId,
            @RequestBody WebsiteMediaVersionRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        return media.restore(token, mediaId, request);
    }

    @PostMapping("/{mediaId}/variants/{targetWidth}/retry")
    public WebsiteMediaAsset retryVariant(
            @PathVariable UUID mediaId,
            @PathVariable int targetWidth,
            @RequestHeader("X-Staff-Session") String token) {
        variants.retry(token, mediaId, targetWidth);
        return media.catalogAssetForManagement(mediaId);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDelete(
            @PathVariable UUID mediaId,
            @RequestBody WebsiteMediaVersionRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        media.permanentlyDelete(token, mediaId, request);
    }
}
