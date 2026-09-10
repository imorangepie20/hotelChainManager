package team.hotelchain.operations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import team.hotelchain.staff.StaffAccessService;

@Service
public class DailyOperationsService {
    private final JdbcTemplate jdbcTemplate;
    private final StaffAccessService staffAccess;

    public DailyOperationsService(JdbcTemplate jdbcTemplate, StaffAccessService staffAccess) {
        this.jdbcTemplate = jdbcTemplate;
        this.staffAccess = staffAccess;
    }

    public DailyOperationsView get(String token, UUID hotelId, LocalDate date) {
        staffAccess.requireHotel(token, hotelId);
        return new DailyOperationsView(
                hotelId,
                date,
                reservationsForDate(hotelId, date, true),
                reservationsForDate(hotelId, date, false),
                roomsNeedingCleaning(hotelId)
        );
    }

    private List<DailyOperationsView.ReservationItem> reservationsForDate(UUID hotelId, LocalDate date, boolean arrivals) {
        String stayDateColumn = arrivals ? "r.check_in" : "r.check_out";
        String status = arrivals ? "CONFIRMED" : "CHECKED_IN";
        String sql = """
                select r.id, r.guest_name, rt.name as room_type_name, r.status,
                       coalesce(string_agg(pr.room_number, ',' order by pr.room_number), '') as assigned_room_numbers
                from reservation r
                join room_type rt on rt.id = r.room_type_id
                left join reservation_room_assignment rra on rra.reservation_id = r.id
                left join physical_room pr on pr.id = rra.physical_room_id
                where rt.hotel_id = ? and %s = ? and r.status = ?
                group by r.id, r.guest_name, rt.name, r.status, r.created_at
                order by r.created_at
                """.formatted(stayDateColumn);
        return jdbcTemplate.query(sql, (rs, rowNum) -> reservationItem(rs), hotelId, date, status);
    }

    private List<DailyOperationsView.RoomItem> roomsNeedingCleaning(UUID hotelId) {
        return jdbcTemplate.query("""
                        select pr.id, pr.room_number, rt.name as room_type_name, pr.housekeeping_status
                        from physical_room pr
                        join room_type rt on rt.id = pr.room_type_id
                        where pr.hotel_id = ? and pr.housekeeping_status = 'NEEDS_CLEANING'
                        order by pr.room_number
                        """,
                (rs, rowNum) -> new DailyOperationsView.RoomItem(
                        rs.getObject("id", UUID.class),
                        rs.getString("room_number"),
                        rs.getString("room_type_name"),
                        rs.getString("housekeeping_status")
                ),
                hotelId);
    }

    private DailyOperationsView.ReservationItem reservationItem(ResultSet rs) throws SQLException {
        String assignedRoomNumbers = rs.getString("assigned_room_numbers");
        List<String> rooms = assignedRoomNumbers == null || assignedRoomNumbers.isBlank()
                ? List.of()
                : Arrays.asList(assignedRoomNumbers.split(","));
        return new DailyOperationsView.ReservationItem(
                rs.getObject("id", UUID.class),
                rs.getString("guest_name"),
                rs.getString("room_type_name"),
                rs.getString("status"),
                rooms
        );
    }
}
