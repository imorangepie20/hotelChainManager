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

    public record InventoryDay(
            LocalDate stayDate,
            int capacity,
            int held,
            int confirmed,
            int remaining) {
    }
}
