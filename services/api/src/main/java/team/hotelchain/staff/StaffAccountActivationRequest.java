package team.hotelchain.staff;

/**
 * 본사가 직원을 비활성하거나 다시 활성할 때 받는 요청이다.
 * <p>
 * {@code active}가 false면 비활성이고, true면 재활성이다. 비활성 직원은
 * 로그인과 세션 사용이 즉시 거부된다.
 */
public record StaffAccountActivationRequest(boolean active) {
}
