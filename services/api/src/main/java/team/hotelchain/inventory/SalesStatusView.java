package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 객실 유형의 판매 중지 구간 목록. SELECT만 사용하고 재고를 변경하지 않는다.
 */
public record SalesStatusView(
        UUID hotelId,
        UUID roomTypeId,
        List<StoppedRange> stoppedRanges) {

    public record StoppedRange(LocalDate fromDate, LocalDate toDate) {
    }
}
