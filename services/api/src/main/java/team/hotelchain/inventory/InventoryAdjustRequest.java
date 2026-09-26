package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 본사가 객실 유형의 일자 재고 총량을 바꿀 때 받는 요청이다.
 * <p>
 * 총량은 0 이상 1,000 이하. 음수가 될 수 없고, 한 유형의 총량이 수천 실이 되는
 * 경우는 이 MVP에 없다. 검증은 서버가 최종 판단한다.
 */
public record InventoryAdjustRequest(
        UUID roomTypeId,
        List<DayAdjustment> adjustments) {

    public static final int MIN_CAPACITY = 0;
    public static final int MAX_CAPACITY = 1_000;

    public InventoryAdjustRequest {
        if (adjustments == null) {
            adjustments = List.of();
        } else {
            adjustments = List.copyOf(adjustments);
        }
    }

    public void validate() {
        if (roomTypeId == null) {
            throw new IllegalArgumentException("객실 유형을 선택해 주세요.");
        }
        if (adjustments.isEmpty()) {
            throw new IllegalArgumentException("조정할 날짜를 최소 하루 선택해 주세요.");
        }
        for (DayAdjustment adjustment : adjustments) {
            adjustment.validate();
        }
    }

    public record DayAdjustment(LocalDate stayDate, int capacity) {

        public void validate() {
            if (stayDate == null) {
                throw new IllegalArgumentException("조정할 날짜를 입력해 주세요.");
            }
            if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
                throw new IllegalArgumentException(
                        "재고 총량은 " + MIN_CAPACITY + " 이상 " + MAX_CAPACITY + " 이하여야 합니다.");
            }
        }
    }
}
