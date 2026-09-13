package team.hotelchain.webcontent;

import java.util.List;

public record WebsiteMediaStorageBackfillResult(
        int examined,
        int copied,
        int skipped,
        int mismatch,
        int failed,
        List<String> failedStorageKeys) {
}
