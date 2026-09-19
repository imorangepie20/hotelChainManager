package team.hotelchain.reports;

import java.util.List;

/**
 * 본사가 읽는 운영 통계. SELECT만 사용하므로 상태를 변경하지 않는다.
 */
public record OperationsReportView(
        String from,
        String to,
        int days,
        List<HotelOperationsMetrics> hotels,
        Totals totals) {

    public record Totals(
            long reservations,
            long cancelled,
            long noShow,
            long expired,
            long revenueKrw,
            long changeRequestsPending,
            long changeRequestsCompleted) {
    }
}
