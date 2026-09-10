package team.hotelchain.reservation;

import java.time.LocalDate;

public record ReservationNight(LocalDate date, int amount) {
}
