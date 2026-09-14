package team.hotelchain.reservationchange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import team.hotelchain.inventory.NightlyPrice;

public record CustomerReservationChangeQuoteView(
        UUID quoteId, long baseOperationRevision, Instant expiresAt,
        LocalDate checkIn, LocalDate checkOut, int adults, int children, int rooms,
        long previousTotal, String currency, List<Offer> offers) {
    public record Offer(UUID roomTypeId, String roomTypeName, UUID ratePlanId, String ratePlanName,
            boolean breakfastIncluded, int remaining, List<NightlyPrice> nightlyPrices,
            long total, long difference, String currency) {
    }
}
