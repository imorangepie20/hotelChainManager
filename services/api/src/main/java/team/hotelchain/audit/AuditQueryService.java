package team.hotelchain.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 V29~V37 감사 표와 예약 변경 이력을 통합해 읽는다.
 * <p>
 * 모든 쿼리가 SELECT이므로 상태를 변경하지 않는다. 원본 JSON이나 개인정보 전문은
 * 노출하지 않고 본사가 파악할 요약만 만든다.
 */
@Service
public class AuditQueryService {

    static final int MAX_LIMIT = 200;
    static final int MAX_OFFSET = 10_000;

    private static final String COLUMNS = """
            event_type, created_at, staff_email, staff_display_name, staff_role,
            hotel_id, hotel_name, reservation_id, guest_name, room_number, summary
            """;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;

    public AuditQueryService(JdbcTemplate jdbc, StaffAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public AuditEventsView list(String token, AuditQueryFilters filters) {
        access.requireHeadquarters(token);
        validate(filters);

        String where = whereClause(filters);
        List<Object> params = new ArrayList<>();
        addParams(params, filters);

        Integer total = jdbc.queryForObject(
                "select count(*) from (" + unionAll() + ") a" + where, Integer.class, params.toArray());
        int totalCount = total == null ? 0 : total;

        List<Object> rows = new ArrayList<>(params);
        rows.add(filters.limit());
        rows.add(filters.offset());
        List<AuditEventView> events = jdbc.query(
                "select " + COLUMNS + " from (" + unionAll() + ") a" + where
                        + " order by created_at desc, event_type, reservation_id nulls last"
                        + " limit ? offset ?",
                this::mapEvent, rows.toArray());

        return new AuditEventsView(events, totalCount, filters.limit(), filters.offset());
    }

    private String unionAll() {
        return String.join("\nunion all\n", List.of(
                guestChanges(), partyChanges(), roomReassignments(), stayChanges(),
                cancellations(), roomOperationalTransitions(), checkedInRoomMoves(),
                changeRequestEvents()));
    }

    // 예약자 정정. 이전 이름·이메일 → 새 이름·이메일
    private String guestChanges() {
        return """
                select 'GUEST_UPDATE' as event_type, c.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       rt.hotel_id::text as hotel_id, h.name as hotel_name,
                       c.reservation_id::text as reservation_id, r.guest_name as guest_name,
                       cast(null as text) as room_number,
                       ('이전 예약자 ' || c.previous_guest_name || ' / ' || c.previous_guest_email
                        || ' → ' || c.guest_name || ' / ' || c.guest_email) as summary
                  from reservation_guest_change c
                  join staff_member s on s.id = c.staff_id
                  join reservation r on r.id = c.reservation_id
                  join room_type rt on rt.id = r.room_type_id
                  join hotel h on h.id = rt.hotel_id
                """;
    }

    // 투숙 인원 변경. 이전 성인·아동 → 새 성인·아동
    private String partyChanges() {
        return """
                select 'PARTY_UPDATE' as event_type, c.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       rt.hotel_id::text as hotel_id, h.name as hotel_name,
                       c.reservation_id::text as reservation_id, r.guest_name as guest_name,
                       cast(null as text) as room_number,
                       ('이전 인원 성인 ' || c.previous_adults || ' / 아동 ' || c.previous_children
                        || ' → 성인 ' || c.adults || ' / 아동 ' || c.children) as summary
                  from reservation_party_change c
                  join staff_member s on s.id = c.staff_id
                  join reservation r on r.id = c.reservation_id
                  join room_type rt on rt.id = r.room_type_id
                  join hotel h on h.id = rt.hotel_id
                """;
    }

    // 배정 객실 변경. 이전 객실 번호 → 새 객실 번호
    private String roomReassignments() {
        return """
                select 'ROOM_REASSIGNMENT' as event_type, c.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       rt.hotel_id::text as hotel_id, h.name as hotel_name,
                       c.reservation_id::text as reservation_id, r.guest_name as guest_name,
                       c.room_number as room_number,
                       ('이전 객실 ' || c.previous_room_number || ' → ' || c.room_number) as summary
                  from reservation_room_assignment_change c
                  join staff_member s on s.id = c.staff_id
                  join reservation r on r.id = c.reservation_id
                  join room_type rt on rt.id = r.room_type_id
                  join hotel h on h.id = rt.hotel_id
                """;
    }

    // 숙박 조건 변경. 이전 날짜·객실 유형·총액 → 새 날짜·객실 유형·총액
    private String stayChanges() {
        return """
                select 'STAY_CHANGE' as event_type, c.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       rt.hotel_id::text as hotel_id, h.name as hotel_name,
                       c.reservation_id::text as reservation_id, r.guest_name as guest_name,
                       cast(null as text) as room_number,
                       ('이전 숙박 ' || c.previous_check_in || ' ~ ' || c.previous_check_out
                        || ' / 총액 ' || c.previous_total_krw || '원'
                        || ' → ' || c.check_in || ' ~ ' || c.check_out
                        || ' / 총액 ' || c.total_krw || '원'
                        || case when c.difference_krw >= 0 then ' (추가 결제 ' || c.difference_krw || '원)'
                                else ' (환불 ' || abs(c.difference_krw) || '원)' end) as summary
                  from reservation_stay_change c
                  join staff_member s on s.id = c.staff_id
                  join reservation r on r.id = c.reservation_id
                  join room_type rt on rt.id = r.room_type_id
                  join hotel h on h.id = rt.hotel_id
                """;
    }

    // 직원 예약 취소. 환불액과 취소 후 예약 상태
    private String cancellations() {
        return """
                select 'CANCELLATION' as event_type, c.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       rt.hotel_id::text as hotel_id, h.name as hotel_name,
                       c.reservation_id::text as reservation_id, r.guest_name as guest_name,
                       cast(null as text) as room_number,
                       ('취소 처리 환불액 ' || c.refund_amount_krw || '원 / 환불 상태 ' || c.refund_status) as summary
                  from cancellation_attempt c
                  join staff_member s on s.id = c.staff_id
                  join reservation r on r.id = c.reservation_id
                  join room_type rt on rt.id = r.room_type_id
                  join hotel h on h.id = rt.hotel_id
                 where c.actor_type = 'STAFF'
                """;
    }

    // 실제 객실 운영 상태 전환. 이전 상태·사유 → 새 상태·사유
    private String roomOperationalTransitions() {
        return """
                select 'ROOM_OPERATIONAL_TRANSITION' as event_type, e.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       p.hotel_id::text as hotel_id, h.name as hotel_name,
                       cast(null as text) as reservation_id, cast(null as text) as guest_name,
                       p.room_number as room_number,
                       ('객실 ' || p.room_number || ' 상태 ' || e.previous_status || ' → ' || e.status
                        || ' / 사유 ' || e.reason) as summary
                  from physical_room_operational_event e
                  join staff_member s on s.id = e.staff_id
                  join physical_room p on p.id = e.physical_room_id
                  join hotel h on h.id = p.hotel_id
                """;
    }

    // 투숙 중 객실 이동. 이전 객실 → 새 객실과 이동 사유
    private String checkedInRoomMoves() {
        return """
                select 'CHECKED_IN_ROOM_MOVE' as event_type, c.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       rt.hotel_id::text as hotel_id, h.name as hotel_name,
                       c.reservation_id::text as reservation_id, r.guest_name as guest_name,
                       c.room_number as room_number,
                       ('투숙 중 이동 이전 객실 ' || c.previous_room_number || ' → ' || c.room_number
                        || ' / 사유 ' || c.reason) as summary
                  from checked_in_room_move c
                  join staff_member s on s.id = c.staff_id
                  join reservation r on r.id = c.reservation_id
                  join room_type rt on rt.id = r.room_type_id
                  join hotel h on h.id = rt.hotel_id
                """;
    }

    // 예약 변경 요청의 상태 전환. actor_staff_id가 없는 자동 전환은 건너뛴다.
    private String changeRequestEvents() {
        return """
                select 'CHANGE_REQUEST_EVENT' as event_type, e.created_at,
                       s.email as staff_email, s.display_name as staff_display_name, s.role as staff_role,
                       req.hotel_id::text as hotel_id, h.name as hotel_name,
                       req.reservation_id::text as reservation_id, r.guest_name as guest_name,
                       cast(null as text) as room_number,
                       ('예약 변경 요청 ' || e.event_type
                        || ' / ' || coalesce(e.from_status, '시작 없음') || ' → ' || coalesce(e.to_status, '상태 없음')) as summary
                  from reservation_change_event e
                  join staff_member s on s.id = e.actor_staff_id
                  join reservation_change_request req on req.id = e.request_id
                  join reservation r on r.id = req.reservation_id
                  join hotel h on h.id = req.hotel_id
                 where e.actor_staff_id is not null
                """;
    }

    private String whereClause(AuditQueryFilters filters) {
        List<String> clauses = new ArrayList<>();
        if (filters.hotelId() != null) clauses.add("a.hotel_id = ?");
        if (filters.from() != null) clauses.add("a.created_at >= ?");
        if (filters.to() != null) clauses.add("a.created_at < ?");
        if (filters.reservationId() != null) clauses.add("a.reservation_id = ?");
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    private void addParams(List<Object> params, AuditQueryFilters filters) {
        if (filters.hotelId() != null) params.add(filters.hotelId().toString());
        if (filters.from() != null) params.add(java.sql.Timestamp.from(atStartOfDay(filters.from())));
        if (filters.to() != null) params.add(java.sql.Timestamp.from(atStartOfDay(filters.to().plusDays(1))));
        if (filters.reservationId() != null) params.add(filters.reservationId().toString());
    }

    private static java.time.Instant atStartOfDay(LocalDate date) {
        return date.atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
    }

    private AuditEventView mapEvent(ResultSet rs, int rowNumber) throws SQLException {
        return new AuditEventView(
                rs.getString("event_type"),
                rs.getString("created_at"),
                rs.getString("staff_email"),
                rs.getString("staff_display_name"),
                rs.getString("staff_role"),
                rs.getString("hotel_id"),
                rs.getString("hotel_name"),
                rs.getString("reservation_id"),
                rs.getString("guest_name"),
                rs.getString("room_number"),
                rs.getString("summary"));
    }

    private void validate(AuditQueryFilters filters) {
        if (filters.limit() < 1 || filters.limit() > MAX_LIMIT) {
            throw new IllegalArgumentException("조회 건수는 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
        if (filters.offset() < 0 || filters.offset() > MAX_OFFSET) {
            throw new IllegalArgumentException("조회 시작 위치는 0 이상 " + MAX_OFFSET + " 이하여야 합니다.");
        }
        if (filters.from() != null && filters.to() != null && !filters.to().isAfter(filters.from())) {
            throw new IllegalArgumentException("기간 끝날짜는 시작 날짜보다 이후여야 합니다.");
        }
        if (filters.hotelId() != null && !hotelExists(filters.hotelId())) {
            throw new team.hotelchain.hotel.HotelNotFoundException(filters.hotelId());
        }
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject("select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }
}
