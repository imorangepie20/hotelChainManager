package team.hotelchain.webcontent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "website.media.variant-job-enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class WebsiteMediaVariantJob {
    private final WebsiteMediaVariantService variants;
    private final WebsiteMediaVariantEncoder encoder;
    private final Path storageDirectory;

    public WebsiteMediaVariantJob(
            WebsiteMediaVariantService variants,
            WebsiteMediaVariantEncoder encoder,
            @Value("${website.media.storage-dir:}") String configuredStorageDirectory) {
        this.variants = variants;
        this.encoder = encoder;
        this.storageDirectory = configuredStorageDirectory == null || configuredStorageDirectory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media")
                : Path.of(configuredStorageDirectory);
    }

    @Scheduled(fixedDelayString = "${website.media.variant-scan-delay:2s}", scheduler = "websiteMediaVariantScheduler")
    public void generateNextVariant() {
        processNext();
    }

    boolean processNext() {
        var claimed = variants.claimNext();
        if (claimed.isEmpty()) return false;

        WebsiteMediaVariantService.VariantClaim claim = claimed.orElseThrow();
        Path root = storageDirectory.toAbsolutePath().normalize();
        String finalStorageKey = claim.assetId() + "-" + claim.targetWidth()
                + "-" + claim.variantId() + "-" + claim.attemptCount()
                + "-" + UUID.randomUUID() + ".webp";
        Path source = root.resolve(claim.sourceStorageKey()).normalize();
        Path target = root.resolve(finalStorageKey).normalize();
        Path temporaryTarget = root.resolve(finalStorageKey + ".tmp").normalize();
        try {
            requireInsideRoot(root, source);
            requireInsideRoot(root, target);
            requireInsideRoot(root, temporaryTarget);
            if (!Files.isRegularFile(source)) throw new IOException("원본 미디어 파일을 찾을 수 없습니다.");
            Files.createDirectories(root);
            WebsiteMediaVariantEncoder.Result result = encoder.encode(source, temporaryTarget, claim.targetWidth());
            boolean completed = variants.completeReady(
                    claim.variantId(), claim.attemptCount(), claim.claimToken(),
                    finalStorageKey, result, temporaryTarget, target);
            if (!completed) deleteTemporaryFile(temporaryTarget);
        } catch (Exception exception) {
            deleteTemporaryFile(temporaryTarget);
            variants.completeFailed(
                    claim.variantId(), claim.attemptCount(), claim.claimToken(), failureSummary(exception));
        }
        return true;
    }

    private void requireInsideRoot(Path root, Path path) throws IOException {
        if (!path.startsWith(root)) throw new IOException("미디어 저장 경로가 올바르지 않습니다.");
    }

    private String failureSummary(Exception exception) {
        if (exception instanceof IOException && "원본 미디어 파일을 찾을 수 없습니다.".equals(exception.getMessage())) {
            return "원본 미디어 파일을 찾을 수 없습니다.";
        }
        if (exception instanceof IOException && "미디어 저장 경로가 올바르지 않습니다.".equals(exception.getMessage())) {
            return "미디어 저장 경로가 올바르지 않습니다.";
        }
        return "미디어 variant 생성에 실패했습니다.";
    }

    private void deleteTemporaryFile(Path temporaryTarget) {
        try {
            Files.deleteIfExists(temporaryTarget);
        } catch (IOException ignored) {
            // A later storage audit can remove an unreachable temporary file.
        }
    }
}
