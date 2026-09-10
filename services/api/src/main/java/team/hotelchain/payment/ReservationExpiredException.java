package team.hotelchain.payment;

import team.hotelchain.reservation.BusinessConflictException;

public class ReservationExpiredException extends BusinessConflictException {

    public ReservationExpiredException() {
        super("HOLD_EXPIRED", "예약 확보 시간이 만료되었습니다. 다시 검색해 주세요.");
    }
}
