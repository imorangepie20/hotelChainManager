package team.hotelchain.hotel;

/**
 * 지점 생성 결과. {@code created}가 {@code false}면 멱원 재호출로 같은 지점을
 * 돌려주는 것이다.
 */
public record HotelCreateResponse(
        String hotelId,
        String name,
        String region,
        String timezone,
        int roomTypes,
        boolean created) {

    public static HotelCreateResponse created(java.util.UUID hotelId, HotelCreateRequest request, int roomTypes) {
        return new HotelCreateResponse(
                hotelId.toString(), request.normalizedName(), request.normalizedRegion(),
                request.timezone().trim(), roomTypes, true);
    }

    public static HotelCreateResponse existing(HotelSummary hotel, int roomTypes) {
        return new HotelCreateResponse(
                hotel.id().toString(), hotel.name(), hotel.region(), hotel.timezone(), roomTypes, false);
    }
}
