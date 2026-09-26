package team.hotelchain.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 본사가 객실 유형의 일자별 요금을 바꿀 때 받는 요청이다.
 * <p>
 * 요금제의 {@code rate_day} 금액을 날짜별로 덮어쓴다. 주말·계절 차등은
 * 이 요청으로 만드는 것이 아니라, 본사가 날짜별로 다른 금액을 보낼 때
 * 자연스럽게 생긴다. 금액은 0원 이상 10,000,000원 이하다.
 * <p>
 * {@code ratePlanId}는 둘 이상의 요금제가 있는 유형에서 어느 요금제의
 * 금액을 바꾸는지 지정한다. 명시하지 않으면 기본 요금제를 쓴다.
 */
public record RateAdjustRequest(UUID roomTypeId, UUID ratePlanId, List<DayRate> adjustments) {

    public record DayRate(LocalDate stayDate, int amountKrw) {
    }

    /**
     * 경로 변수의 객실 유형을 본문에 덮어쓴다. 본문과 경로가 달라도
     * 본문을 믿지 않고 경로를 따른다.
     */
    RateAdjustRequest withRoomTypeId(UUID roomTypeId) {
        return new RateAdjustRequest(roomTypeId, ratePlanId, adjustments);
    }

    /**
     * 경로 변수의 요금제를 본문에 덮어쓴다. 쿼리 파라미터로 요금제를
     * 지정한 경우에 본문보다 경로를 따른다.
     */
    RateAdjustRequest withRatePlanId(UUID ratePlanId) {
        return new RateAdjustRequest(roomTypeId, ratePlanId, adjustments);
    }

    private static final int MAX_AMOUNT_KRW = 10_000_000;
    private static final int MAX_DAYS_PER_REQUEST = 92;

    void validate() {
        if (roomTypeId == null) {
            throw new IllegalArgumentException("객실 유형을 지정해 주세요.");
        }
        if (adjustments == null || adjustments.isEmpty()) {
            throw new IllegalArgumentException("바꿀 날짜와 금액을 하나 이상 입력해 주세요.");
        }
        if (adjustments.size() > MAX_DAYS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "한 번에 바꿀 수 있는 일자는 최대 " + MAX_DAYS_PER_REQUEST + "일입니다.");
        }
        for (DayRate rate : adjustments) {
            if (rate.stayDate() == null) {
                throw new IllegalArgumentException("날짜를 입력해 주세요.");
            }
            if (rate.amountKrw() < 0 || rate.amountKrw() > MAX_AMOUNT_KRW) {
                throw new IllegalArgumentException(
                        "금액은 0원 이상 " + MAX_AMOUNT_KRW + "원 이하여야 합니다.");
            }
        }
    }
}
