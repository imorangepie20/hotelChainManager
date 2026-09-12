package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;

public record WebsitePageConnections(
        List<UUID> roomTypeIds,
        List<UUID> targetHotelIds,
        List<WebsitePageRelation> relatedPages) {
    public WebsitePageConnections {
        roomTypeIds = roomTypeIds == null ? List.of() : List.copyOf(roomTypeIds);
        targetHotelIds = targetHotelIds == null ? List.of() : List.copyOf(targetHotelIds);
        relatedPages = relatedPages == null ? List.of() : List.copyOf(relatedPages);
    }

    public static WebsitePageConnections empty() {
        return new WebsitePageConnections(List.of(), List.of(), List.of());
    }
}
