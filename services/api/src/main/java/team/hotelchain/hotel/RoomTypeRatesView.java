package team.hotelchain.hotel;

import java.util.List;
import java.util.UUID;

/**
 * 본사가 객실 유형의 일자별 요금을 읽은 결과. SELECT만 사용한다.
 * <p>
 * {@code ratePlanId}가 null이면 요금제가 없는 유형이고 {@code days}는 비어 있다.
 */
public record RoomTypeRatesView(
        UUID hotelId,
        UUID roomTypeId,
        UUID ratePlanId,
        String ratePlanName,
        List<RateDay> days) {

    public record RateDay(java.time.LocalDate stayDate, int amountKrw) {
    }
}
