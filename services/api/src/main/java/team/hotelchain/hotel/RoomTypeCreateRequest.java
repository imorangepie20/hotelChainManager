package team.hotelchain.hotel;

/**
 * 본사가 객실 유형을 만들 때 받는 요청이다.
 * 이름은 1~100자, 최대 인원은 1 이상 20 이하. 검증은 서버가 최종 판단한다.
 * <p>
 * {@code breakfastIncluded}·{@code defaultRateKrw}는 함께 심기는 기본 요금제의
 * 조식 포함 여부와 일자별 기본 요금이다. 본사가 만든 유형이 고객 검색에
 * 나타나려면 요금·재고가 있어야 하므로 생성 시점에 같이 정한다.
 */
public record RoomTypeCreateRequest(
        String name,
        int maxOccupancy,
        Boolean breakfastIncluded,
        Integer defaultRateKrw) {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int MIN_OCCUPANCY = 1;
    public static final int MAX_OCCUPANCY = 20;
    public static final int MIN_RATE_KRW = 0;
    public static final int MAX_RATE_KRW = 10_000_000;

    public void validate() {
        if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "객실 유형 이름은 1자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (maxOccupancy < MIN_OCCUPANCY || maxOccupancy > MAX_OCCUPANCY) {
            throw new IllegalArgumentException(
                    "최대 인원은 " + MIN_OCCUPANCY + " 이상 " + MAX_OCCUPANCY + " 이하여야 합니다.");
        }
        if (defaultRateKrw != null && (defaultRateKrw < MIN_RATE_KRW || defaultRateKrw > MAX_RATE_KRW)) {
            throw new IllegalArgumentException(
                    "기본 요금은 " + MIN_RATE_KRW + "원 이상 " + MAX_RATE_KRW + "원 이하여야 합니다.");
        }
    }

    /**
     * 조식 포함 여부를 보내지 않으면 조식 미포함(객실만)으로 취급한다.
     * 폼에서는 항상 값을 보내지만, 멱원 재호출처럼 본문이 유실될 수 있으므로
     * 기본값을 정해 둔다.
     */
    public boolean breakfastIncludedOrFalse() {
        return Boolean.TRUE.equals(breakfastIncluded);
    }
}
