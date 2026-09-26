package team.hotelchain.hotel;

import java.util.List;
import java.util.UUID;

/**
 * 본사·지점 직원이 객실 유형의 전체 요금제를 읽는다. SELECT만 사용한다.
 * <p>
 * 카탈로그 본문이 {@link RoomTypeCatalogView.RatePlanSummary}로 같은 정보를
 * 내려주지만, 요금제 화면은 유형 하나의 요금제만 단독으로 읽는다.
 */
public record RatePlanListView(
        UUID hotelId,
        UUID roomTypeId,
        List<RatePlanSummary> ratePlans) {

    public record RatePlanSummary(
            UUID ratePlanId,
            String name,
            boolean breakfastIncluded,
            String policyVersion,
            int pricedDays,
            Integer minAmountKrw,
            Integer maxAmountKrw) {
    }
}
