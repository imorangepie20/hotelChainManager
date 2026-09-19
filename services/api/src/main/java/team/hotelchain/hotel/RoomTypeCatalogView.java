package team.hotelchain.hotel;

import java.util.List;
import java.util.UUID;

public record RoomTypeCatalogView(
        UUID roomTypeId,
        String name,
        int maxOccupancy,
        List<RatePlanSummary> ratePlans) {

    public record RatePlanSummary(
            UUID ratePlanId,
            String name,
            boolean breakfastIncluded,
            String policyVersion,
            int pricedDays,
            Integer minAmountKrw,
            Integer maxAmountKrw,
            Double avgAmountKrw) {
    }
}
