package team.hotelchain.reservation;

import java.util.List;
import java.util.UUID;

import team.hotelchain.inventory.NightlyPrice;

public record StaffReservationStayChangeOffer(
        UUID roomTypeId,
        String roomTypeName,
        UUID ratePlanId,
        String ratePlanName,
        boolean breakfastIncluded,
        int remaining,
        List<NightlyPrice> nightlyPrices,
        long totalKrw,
        long differenceKrw,
        String currency) {
}
