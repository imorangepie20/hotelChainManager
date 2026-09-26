package team.hotelchain.hotel;

import java.util.UUID;

/**
 * 객실 유형 수정 결과. 멱원 재호출은 여기에 저장된 roomTypeId를 그대로 돌려준다.
 * created가 false면 이미 처리된 요청의 결과를 다시 보낸 것이다.
 * <p>
 * {@code ratePlan}은 이 객실 유형의 기본 요금제 현재 상태다. 요청이 조식 포함
 * 여부나 기본 요금을 바꿨든 안 바꿨든 현재값을 돌려주므로 멱원 재호출이
 * 같은 응답을 내려준다. 요금제가 없으면 {@code null}이다.
 */
public record RoomTypeUpdateResponse(
        UUID roomTypeId,
        UUID hotelId,
        String name,
        int maxOccupancy,
        boolean created,
        RatePlanSummary ratePlan) {

    public record RatePlanSummary(
            UUID ratePlanId,
            String ratePlanName,
            boolean breakfastIncluded,
            int defaultRateKrw,
            int pricedDays) {
    }
}
