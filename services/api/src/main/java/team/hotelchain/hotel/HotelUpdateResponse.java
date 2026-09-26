package team.hotelchain.hotel;

/**
 * 지점 수정 결과. {@code changed}가 {@code false}면 멱원 재호출로 같은 결과를
 * 돌려주는 것이다.
 */
public record HotelUpdateResponse(
        String hotelId,
        String name,
        String region,
        String timezone,
        int roomTypes,
        boolean changed) {

    public static HotelUpdateResponse of(java.util.UUID hotelId, String name, String region,
            String timezone, int roomTypes, boolean changed) {
        return new HotelUpdateResponse(hotelId.toString(), name, region, timezone, roomTypes, changed);
    }
}
