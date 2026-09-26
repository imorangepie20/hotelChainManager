package team.hotelchain.inventory;

/**
 * 본사가 일자 재고를 조정하려고 했지만 해당 객실 유형에 그 일자의 재고가
 * 없을 때 발생한다. 시드되지 않은 일자는 판매할 수 없으므로 404로 거부한다.
 */
public class InventoryDayNotFoundException extends RuntimeException {

    public InventoryDayNotFoundException() {
        super("해당 객실 유형의 일자 재고가 없습니다. 객실 유형을 먼저 추가해 주세요.");
    }
}
