package team.hotelchain.webcontent;

import java.util.List;
import java.util.UUID;

public record ContentReferenceCatalog(List<Hotel> hotels, List<Page> pages) {
    public ContentReferenceCatalog {
        hotels = List.copyOf(hotels);
        pages = List.copyOf(pages);
    }

    public record Hotel(UUID id, String name, String region, List<RoomType> roomTypes) {
        public Hotel {
            roomTypes = List.copyOf(roomTypes);
        }
    }

    public record RoomType(UUID id, String name, int maxOccupancy) {
    }

    public record Page(UUID id, ContentKind contentKind, UUID hotelId, String title, String path) {
    }
}
