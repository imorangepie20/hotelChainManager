package team.hotelchain.inventory;

import java.time.LocalDate;

public record NightlyPrice(LocalDate date, int amount) {
}
