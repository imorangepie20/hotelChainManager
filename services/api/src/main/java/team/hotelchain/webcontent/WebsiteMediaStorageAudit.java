package team.hotelchain.webcontent;

import java.time.OffsetDateTime;
import java.util.List;

public record WebsiteMediaStorageAudit(
        OffsetDateTime checkedAt,
        boolean healthy,
        List<String> missingStorageKeys,
        List<String> orphanStorageKeys,
        List<String> staleTemporaryStorageKeys,
        String mode,
        List<WebsiteMediaStoreAudit> stores) {
}
