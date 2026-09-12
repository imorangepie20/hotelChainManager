package team.hotelchain.webcontent;

import java.util.UUID;

public record WebsitePageRelation(UUID targetPageId, String relationType, int displayOrder) {
}
