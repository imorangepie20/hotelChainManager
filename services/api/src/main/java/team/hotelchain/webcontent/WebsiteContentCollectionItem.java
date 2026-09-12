package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsiteContentCollectionItem(
        UUID id,
        ContentKind contentKind,
        String path,
        String title,
        String summary,
        String image,
        String hotelSlug) {
}
