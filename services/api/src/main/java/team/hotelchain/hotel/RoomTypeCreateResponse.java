package team.hotelchain.hotel;

import java.util.List;
import java.util.UUID;

/**
 * 객실 유형 생성 결과. 멱원 재호출은 여기에 저장된 roomTypeId를 그대로 돌려준다.
 * created가 false면 이미 처리된 요청의 결과를 다시 보낸 것이다.
 * <p>
 * 시드 필드는 본사가 방금 만든 유형이 바로 예약 가능한지 확인하게 해 준다.
 * 요금·재고가 없는 유형은 고객 검색에 나타나지 않는다.
 */
public record RoomTypeCreateResponse(
        UUID roomTypeId,
        UUID hotelId,
        String name,
        int maxOccupancy,
        boolean created,
        SeedSummary seed) {

    public record SeedSummary(
            UUID ratePlanId,
            String ratePlanName,
            int pricedDays,
            int defaultRateKrw,
            int inventoryCapacity,
            boolean created) {
    }

    public static RoomTypeCreateResponse created(
            UUID roomTypeId, UUID hotelId, String name, int maxOccupancy, SeedSummary seed) {
        return new RoomTypeCreateResponse(roomTypeId, hotelId, name, maxOccupancy, true, seed);
    }

    public static RoomTypeCreateResponse existing(
            UUID roomTypeId, UUID hotelId, String name, int maxOccupancy, SeedSummary seed) {
        return new RoomTypeCreateResponse(roomTypeId, hotelId, name, maxOccupancy, false, seed);
    }

    public SeedSummary seedOrEmpty() {
        return seed != null ? seed : new SeedSummary(null, null, 0, 0, 0, false);
    }

    public static SeedSummary noSeed() {
        return new SeedSummary(null, null, 0, 0, 0, false);
    }

    public record RateDayRow(java.time.LocalDate stayDate, int amountKrw) {
    }

    public record InventoryDayRow(java.time.LocalDate stayDate, int capacity) {
    }

    public record SeedBatch(
            UUID ratePlanId,
            String ratePlanName,
            List<RateDayRow> rateDays,
            List<InventoryDayRow> inventoryDays,
            int defaultRateKrw,
            int inventoryCapacity) {
    }
}
