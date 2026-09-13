package team.hotelchain.reservationchange;

import java.time.Instant;
import java.time.LocalDate;

public record CustomerReservationChangePaymentView(
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
