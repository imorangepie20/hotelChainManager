package team.hotelchain.guestrequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사·지점 직원이 고객 요청을 읽는다.
 * <p>
 * 모든 쿼리가 SELECT이므로 요청 상태를 변경하지 않는다. 본사는 전 지점을 읽고
 * 지점 직원은 자기 지점만 읽는다.
 */
@Service
public class GuestRequestQueryService {

    static final int MAX_LIMIT = 100;
    static final int MAX_OFFSET = 10_000;
    private static final int DEFAULT_LIMIT = 50;

    private static final String SUMMARY_COLUMNS = """
            id, hotel_id, hotel_name, reservation_id, request_type, subject, guest_name,
            status, priority, assigned_display_name, created_at, updated_at
            """;

    private static final String REQUEST_COLUMNS = """
            id, hotel_id, hotel_name, reservation_id, request_type, subject, body,
            guest_name, guest_email, guest_phone, status, priority,
            assigned_to, assigned_display_name, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public GuestRequestQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public GuestRequestListView list(String token, String status, UUID hotelId, Integer limit, Integer offset) {
        StaffPrincipal staff = access.current(token);
        int pageSize = sanitizeLimit(limit);
        int startPosition = sanitizeOffset(offset);
        validateStatus(status);

        // 본사는 지점 필터가 없으면 전 지점을 읽는다. 지점 직원은 무조건 자기 지점만.
        UUID effectiveHotelId;
        if ("HQ_ADMIN".equals(staff.role())) {
            if (hotelId != null && !hotelExists(hotelId)) {
                throw new HotelNotFoundException(hotelId);
            }
            effectiveHotelId = hotelId;
        } else {
            // 지점 직원은 자기 지점만 읽을 수 있다.
            if (hotelId != null && !hotelId.equals(staff.hotelId())) {
                throw new StaffAccessDeniedException();
            }
            effectiveHotelId = staff.hotelId();
            if (effectiveHotelId == null) {
                throw new StaffAccessDeniedException();
            }
            if (!hotelExists(effectiveHotelId)) {
                throw new HotelNotFoundException(effectiveHotelId);
            }
        }

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (effectiveHotelId != null) {
            where.append(" where hotel_id = ?");
            params.add(effectiveHotelId);
        }
        if (status != null && !status.isBlank()) {
            where.append(where.isEmpty() ? " where status = ?" : " and status = ?");
            params.add(status.trim());
        }

        Integer total = jdbc.queryForObject(
                "select count(*) from guest_request_detail" + where, Integer.class, params.toArray());
        int totalCount = total == null ? 0 : total;

        List<Object> rows = new ArrayList<>(params);
        rows.add(pageSize);
        rows.add(startPosition);
        List<GuestRequestListView.GuestRequestSummary> summaries = jdbc.query(
                "select " + SUMMARY_COLUMNS + " from guest_request_detail" + where
                        + " order by created_at desc, id"
                        + " limit ? offset ?",
                (rs, rowNumber) -> mapSummary(rs),
                rows.toArray());

        return new GuestRequestListView(summaries, totalCount,
                status == null ? "" : status.trim(), effectiveHotelId, pageSize, startPosition);
    }

    @Transactional(readOnly = true)
    public GuestRequestView get(String token, UUID requestId) {
        StaffPrincipal staff = access.current(token);
        RequestRow request = loadRequest(requestId);
        requireAccess(staff, request.hotelId());
        return toView(request);
    }

    private GuestRequestListView.GuestRequestSummary mapSummary(ResultSet rs) throws SQLException {
        return new GuestRequestListView.GuestRequestSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("hotel_id", UUID.class),
                rs.getString("hotel_name"),
                rs.getObject("reservation_id", UUID.class),
                rs.getString("request_type"),
                rs.getString("subject"),
                rs.getString("guest_name"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getString("assigned_display_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private RequestRow loadRequest(UUID requestId) {
        RequestRow request = jdbc.query(
                "select " + REQUEST_COLUMNS + " from guest_request_detail where id = ?",
                rs -> rs.next() ? mapRequest(rs) : null,
                requestId);
        if (request == null) {
            throw new GuestRequestNotFoundException(requestId);
        }
        return request;
    }

    private RequestRow mapRequest(ResultSet rs) throws SQLException {
        return new RequestRow(
                rs.getObject("id", UUID.class),
                rs.getObject("hotel_id", UUID.class),
                rs.getString("hotel_name"),
                rs.getObject("reservation_id", UUID.class),
                rs.getString("request_type"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getString("guest_name"),
                rs.getString("guest_email"),
                rs.getString("guest_phone"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getObject("assigned_to", UUID.class),
                rs.getString("assigned_display_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private GuestRequestView toView(RequestRow request) {
        List<GuestRequestView.EventView> events = jdbc.query("""
                select e.id, e.event_type, e.from_status, e.to_status,
                       coalesce(s.display_name, '시스템') as actor_display_name, e.note, e.created_at
                  from guest_request_event e
                  left join staff_member s on s.id = e.actor_staff_id
                 where e.request_id = ?
                 order by e.created_at, e.id
                """, (rs, rowNumber) -> new GuestRequestView.EventView(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("from_status"),
                        rs.getString("to_status"),
                        rs.getString("actor_display_name"),
                        rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant()),
                request.id());
        return new GuestRequestView(
                request.id(), request.hotelId(), request.hotelName(), request.reservationId(),
                request.requestType(), request.subject(), request.body(),
                request.guestName(), request.guestEmail(), request.guestPhone(),
                request.status(), request.priority(), request.assignedTo(),
                request.assignedDisplayName(), request.createdAt(), request.updatedAt(), events);
    }

    private void requireAccess(StaffPrincipal staff, UUID hotelId) {
        if (!"HQ_ADMIN".equals(staff.role()) && !hotelId.equals(staff.hotelId())) {
            throw new StaffAccessDeniedException();
        }
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("조회 건수는 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
        return limit;
    }

    private int sanitizeOffset(Integer offset) {
        if (offset == null) return 0;
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("조회 시작 위치는 0 이상 " + MAX_OFFSET + " 이하여야 합니다.");
        }
        return offset;
    }

    private void validateStatus(String status) {
        if (status == null || status.isBlank()) return;
        if (!java.util.Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(status.trim())) {
            throw new IllegalArgumentException("알 수 없는 처리 상태입니다.");
        }
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private record RequestRow(
            UUID id,
            UUID hotelId,
            String hotelName,
            UUID reservationId,
            String requestType,
            String subject,
            String body,
            String guestName,
            String guestEmail,
            String guestPhone,
            String status,
            String priority,
            UUID assignedTo,
            String assignedDisplayName,
            Instant createdAt,
            Instant updatedAt) {
    }
}
