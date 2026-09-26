package team.hotelchain.hotel;

/**
 * 지점 판매 상태 전환 본문. {@code active}가 {@code false}면 판매 중지, {@code true}면 재개다.
 */
public record HotelActivationRequest(Boolean active) {
}
