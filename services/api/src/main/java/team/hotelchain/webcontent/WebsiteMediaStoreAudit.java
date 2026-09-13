package team.hotelchain.webcontent;

import java.util.List;

public record WebsiteMediaStoreAudit(
        String storeName,
        boolean healthy,
        List<String> missingStorageKeys,
        List<String> orphanStorageKeys,
        List<String> staleTemporaryStorageKeys) {
}
