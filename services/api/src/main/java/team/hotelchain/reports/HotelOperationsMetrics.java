package team.hotelchain.reports;

/**
 * 지점별 운영 통계. 모든 값이 SELECT 집계 결과다.
 */
public record HotelOperationsMetrics(
        String hotelId,
        String hotelName,
        String region,
        long reservations,
        long cancelled,
        long noShow,
        long expired,
        long revenueKrw,
        long changeRequestsPending,
        long changeRequestsCompleted,
        double occupancyRate) {
}
