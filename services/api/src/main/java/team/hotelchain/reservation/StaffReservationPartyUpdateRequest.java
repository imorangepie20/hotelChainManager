package team.hotelchain.reservation;

import java.math.BigDecimal;

public record StaffReservationPartyUpdateRequest(BigDecimal adults, BigDecimal children) {
    public StaffReservationPartyUpdateRequest(int adults, int children) {
        this(BigDecimal.valueOf(adults), BigDecimal.valueOf(children));
    }
}
