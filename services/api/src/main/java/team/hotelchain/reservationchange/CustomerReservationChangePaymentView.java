package team.hotelchain.reservationchange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerReservationChangePaymentView(
        UUID reservationId,
        String reservationNumberSuffix,
        LocalDate checkIn,
        LocalDate checkOut,
        String roomTypeName,
        String ratePlanName,
        long additionalAmountKrw,
        String currency,
        Instant expiresAt,
        String environmentLabel,
        String status) {
}
