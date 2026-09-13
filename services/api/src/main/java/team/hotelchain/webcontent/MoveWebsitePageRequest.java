package team.hotelchain.webcontent;

import java.util.UUID;

public record MoveWebsitePageRequest(UUID parentId, String slug, int expectedDraftVersion, int expectedLifecycleVersion,
        int expectedPublishedVersion) {
    public MoveWebsitePageRequest(UUID parentId, String slug, int expectedDraftVersion, int expectedLifecycleVersion) {
        this(parentId, slug, expectedDraftVersion, expectedLifecycleVersion, 0);
    }
}
