package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityQuery(
        UUID hotelId,
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children,
        int rooms) {
}
