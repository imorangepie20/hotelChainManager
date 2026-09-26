package team.hotelchain.reports;

import java.util.List;

/**
 * 본사가 읽는 지점별 객실 유형별 매출. SELECT만 사용하므로 상태를 변경하지 않는다.
 * <p>
 * 매출은 실제 숙박으로 이어진 예약만 인정하므로 취소·노쇼 건의 금액은 합계에서 뺀다.
 */
public record RoomTypeRevenueView(
        String from,
        String to,
        int days,
        String hotelId,
        String hotelName,
        List<RoomTypeRevenueRow> roomTypes,
        Totals totals) {

    public record RoomTypeRevenueRow(
            String roomTypeId,
            String roomTypeName,
            long reservations,
            long cancelled,
            long noShow,
            long revenueKrw,
            double revenueShare) {
    }

    public record Totals(
            long reservations,
            long cancelled,
            long noShow,
            long revenueKrw) {
    }
}
