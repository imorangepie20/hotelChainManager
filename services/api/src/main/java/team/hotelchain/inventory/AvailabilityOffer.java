package team.hotelchain.inventory;

import java.util.List;
import java.util.UUID;

public record AvailabilityOffer(
        UUID roomTypeId,
        String roomTypeName,
        UUID ratePlanId,
        String ratePlanName,
        boolean breakfastIncluded,
        int remaining,
        List<NightlyPrice> nightlyPrices,
        int total,
        String currency,
        String policyVersion) {
}
