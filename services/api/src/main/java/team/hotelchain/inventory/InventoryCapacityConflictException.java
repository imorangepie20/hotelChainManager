package team.hotelchain.inventory;

/**
 * 본사가 일자 재고의 총량을 확정·보류 건수 아래로 내리려고 할 때 발생한다.
 * 재고는 음수가 될 수 없으므로 값을 바꾸지 않고 409로 거부한다.
 */
public class InventoryCapacityConflictException extends RuntimeException {

    private final int conflictingDays;

    public InventoryCapacityConflictException(int conflictingDays) {
        super("재고 총량을 내릴 수 없습니다. " + conflictingDays
                + "개 일자의 확정·보류 건수가 새 총량을 초과합니다.");
        this.conflictingDays = conflictingDays;
    }

    public int conflictingDays() {
        return conflictingDays;
    }
}
