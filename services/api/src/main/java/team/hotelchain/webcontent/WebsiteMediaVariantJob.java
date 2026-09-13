package team.hotelchain.webcontent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.hotelchain.webcontent.storage.WebsiteMediaObjectNotFoundException;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageGateway;

@Component
@ConditionalOnProperty(
        name = "website.media.variant-job-enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class WebsiteMediaVariantJob {
    private final WebsiteMediaVariantService variants;
    private final WebsiteMediaVariantEncoder encoder;
    private final WebsiteMediaStorageGateway storage;
    private final Path workDirectory;

    @Autowired
    public WebsiteMediaVariantJob(
            WebsiteMediaVariantService variants,
            WebsiteMediaVariantEncoder encoder,
            WebsiteMediaStorageGateway storage,
            @Value("${website.media.work-dir:}") String configuredWorkDirectory) {
        this.variants = variants;
        this.encoder = encoder;
        this.storage = storage;
        this.workDirectory = configuredWorkDirectory == null || configuredWorkDirectory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media-work")
                : Path.of(configuredWorkDirectory);
    }

    WebsiteMediaVariantJob(
            WebsiteMediaVariantService variants,
            WebsiteMediaVariantEncoder encoder,
            WebsiteMediaStorageGateway storage) {
        this(variants, encoder, storage, "");
    }

    @Scheduled(fixedDelayString = "${website.media.variant-scan-delay:2s}", scheduler = "websiteMediaVariantScheduler")
    public void generateNextVariant() {
        processNext();
    }

    boolean processNext() {
        var claimed = variants.claimNext();
        if (claimed.isEmpty()) return false;

        WebsiteMediaVariantService.VariantClaim claim = claimed.orElseThrow();
        String finalStorageKey = claim.assetId() + "-" + claim.targetWidth()
                + "-" + claim.variantId() + "-" + claim.attemptCount()
                + "-" + UUID.randomUUID() + ".webp";
        Path source = null;
        Path temporaryTarget = null;
        try {
            Path work = workDirectory.toAbsolutePath().normalize();
            Files.createDirectories(work);
            source = storage.materialize(claim.sourceStorageKey(), work);
            temporaryTarget = work.resolve(UUID.randomUUID() + ".webp.tmp");
            WebsiteMediaVariantEncoder.Result result = encoder.encode(source, temporaryTarget, claim.targetWidth());
            boolean completed = variants.completeReady(
                    claim.variantId(), claim.attemptCount(), claim.claimToken(),
                    finalStorageKey, result, Files.readAllBytes(temporaryTarget));
            if (!completed) return true;
        } catch (Exception exception) {
            variants.completeFailed(
                    claim.variantId(), claim.attemptCount(), claim.claimToken(), failureSummary(exception));
        } finally {
            deleteTemporaryFile(temporaryTarget);
            deleteTemporaryFile(source);
        }
        return true;
    }

    private String failureSummary(Exception exception) {
        if (exception instanceof WebsiteMediaObjectNotFoundException) {
            return "원본 미디어 파일을 찾을 수 없습니다.";
        }
        return "미디어 variant 생성에 실패했습니다.";
    }

    private void deleteTemporaryFile(Path temporaryTarget) {
        if (temporaryTarget == null) return;
        try {
            Files.deleteIfExists(temporaryTarget);
        } catch (IOException ignored) {
            // A later storage audit can remove an unreachable temporary file.
        }
    }
}
