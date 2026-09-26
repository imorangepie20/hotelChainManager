package team.hotelchain.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 지점별 매출·점유율·취소율·노쇼율과 예약 변경 승인 대기·완료 건수를 확인한다.
 * <p>
 * 모든 쿼리가 SELECT이므로 가격·재고·예약 상태를 변경하지 않는다. 집계는 지점 현지
 * 시간대({@code Asia/Seoul}) 기준 날짜로 묶는다.
 */
@Service
public class OperationsReportService {

    static final int MAX_RANGE_DAYS = 92;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public OperationsReportService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public OperationsReportView operations(String token, LocalDate from, LocalDate to, UUID hotelId) {
        access.requireHeadquarters(token);
        LocalDate start = from == null ? LocalDate.now(ZONE).minusDays(6) : from;
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        validateRange(start, end);
        if (hotelId != null && !hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        Map<UUID, HotelOperationsMetrics> metrics = new LinkedHashMap<>();
        for (HotelRow hotel : loadHotels(hotelId)) {
            metrics.put(hotel.id(), new HotelOperationsMetrics(
                    hotel.id().toString(), hotel.name(), hotel.region(),
                    0, 0, 0, 0, 0L, 0, 0, 0.0d));
        }

        applyReservations(metrics, start, end);
        applyChangeRequests(metrics, start, end);
        applyCapacity(metrics, start, end);

        List<HotelOperationsMetrics> rows = new ArrayList<>(metrics.values());
        return new OperationsReportView(start.toString(), end.toString(),
                (int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1,
                rows, totals(rows));
    }

    private List<HotelRow> loadHotels(UUID hotelId) {
        String sql = "select id, name, region from hotel";
        if (hotelId != null) {
            sql += " where id = ?";
        }
        return jdbc.query(sql + " order by name", (rs, row) -> new HotelRow(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("region")),
                hotelId == null ? new Object[0] : new Object[] { hotelId });
    }

    // 예약 건수·상태별 수·매출을 지점 현지 날짜로 묶는다. 매출은 실제 숙박으로
    // 이어진 예약만 인정하므로 취소·노쇼 분은 뺀다.
    private void applyReservations(Map<UUID, HotelOperationsMetrics> metrics, LocalDate from, LocalDate to) {
        if (metrics.isEmpty()) return;
        jdbc.query("""
                select rt.hotel_id as hotel_id,
                       count(*) as reservations,
                       count(*) filter (where r.status = 'CANCELLED') as cancelled,
                       count(*) filter (where r.status = 'NO_SHOW') as no_show,
                       count(*) filter (where r.status = 'EXPIRED') as expired,
                       coalesce(sum(r.total_krw), 0) - coalesce(sum(
                           case when r.status in ('CANCELLED', 'NO_SHOW') then r.total_krw else 0 end), 0) as revenue
                  from reservation r
                  join room_type rt on rt.id = r.room_type_id
                 where (r.created_at at time zone ?)::date >= ?
                   and (r.created_at at time zone ?)::date <= ?
                 group by rt.hotel_id
                """, (rs) -> {
                    UUID hotelId = rs.getObject("hotel_id", UUID.class);
                    HotelOperationsMetrics base = metrics.get(hotelId);
                    if (base == null) return;
                    metrics.put(hotelId, new HotelOperationsMetrics(
                            base.hotelId(), base.hotelName(), base.region(),
                            rs.getLong("reservations"),
                            rs.getLong("cancelled"),
                            rs.getLong("no_show"),
                            rs.getLong("expired"),
                            rs.getLong("revenue"),
                            base.changeRequestsPending(),
                            base.changeRequestsCompleted(),
                            base.occupancyRate()));
                }, ZONE.getId(), from, ZONE.getId(), to);
    }

    // 예약 변경 요청의 진행 중·완료 건수를 더한다.
    private void applyChangeRequests(Map<UUID, HotelOperationsMetrics> metrics, LocalDate from, LocalDate to) {
        if (metrics.isEmpty()) return;
        jdbc.query("""
                select req.hotel_id as hotel_id,
                       count(*) filter (where req.status in (
                           'PENDING_APPROVAL', 'APPROVED', 'AWAITING_PAYMENT', 'REFUND_PENDING',
                           'READY_TO_APPLY', 'APPLYING', 'RECONCILIATION_REQUIRED')) as pending,
                       count(*) filter (where req.status = 'COMPLETED') as completed
                  from reservation_change_request req
                 where (req.created_at at time zone ?)::date >= ?
                   and (req.created_at at time zone ?)::date <= ?
                 group by req.hotel_id
                """, (rs) -> {
                    UUID hotelId = rs.getObject("hotel_id", UUID.class);
                    HotelOperationsMetrics base = metrics.get(hotelId);
                    if (base == null) return;
                    metrics.put(hotelId, new HotelOperationsMetrics(
                            base.hotelId(), base.hotelName(), base.region(),
                            base.reservations(), base.cancelled(), base.noShow(), base.expired(),
                            base.revenueKrw(),
                            rs.getLong("pending"),
                            rs.getLong("completed"),
                            base.occupancyRate()));
                }, ZONE.getId(), from, ZONE.getId(), to);
    }

    // 숙박일별 가용 재고 대비 확정 건수로 점유율을 계산한다.
    private void applyCapacity(Map<UUID, HotelOperationsMetrics> metrics, LocalDate from, LocalDate to) {
        if (metrics.isEmpty()) return;
        jdbc.query("""
                select rt.hotel_id as hotel_id,
                       sum(i.capacity) as capacity,
                       sum(i.confirmed) as confirmed
                  from inventory_day i
                  join room_type rt on rt.id = i.room_type_id
                 where i.stay_date >= ? and i.stay_date <= ?
                 group by rt.hotel_id
                """, (rs) -> {
                    UUID hotelId = rs.getObject("hotel_id", UUID.class);
                    HotelOperationsMetrics base = metrics.get(hotelId);
                    if (base == null) return;
                    long capacity = rs.getLong("capacity");
                    double occupancy = capacity == 0 ? 0.0d
                            : Math.min(1.0d, rs.getLong("confirmed") / (double) capacity);
                    metrics.put(hotelId, new HotelOperationsMetrics(
                            base.hotelId(), base.hotelName(), base.region(),
                            base.reservations(), base.cancelled(), base.noShow(), base.expired(),
                            base.revenueKrw(), base.changeRequestsPending(),
                            base.changeRequestsCompleted(), occupancy));
                }, from, to);
    }

    private OperationsReportView.Totals totals(List<HotelOperationsMetrics> rows) {
        long reservations = 0, cancelled = 0, noShow = 0, expired = 0;
        long revenue = 0, pending = 0, completed = 0;
        for (HotelOperationsMetrics row : rows) {
            reservations += row.reservations();
            cancelled += row.cancelled();
            noShow += row.noShow();
            expired += row.expired();
            revenue += row.revenueKrw();
            pending += row.changeRequestsPending();
            completed += row.changeRequestsCompleted();
        }
        return new OperationsReportView.Totals(reservations, cancelled, noShow, expired, revenue, pending, completed);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (!to.isAfter(from) && !to.isEqual(from)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜와 같거나 이후여야 합니다.");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("조회 기간은 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    // 본사가 지점의 객실 유형별 매출을 확인한다. SELECT만 사용한다.
    // 매출 집계는 applyReservations와 같은 기준을 쓴다.
    public RoomTypeRevenueView roomTypeRevenue(String token, UUID hotelId, LocalDate from, LocalDate to) {
        access.requireHeadquarters(token);
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        LocalDate start = from == null ? LocalDate.now(ZONE).minusDays(6) : from;
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        validateRange(start, end);

        HotelRow hotel = loadHotels(hotelId).get(0);
        List<RoomTypeRevenueView.RoomTypeRevenueRow> rows = loadRoomTypeRows(hotelId, start, end);
        long totalRevenue = rows.stream().mapToLong(RoomTypeRevenueView.RoomTypeRevenueRow::revenueKrw).sum();
        long totalReservations = rows.stream().mapToLong(RoomTypeRevenueView.RoomTypeRevenueRow::reservations).sum();
        long totalCancelled = rows.stream().mapToLong(RoomTypeRevenueView.RoomTypeRevenueRow::cancelled).sum();
        long totalNoShow = rows.stream().mapToLong(RoomTypeRevenueView.RoomTypeRevenueRow::noShow).sum();

        return new RoomTypeRevenueView(start.toString(), end.toString(),
                (int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1,
                hotel.id().toString(), hotel.name(), rows,
                new RoomTypeRevenueView.Totals(totalReservations, totalCancelled, totalNoShow, totalRevenue));
    }

    // 취소·노쇼가 아닌 예약의 금액만 매출로 인정한다.
    private List<RoomTypeRevenueView.RoomTypeRevenueRow> loadRoomTypeRows(
            UUID hotelId, LocalDate from, LocalDate to) {
        List<RoomTypeRevenueView.RoomTypeRevenueRow> rows = jdbc.query("""
                select rt.id as room_type_id,
                       rt.name as room_type_name,
                       count(*) as reservations,
                       count(*) filter (where r.status = 'CANCELLED') as cancelled,
                       count(*) filter (where r.status = 'NO_SHOW') as no_show,
                       coalesce(sum(r.total_krw), 0) - coalesce(sum(
                           case when r.status in ('CANCELLED', 'NO_SHOW') then r.total_krw else 0 end), 0) as revenue
                  from reservation r
                  join room_type rt on rt.id = r.room_type_id
                 where rt.hotel_id = ?
                   and (r.created_at at time zone ?)::date >= ?
                   and (r.created_at at time zone ?)::date <= ?
                 group by rt.id, rt.name
                 order by revenue desc, rt.name
                """, (rs, row) -> new RoomTypeRevenueView.RoomTypeRevenueRow(
                        rs.getObject("room_type_id", UUID.class).toString(),
                        rs.getString("room_type_name"),
                        rs.getLong("reservations"),
                        rs.getLong("cancelled"),
                        rs.getLong("no_show"),
                        rs.getLong("revenue"),
                        0.0d),
                hotelId, ZONE.getId(), from, ZONE.getId(), to);

        // 지점 매출에서 각 유형이 차지하는 비중을 계산한다. 0원이면 0%다.
        long totalRevenue = rows.stream().mapToLong(RoomTypeRevenueView.RoomTypeRevenueRow::revenueKrw).sum();
        if (totalRevenue == 0) return rows;
        return rows.stream().map(row -> new RoomTypeRevenueView.RoomTypeRevenueRow(
                        row.roomTypeId(), row.roomTypeName(), row.reservations(), row.cancelled(),
                        row.noShow(), row.revenueKrw(), row.revenueKrw() / (double) totalRevenue))
                .toList();
    }

    private record HotelRow(UUID id, String name, String region) {
    }
}
