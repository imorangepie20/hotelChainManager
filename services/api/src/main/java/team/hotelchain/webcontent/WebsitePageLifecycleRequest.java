package team.hotelchain.webcontent;

public record WebsitePageLifecycleRequest(
        int expectedLifecycleVersion,
        int expectedDraftVersion,
        int expectedPublishedVersion) {
}
