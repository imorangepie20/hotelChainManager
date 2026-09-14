package team.hotelchain.reservationchange;

import java.time.LocalDate;

public record CustomerReservationChangeQuoteRequest(
        LocalDate checkIn, LocalDate checkOut, int adults, int children) {
}
