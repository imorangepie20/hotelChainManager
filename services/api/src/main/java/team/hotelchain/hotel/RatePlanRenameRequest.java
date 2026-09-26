package team.hotelchain.hotel;

/**
 * 본사가 요금제의 이름을 바꿀 때 받는 요청이다.
 * <p>
 * 조식 포함 여부·정책 버전은 확정 예약의 계약 조건이어서 이 요청으로
 * 바꿀 수 없다. 계약 조건이 다른 요금제가 필요하면 새 요금제를 만드는 것이
 * 맞다. 이것이 "두 번째 요금제"를 만드는 이유다.
 */
public record RatePlanRenameRequest(String name) {

    public static final int NAME_MAX_LENGTH = 100;

    public void validate() {
        if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "요금제 이름은 1자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
