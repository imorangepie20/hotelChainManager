package team.hotelchain.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 본사가 객실 유형의 일자별 요금을 바꾼 결과. 멱원 재호출은 요청했던
 * 일자만 다시 돌려준다. 전체 일자를 내보내면 다른 날짜의 가격이 바뀐 뒤
 * 재호출 응답이 달라져 멱원이 깨진다.
 */
public record RateAdjustResponse(
        UUID hotelId,
        UUID roomTypeId,
        UUID ratePlanId,
        List<AdjustedRate> days,
        boolean created) {

    public record AdjustedRate(LocalDate stayDate, int amountKrw) {
    }
}
