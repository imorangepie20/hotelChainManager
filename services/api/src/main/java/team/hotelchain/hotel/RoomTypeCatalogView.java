package team.hotelchain.hotel;

import java.util.List;
import java.util.UUID;

public record RoomTypeCatalogView(
        UUID roomTypeId,
        String name,
        int maxOccupancy,
        boolean breakfastIncluded,
        Integer defaultRateKrw,
        List<RatePlanSummary> ratePlans) {

    public record RatePlanSummary(
            UUID ratePlanId,
            String name,
            boolean breakfastIncluded,
            String policyVersion,
            int pricedDays,
            Integer minAmountKrw,
            Integer maxAmountKrw,
            Double avgAmountKrw) {
    }

    /**
     * 본사가 수정 화면에 미리 채울 기본 요금제의 현재값.
     * 요금제가 없으면 모두 {@code null}이다.
     */
    public record RoomDefaults(
            UUID ratePlanId,
            String ratePlanName,
            boolean breakfastIncluded,
            Integer defaultRateKrw) {
    }
}
