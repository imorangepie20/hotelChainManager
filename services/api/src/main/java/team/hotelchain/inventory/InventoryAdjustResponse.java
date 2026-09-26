package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 일자 재고 조정 결과. 멱원 재호출은 저장된 객실 유형의 현재 재고를
 * 그대로 돌려준다. created가 false면 이미 처리된 요청의 결과를 다시 보낸 것이다.
 */
public record InventoryAdjustResponse(
        UUID hotelId,
        UUID roomTypeId,
        List<AdjustedDay> days,
        boolean created) {

    public record AdjustedDay(
            LocalDate stayDate,
            int capacity,
            int held,
            int confirmed,
            int remaining) {
    }
}
