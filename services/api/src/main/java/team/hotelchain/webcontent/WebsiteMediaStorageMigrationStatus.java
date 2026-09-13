package team.hotelchain.webcontent;

public record WebsiteMediaStorageMigrationStatus(
        String mode,
        int total,
        int both,
        int localOnly,
        int s3Only,
        int mismatch,
        int missing,
        long fallbackCount) {
}
