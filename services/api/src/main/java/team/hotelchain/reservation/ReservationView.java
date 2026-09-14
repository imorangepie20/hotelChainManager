package team.hotelchain.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReservationView(
        UUID id,
        String status,
        LocalDate checkIn,
        LocalDate checkOut,
        int rooms,
        Instant expiresAt,
        long total,
        String currency,
        List<ReservationNight> nightlyPrices,
        String cancellationPolicy,
        ReservationGuest guest,
        String roomTypeName, String ratePlanName, int adults, int children,
        String paymentStatus, CancellationPolicyDetails cancellationPolicyDetails) {
}
