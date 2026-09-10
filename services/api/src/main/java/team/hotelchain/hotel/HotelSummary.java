package team.hotelchain.hotel;

import java.util.UUID;

public record HotelSummary(UUID id, String name, String region, String timezone) {
}
