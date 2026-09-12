package team.hotelchain.webcontent;

public record WebsiteMediaMetadataRequest(
        String displayName,
        String defaultAltText,
        int expectedVersion) {
}
