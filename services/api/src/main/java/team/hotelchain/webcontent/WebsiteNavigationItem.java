package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;

public record WebsiteNavigationItem(UUID id, UUID hotelId, String label, String path, List<WebsiteNavigationItem> children) {
}
