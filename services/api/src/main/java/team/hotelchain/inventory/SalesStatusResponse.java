package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 본사가 객실 유형의 날짜 구간 판매 상태를 바꾼 결과.
 * <p>
 * {@code created}가 {@code false}면 멱원 재호출로 같은 구간을 돌려주는 것이다.
 * {@code stoppedDays}는 해당 유형에서 현재 {@code STOPPED}인 일자 수다.
 */
public record SalesStatusResponse(
        UUID hotelId,
        UUID roomTypeId,
        LocalDate fromDate,
        LocalDate toDate,
        String status,
        int stoppedDays,
        boolean created) {
}
