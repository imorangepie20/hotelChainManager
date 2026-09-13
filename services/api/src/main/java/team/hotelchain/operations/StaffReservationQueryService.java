package team.hotelchain.operations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import team.hotelchain.staff.StaffAccessService;

@Service
public class StaffReservationQueryService {
    private static final int MAX_RESULTS = 200;
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;

    public StaffReservationQueryService(JdbcTemplate jdbc, StaffAccessService staffAccess) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
    }

    public StaffReservationSearchView search(String token, UUID hotelId, LocalDate date, String query, String status) {
        staffAccess.requireHotel(token, hotelId);

        StringBuilder sql = new StringBuilder("""
                select r.id, r.guest_name, r.guest_email, rt.name as room_type_name,
                       rp.name as rate_plan_name, r.check_in, r.check_out, r.adults, r.children,
                       r.rooms, r.status, r.total_krw, r.currency,
                       array_agg(pr.room_number order by pr.room_number)
                           filter (where pr.room_number is not null) as assigned_room_numbers
                from reservation r
                join room_type rt on rt.id = r.room_type_id
                join rate_plan rp on rp.id = r.rate_plan_id
                left join reservation_room_assignment rra on rra.reservation_id = r.id
                left join physical_room pr on pr.id = rra.physical_room_id
                where rt.hotel_id = ? and r.check_in <= ? and r.check_out >= ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(hotelId, date, date));

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (!normalizedQuery.isEmpty()) {
            sql.append(" and (position(? in lower(r.guest_name)) > 0 or position(? in lower(r.guest_email)) > 0 or position(? in lower(cast(r.id as varchar))) > 0)\n");
            parameters.add(normalizedQuery);
            parameters.add(normalizedQuery);
            parameters.add(normalizedQuery);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and r.status = ?\n");
            parameters.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append("""
                group by r.id, r.guest_name, r.guest_email, rt.name, rp.name, r.check_in, r.check_out,
                         r.adults, r.children, r.rooms, r.status, r.total_krw, r.currency, r.created_at
                order by r.check_in, r.created_at, r.id
                limit 201
                """);

        List<StaffReservationSearchView.ReservationSummary> results = jdbc.query(
                sql.toString(), (rs, rowNum) -> reservationSummary(rs), parameters.toArray());
        boolean truncated = results.size() > MAX_RESULTS;
        return new StaffReservationSearchView(
                hotelId,
                date,
                truncated,
                truncated ? List.copyOf(results.subList(0, MAX_RESULTS)) : results
        );
    }

    private StaffReservationSearchView.ReservationSummary reservationSummary(ResultSet rs) throws SQLException {
        java.sql.Array assignedRoomNumbers = rs.getArray("assigned_room_numbers");
        List<String> rooms = assignedRoomNumbers == null
                ? List.of()
                : java.util.Arrays.stream((Object[]) assignedRoomNumbers.getArray()).map(String::valueOf).toList();
        return new StaffReservationSearchView.ReservationSummary(
                rs.getObject("id", UUID.class),
                rs.getString("guest_name"),
                rs.getString("guest_email"),
                rs.getString("room_type_name"),
                rs.getString("rate_plan_name"),
                rs.getDate("check_in").toLocalDate(),
                rs.getDate("check_out").toLocalDate(),
                rs.getInt("adults"),
                rs.getInt("children"),
                rs.getInt("rooms"),
                rs.getString("status"),
                rs.getLong("total_krw"),
                rs.getString("currency"),
                rooms
        );
    }
}
