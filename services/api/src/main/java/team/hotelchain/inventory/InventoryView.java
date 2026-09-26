package team.hotelchain.inventory;

import java.time.LocalDate;
import java.util.List;

public record InventoryView(
        java.util.UUID hotelId,
        List<RoomTypeInventory> roomTypes) {

    public record RoomTypeInventory(
            java.util.UUID roomTypeId,
            String name,
            int maxOccupancy,
            List<InventoryDay> days) {
    }

    // salesStatus는 총량과 별개다. STOPPED여도 capacity는 그대로여서
    // 재개하면 중지 전과 같은 재고가 돌아온다.
    public record InventoryDay(
            LocalDate stayDate,
            int capacity,
            int held,
            int confirmed,
            int remaining,
            String salesStatus) {
    }
}
