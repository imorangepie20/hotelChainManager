package team.hotelchain.inventory;

import java.util.List;

public record AvailabilityResponse(List<AvailabilityOffer> offers) {
}
