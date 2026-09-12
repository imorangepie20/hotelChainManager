package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsitePageVersionComparison(
        UUID pageId,
        WebsitePageVersionSnapshot base,
        WebsitePageVersionSnapshot compare) {
}
