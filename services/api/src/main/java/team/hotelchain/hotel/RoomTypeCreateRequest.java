package team.hotelchain.hotel;

/**
 * 본사가 객실 유형을 만들 때 받는 요청이다.
 * 이름은 1~100자, 최대 인원은 1 이상 20 이하. 검증은 서버가 최종 판단한다.
 */
public record RoomTypeCreateRequest(
        String name,
        int maxOccupancy) {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int MIN_OCCUPANCY = 1;
    public static final int MAX_OCCUPANCY = 20;

    public void validate() {
        if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "객실 유형 이름은 1자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (maxOccupancy < MIN_OCCUPANCY || maxOccupancy > MAX_OCCUPANCY) {
            throw new IllegalArgumentException(
                    "최대 인원은 " + MIN_OCCUPANCY + " 이상 " + MAX_OCCUPANCY + " 이하여야 합니다.");
        }
    }
}
