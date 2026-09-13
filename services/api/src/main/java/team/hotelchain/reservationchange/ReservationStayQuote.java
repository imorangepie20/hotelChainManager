package team.hotelchain.reservationchange;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import team.hotelchain.inventory.NightlyPrice;
import team.hotelchain.reservation.BusinessConflictException;

public record ReservationStayQuote(
        UUID reservationId,
        UUID roomTypeId,
        UUID ratePlanId,
        UUID hotelId,
        String timezone,
        LocalDate previousCheckIn,
        LocalDate previousCheckOut,
        LocalDate targetCheckIn,
        LocalDate targetCheckOut,
        int adults,
        int children,
        int rooms,
        String reservationStatus,
        long previousTotalKrw,
        String currency,
        long operationRevision,
        int previousNights,
        int assignments,
        List<SelectedStayOffer> offers) {

    public SelectedStayOffer selected(UUID selectedRoomTypeId, UUID selectedRatePlanId) {
        return offers.stream()
                .filter(offer -> offer.roomTypeId().equals(selectedRoomTypeId)
                        && offer.ratePlanId().equals(selectedRatePlanId))
                .findFirst()
                .orElseThrow(() -> new BusinessConflictException(
                        "SOLD_OUT", "선택한 객실 유형과 요금제를 이용할 수 없습니다."));
    }

    public record SelectedStayOffer(
            UUID roomTypeId,
            String roomTypeName,
            UUID ratePlanId,
            String ratePlanName,
            boolean breakfastIncluded,
            int maxOccupancy,
            int remaining,
            List<NightlyPrice> nightlyPrices,
            long totalKrw,
            long differenceKrw,
            String currency) {
    }
}
