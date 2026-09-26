package team.hotelchain.inventory;

/**
 * 본사가 객실 유형의 날짜 구간 판매 상태를 바꿀 때 쓴다.
 * <p>
 * 총량과 별개로 동작한다. {@code status}가 {@code STOPPED}면 해당 구간의
 * 신규 판매를 막고 기존 예약은 그대로 둔다. 지점 전체 판매 중지와 같은
 * 원칙이다.
 * <p>
 * 검증은 서버가 최종 판단한다.
 */
public record SalesStatusRequest(java.time.LocalDate fromDate, java.time.LocalDate toDate, String status) {

    public static final int MAX_RANGE_DAYS = 92;

    public void validate() {
        if (fromDate == null) {
            throw new IllegalArgumentException("시작 날짜를 입력해 주세요.");
        }
        if (toDate == null) {
            throw new IllegalArgumentException("종료 날짜를 입력해 주세요.");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜와 같거나 이후여야 합니다.");
        }
        if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
            throw new IllegalArgumentException("판매 상태를 변경할 수 있는 기간은 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("판매 상태를 선택해 주세요.");
        }
        if (!status.equals("OPEN") && !status.equals("STOPPED")) {
            throw new IllegalArgumentException("판매 상태는 OPEN 또는 STOPPED 여야 합니다.");
        }
    }
}
