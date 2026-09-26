package team.hotelchain.hotel;

/**
 * 본사가 객실 유형의 이름·최대 인원을 바꿀 때 받는 요청이다.
 * 이름은 1~100자, 최대 인원은 1 이상 20 이하. 검증은 서버가 최종 판단한다.
 * <p>
 * 최대 인원은 예약 가능 조건(max_occupancy * rooms &gt;= partySize)에 직결되므로
 * 값을 내리면 진행 중인 예약과 충돌하는지 별도로 검증한다.
 * <p>
 * {@code breakfastIncluded}·{@code defaultRateKrw}는 null이면 변경하지 않는다.
 * 둘은 기본 요금제의 속성이지 객실 유형의 속성이 아니므로, 객실 유형 행을
 * 바꾸는 이 요청에서는 보내지 않은 필드를 건드리지 않는다.
 */
public record RoomTypeUpdateRequest(
        String name,
        Integer maxOccupancy,
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
        if (maxOccupancy == null || maxOccupancy < MIN_OCCUPANCY || maxOccupancy > MAX_OCCUPANCY) {
            throw new IllegalArgumentException(
                    "최대 인원은 " + MIN_OCCUPANCY + " 이상 " + MAX_OCCUPANCY + " 이하여야 합니다.");
        }
        if (defaultRateKrw != null && (defaultRateKrw < MIN_RATE_KRW || defaultRateKrw > MAX_RATE_KRW)) {
            throw new IllegalArgumentException(
                    "기본 요금은 " + MIN_RATE_KRW + "원 이상 " + MAX_RATE_KRW + "원 이하여야 합니다.");
        }
    }
}
