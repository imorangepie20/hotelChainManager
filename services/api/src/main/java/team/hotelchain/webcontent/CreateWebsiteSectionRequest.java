package team.hotelchain.webcontent;

import java.util.UUID;

public record CreateWebsiteSectionRequest(UUID hotelId, String slug, String menuLabel, int menuOrder) {
}
