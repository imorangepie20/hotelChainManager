package team.hotelchain.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 객실 유형별 일자 재고를 읽기 전용으로 확인한다.
 * SELECT만 사용하고 재고를 변경하지 않는다.
 */
@Service
public class InventoryQueryService {

    private static final int MAX_RANGE_DAYS = 92;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public InventoryQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public InventoryView list(String token, UUID hotelId, LocalDate from, LocalDate to) {
        access.requireHotel(token, hotelId);
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(13) : to;
        validateRange(start, end);

        List<InventoryRow> rows = jdbc.query("""
                SELECT rt.id AS room_type_id, rt.name AS room_type_name, rt.max_occupancy,
                       i.stay_date, i.capacity, i.held, i.confirmed,
                       (i.capacity - i.held - i.confirmed) AS remaining,
                       coalesce(s.status, 'OPEN') AS sales_status
                  FROM room_type rt
                  JOIN inventory_day i ON i.room_type_id = rt.id
                  LEFT JOIN room_type_sales_status s
                    ON s.room_type_id = rt.id AND s.stay_date = i.stay_date
                 WHERE rt.hotel_id = ? AND i.stay_date >= ? AND i.stay_date <= ?
                 ORDER BY rt.id, i.stay_date
                 """, this::mapRow, hotelId, start, end);

        return new InventoryView(hotelId, group(rows));
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hotel WHERE id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (!to.isAfter(from) && !to.isEqual(from)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜와 같거나 이후여야 합니다.");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("조회 기간은 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
    }

    private List<InventoryView.RoomTypeInventory> group(List<InventoryRow> rows) {
        Map<UUID, List<InventoryRow>> byRoomType = new LinkedHashMap<>();
        for (InventoryRow row : rows) {
            byRoomType.computeIfAbsent(row.roomTypeId(), ignored -> new java.util.ArrayList<>()).add(row);
        }
        return byRoomType.entrySet().stream()
                .map(entry -> new InventoryView.RoomTypeInventory(
                        entry.getKey(),
                        entry.getValue().getFirst().roomTypeName(),
                        entry.getValue().getFirst().maxOccupancy(),
                        entry.getValue().stream()
                                .map(row -> new InventoryView.InventoryDay(
                                        row.stayDate(), row.capacity(), row.held(),
                                        row.confirmed(), row.remaining(), row.salesStatus()))
                                .toList()))
                .toList();
    }

    private InventoryRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new InventoryRow(
                rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name"),
                rs.getInt("max_occupancy"),
                rs.getDate("stay_date").toLocalDate(),
                rs.getInt("capacity"),
                rs.getInt("held"),
                rs.getInt("confirmed"),
                rs.getInt("remaining"),
                rs.getString("sales_status"));
    }

    private record InventoryRow(
            UUID roomTypeId, String roomTypeName, int maxOccupancy, LocalDate stayDate,
            int capacity, int held, int confirmed, int remaining, String salesStatus) {
    }
}
