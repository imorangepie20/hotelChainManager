package team.hotelchain.reservationchange;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import team.hotelchain.inventory.NightlyPrice;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffPrincipal;

@Component
public class ReservationChangeViews {
    private final JdbcTemplate jdbc;
    private final ReservationChangePolicy policy;

    public ReservationChangeViews(JdbcTemplate jdbc, ReservationChangePolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    public ReservationChangeRequestView get(StaffPrincipal staff, UUID requestId) {
        RequestRow request = jdbc.query("""
                select r.id, r.reservation_id, r.hotel_id, h.name as hotel_name,
                       booking.guest_name, r.status, r.settlement_direction, r.version,
                       r.previous_check_in, r.previous_check_out, r.previous_room_type_id,
                       previous_room_type.name as previous_room_type_name, r.previous_rate_plan_id,
                       previous_rate_plan.name as previous_rate_plan_name,
                       r.target_check_in, r.target_check_out, r.target_room_type_id,
                       target_room_type.name as target_room_type_name, r.target_rate_plan_id,
                       target_rate_plan.name as target_rate_plan_name,
                       r.rooms, r.adults, r.children, r.approval_expires_at, r.current_quote_id
                from reservation_change_request r
                join reservation booking on booking.id = r.reservation_id
                join hotel h on h.id = r.hotel_id
                join room_type previous_room_type on previous_room_type.id = r.previous_room_type_id
                join rate_plan previous_rate_plan on previous_rate_plan.id = r.previous_rate_plan_id
                join room_type target_room_type on target_room_type.id = r.target_room_type_id
                join rate_plan target_rate_plan on target_rate_plan.id = r.target_rate_plan_id
                where r.id = ?
                """, rs -> rs.next() ? mapRequest(rs) : null, requestId);
        if (request == null) {
            throw new ReservationNotFoundException();
        }
        requireAccess(staff, request.hotelId());
        return toView(staff, request);
    }

    public List<ReservationChangeRequestView> list(StaffPrincipal staff, String status, UUID hotelId) {
        StringBuilder sql = new StringBuilder("""
                select id from reservation_change_request where 1 = 1
                """);
        List<Object> parameters = new java.util.ArrayList<>();
        if (!"HQ_ADMIN".equals(staff.role())) {
            sql.append(" and hotel_id = ?");
            parameters.add(staff.hotelId());
        } else if (hotelId != null) {
            sql.append(" and hotel_id = ?");
            parameters.add(hotelId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            parameters.add(status);
        }
        sql.append(" order by created_at desc, id");
        return jdbc.query(sql.toString(), (rs, rowNumber) -> rs.getObject("id", UUID.class), parameters.toArray())
                .stream().map(id -> get(staff, id)).toList();
    }

    private ReservationChangeRequestView toView(StaffPrincipal staff, RequestRow request) {
        ReservationChangeRequestView.QuoteView quote = loadQuote(request.currentQuoteId());
        ReservationChangeRequestView.ApprovalView approval = loadLatestApproval(request.id());
        List<ReservationChangeRequestView.EventView> events = loadEvents(request.id());
        return new ReservationChangeRequestView(
                request.id(), request.reservationId(), request.hotelId(), request.hotelName(),
                request.guestName(), request.status(),
                request.settlementDirection(), request.version(), request.previousCheckIn(),
                request.previousCheckOut(), request.previousRoomTypeId(), request.previousRoomTypeName(),
                request.previousRatePlanId(), request.previousRatePlanName(),
                request.targetCheckIn(), request.targetCheckOut(), request.targetRoomTypeId(),
                request.targetRoomTypeName(), request.targetRatePlanId(), request.targetRatePlanName(),
                request.rooms(), request.adults(), request.children(),
                request.approvalExpiresAt(), quote, approval, events, actions(staff, request));
    }

    private ReservationChangeRequestView.QuoteView loadQuote(UUID quoteId) {
        if (quoteId == null) {
            return null;
        }
        return jdbc.query("""
                select id, revision, previous_total_krw, total_krw, difference_krw, currency, created_at
                from reservation_change_quote where id = ?
                """, rs -> {
            if (!rs.next()) return null;
            List<NightlyPrice> nights = jdbc.query("""
                    select stay_date, amount_krw from reservation_change_quote_night
                    where quote_id = ? order by stay_date
                    """, (nightRows, rowNumber) -> new NightlyPrice(
                            nightRows.getDate("stay_date").toLocalDate(), nightRows.getInt("amount_krw")), quoteId);
            return new ReservationChangeRequestView.QuoteView(
                    rs.getObject("id", UUID.class), rs.getLong("revision"),
                    rs.getLong("previous_total_krw"), rs.getLong("total_krw"),
                    rs.getLong("difference_krw"), rs.getString("currency").trim(),
                    nights, rs.getTimestamp("created_at").toInstant());
        }, quoteId);
    }

    private ReservationChangeRequestView.ApprovalView loadLatestApproval(UUID requestId) {
        return jdbc.query("""
                select id, decision_type, decided_by, decided_role, max_abs_difference_krw, reason, created_at
                from reservation_change_approval
                where request_id = ? order by created_at desc, id desc limit 1
                """, rs -> rs.next() ? new ReservationChangeRequestView.ApprovalView(
                        rs.getObject("id", UUID.class), rs.getString("decision_type"),
                        rs.getObject("decided_by", UUID.class), rs.getString("decided_role"),
                        rs.getLong("max_abs_difference_krw"), rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()) : null, requestId);
    }

    private List<ReservationChangeRequestView.EventView> loadEvents(UUID requestId) {
        return jdbc.query("""
                select id, event_type, from_status, to_status, actor_staff_id,
                       payload ->> 'reason' as reason, created_at
                from reservation_change_event
                where request_id = ? order by created_at, id
                """, (rs, rowNumber) -> new ReservationChangeRequestView.EventView(
                        rs.getObject("id", UUID.class), rs.getString("event_type"),
                        rs.getString("from_status"), rs.getString("to_status"),
                        rs.getObject("actor_staff_id", UUID.class), rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()), requestId);
    }

    private Set<String> actions(StaffPrincipal staff, RequestRow request) {
        Set<String> actions = new LinkedHashSet<>();
        if ("PENDING_APPROVAL".equals(request.status())) {
            actions.add("REPRICE");
            actions.add("CANCEL");
            if ("HQ_ADMIN".equals(staff.role())) {
                actions.add("APPROVE");
                actions.add("REJECT");
            }
        } else if ("APPROVED".equals(request.status())) {
            actions.add("REPRICE");
            actions.add("CANCEL");
            if (policy.settlementEnabled()) {
                if ("CHARGE".equals(request.settlementDirection())) actions.add("PAYMENT_LINK");
                if ("REFUND".equals(request.settlementDirection())) actions.add("REFUND");
            }
        }
        return Set.copyOf(actions);
    }

    private void requireAccess(StaffPrincipal staff, UUID hotelId) {
        if (!"HQ_ADMIN".equals(staff.role()) && !hotelId.equals(staff.hotelId())) {
            throw new StaffAccessDeniedException();
        }
    }

    private RequestRow mapRequest(ResultSet rs) throws SQLException {
        return new RequestRow(
                rs.getObject("id", UUID.class), rs.getObject("reservation_id", UUID.class),
                rs.getObject("hotel_id", UUID.class), rs.getString("hotel_name"),
                rs.getString("guest_name"), rs.getString("status"),
                rs.getString("settlement_direction"), rs.getLong("version"),
                rs.getDate("previous_check_in").toLocalDate(), rs.getDate("previous_check_out").toLocalDate(),
                rs.getObject("previous_room_type_id", UUID.class),
                rs.getString("previous_room_type_name"),
                rs.getObject("previous_rate_plan_id", UUID.class),
                rs.getString("previous_rate_plan_name"),
                rs.getDate("target_check_in").toLocalDate(), rs.getDate("target_check_out").toLocalDate(),
                rs.getObject("target_room_type_id", UUID.class), rs.getString("target_room_type_name"),
                rs.getObject("target_rate_plan_id", UUID.class), rs.getString("target_rate_plan_name"),
                rs.getInt("rooms"), rs.getInt("adults"), rs.getInt("children"),
                rs.getTimestamp("approval_expires_at").toInstant(),
                rs.getObject("current_quote_id", UUID.class));
    }

    private record RequestRow(
            UUID id,
            UUID reservationId,
            UUID hotelId,
            String hotelName,
            String guestName,
            String status,
            String settlementDirection,
            long version,
            java.time.LocalDate previousCheckIn,
            java.time.LocalDate previousCheckOut,
            UUID previousRoomTypeId,
            String previousRoomTypeName,
            UUID previousRatePlanId,
            String previousRatePlanName,
            java.time.LocalDate targetCheckIn,
            java.time.LocalDate targetCheckOut,
            UUID targetRoomTypeId,
            String targetRoomTypeName,
            UUID targetRatePlanId,
            String targetRatePlanName,
            int rooms,
            int adults,
            int children,
            Instant approvalExpiresAt,
            UUID currentQuoteId) {
    }
}
