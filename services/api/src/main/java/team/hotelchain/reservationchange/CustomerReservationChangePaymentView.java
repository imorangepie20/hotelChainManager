package team.hotelchain.reservationchange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerReservationChangePaymentView(
        UUID reservationId,
        String reservationNumberSuffix,
        LocalDate previousCheckIn,
        LocalDate previousCheckOut,
        String previousRoomTypeName,
        String previousRatePlanName,
        long previousTotalKrw,
        LocalDate checkIn,
        LocalDate checkOut,
        String roomTypeName,
        String ratePlanName,
        long totalKrw,
        long differenceKrw,
        long additionalAmountKrw,
        String currency,
        Instant expiresAt,
        String environmentLabel,
        String status) {
}
