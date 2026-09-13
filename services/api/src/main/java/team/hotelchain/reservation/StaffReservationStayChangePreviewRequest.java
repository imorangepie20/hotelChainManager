package team.hotelchain.reservation;

import java.time.LocalDate;

public record StaffReservationStayChangePreviewRequest(LocalDate checkIn, LocalDate checkOut) {
}
