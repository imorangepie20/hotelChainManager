package team.hotelchain.operations;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class RoomOperationsService {
    private static final Set<String> STATUSES = Set.of("AVAILABLE", "INSPECTION_REQUIRED", "OUT_OF_SERVICE");

    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;
    private final Clock clock;

    public RoomOperationsService(JdbcTemplate jdbc, StaffAccessService staffAccess,
            ReservationAccess reservationAccess, Clock clock) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RoomOperationsView list(String token, UUID hotelId) {
        staffAccess.requireHotel(token, hotelId);
        String timezone = jdbc.query("select timezone from hotel where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, hotelId);
        if (timezone == null) {
            throw new IllegalArgumentException("호텔을 찾을 수 없습니다.");
        }
        ZoneId zoneId = ZoneId.of(timezone);
        List<RoomOperationsView.RoomItem> rooms = jdbc.query("""
                select pr.id, pr.room_number, rt.name as room_type_name, pr.housekeeping_status,
                       pr.operational_status, pr.operational_reason, pr.expected_recovery_at,
                       pr.operational_version
                  from physical_room pr
                  join room_type rt on rt.id = pr.room_type_id
                 where pr.hotel_id = ?
                 order by pr.room_number, pr.id
                """, (rs, rowNum) -> new RoomOperationsView.RoomItem(
                        rs.getObject("id", UUID.class),
                        rs.getString("room_number"),
                        rs.getString("room_type_name"),
                        rs.getString("housekeeping_status"),
                        rs.getString("operational_status"),
                        rs.getString("operational_reason"),
                        instant(rs, "expected_recovery_at"),
                        rs.getLong("operational_version"),
                        impactedAssignments(rs.getObject("id", UUID.class), zoneId),
                        recentEvents(rs.getObject("id", UUID.class))),
                hotelId);

        Instant now = clock.instant();
        int inspectionRequired = (int) rooms.stream()
                .filter(room -> "INSPECTION_REQUIRED".equals(room.operationalStatus())).count();
        int outOfService = (int) rooms.stream()
                .filter(room -> "OUT_OF_SERVICE".equals(room.operationalStatus())).count();
        int overdueRecovery = (int) rooms.stream()
                .filter(room -> !"AVAILABLE".equals(room.operationalStatus()))
                .filter(room -> room.expectedRecoveryAt() != null && room.expectedRecoveryAt().isBefore(now)).count();
        return new RoomOperationsView(hotelId,
                new RoomOperationsView.Summary(inspectionRequired, outOfService, overdueRecovery), rooms);
    }

    @Transactional
    public RoomOperationalTransitionResult transition(String token, UUID roomId, String idempotencyKey,
            RoomOperationalTransitionRequest request) {
        requireIdempotencyKey(idempotencyKey);
        NormalizedRequest normalized = normalize(request);
        StaffPrincipal staff = staffAccess.current(token);
        LockedRoom room = lockRoom(roomId);
        staffAccess.requireHotel(staff, room.hotelId());

        String requestHash = requestHash(staff.id(), normalized);
        ExistingEvent existing = existingEvent(roomId, idempotencyKey.trim());
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException("IDEMPOTENCY_CONFLICT", "같은 멱등 키에 다른 요청을 사용할 수 없습니다.");
            }
            return replayResult(room, existing);
        }

        if (room.operationalVersion() != normalized.expectedVersion()) {
            throw new BusinessConflictException("ROOM_OPERATIONAL_VERSION_CONFLICT", "객실 운영 상태가 이미 변경되었습니다.");
        }
        if (room.operationalStatus().equals(normalized.targetStatus())) {
            throw new BusinessConflictException("ROOM_OPERATIONAL_STATUS_UNCHANGED", "현재와 동일한 운영 상태로 변경할 수 없습니다.");
        }
        if ("AVAILABLE".equals(normalized.targetStatus()) && !"CLEAN".equals(room.housekeepingStatus())) {
            throw new BusinessConflictException("ROOM_NOT_CLEAN", "청소 완료된 객실만 사용 가능 상태로 복구할 수 있습니다.");
        }

        List<RoomOperationsView.ImpactedAssignment> impacts = impactedAssignments(roomId, room.zoneId());
        if ("OUT_OF_SERVICE".equals(normalized.targetStatus()) && !impacts.isEmpty()) {
            throw new RoomHasActiveAssignmentsException(impacts);
        }

        UUID eventId = UUID.randomUUID();
        String storedReason = "AVAILABLE".equals(normalized.targetStatus()) ? null : normalized.reason();
        Instant storedRecovery = "AVAILABLE".equals(normalized.targetStatus()) ? null : normalized.expectedRecoveryAt();
        jdbc.update("""
                update physical_room
                   set operational_status = ?, operational_reason = ?, expected_recovery_at = ?,
                       operational_version = operational_version + 1, operational_updated_at = ?
                 where id = ?
                """, normalized.targetStatus(), storedReason, timestamp(storedRecovery),
                Timestamp.from(clock.instant()), roomId);
        jdbc.update("""
                insert into physical_room_operational_event
                    (id, physical_room_id, previous_status, status, previous_reason, reason,
                     previous_expected_recovery_at, expected_recovery_at, staff_id,
                     idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, roomId, room.operationalStatus(), normalized.targetStatus(),
                room.operationalReason(), normalized.reason(), timestamp(room.expectedRecoveryAt()),
                timestamp(storedRecovery), staff.id(), idempotencyKey.trim(), requestHash,
                Timestamp.from(clock.instant()));
        return result(roomId);
    }

    private LockedRoom lockRoom(UUID roomId) {
        LockedRoom room = jdbc.query("""
                select pr.id, pr.hotel_id, pr.room_number, pr.housekeeping_status,
                       pr.operational_status, pr.operational_reason, pr.expected_recovery_at,
                       pr.operational_version, h.timezone
                  from physical_room pr join hotel h on h.id = pr.hotel_id
                 where pr.id = ? for update
                """, rs -> rs.next() ? new LockedRoom(
                        rs.getObject("id", UUID.class), rs.getObject("hotel_id", UUID.class),
                        rs.getString("room_number"), rs.getString("housekeeping_status"),
                        rs.getString("operational_status"), rs.getString("operational_reason"),
                        instant(rs, "expected_recovery_at"), rs.getLong("operational_version"),
                        ZoneId.of(rs.getString("timezone"))) : null,
                roomId);
        if (room == null) {
            throw new IllegalArgumentException("객실을 찾을 수 없습니다.");
        }
        return room;
    }

    private ExistingEvent existingEvent(UUID roomId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash, status, reason, expected_recovery_at
                  from physical_room_operational_event
                 where physical_room_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingEvent(
                        rs.getString("request_hash"), rs.getString("status"), rs.getString("reason"),
                        instant(rs, "expected_recovery_at")) : null,
                roomId, idempotencyKey);
    }

    private RoomOperationalTransitionResult replayResult(LockedRoom room, ExistingEvent event) {
        String operationalReason = "AVAILABLE".equals(event.status()) ? null : event.reason();
        return new RoomOperationalTransitionResult(room.id(), room.roomNumber(), room.housekeepingStatus(),
                event.status(), operationalReason, event.expectedRecoveryAt(), room.operationalVersion());
    }

    private RoomOperationalTransitionResult result(UUID roomId) {
        return jdbc.query("""
                select id, room_number, housekeeping_status, operational_status, operational_reason,
                       expected_recovery_at, operational_version
                  from physical_room where id = ?
                """, rs -> rs.next() ? new RoomOperationalTransitionResult(
                        rs.getObject("id", UUID.class), rs.getString("room_number"),
                        rs.getString("housekeeping_status"), rs.getString("operational_status"),
                        rs.getString("operational_reason"), instant(rs, "expected_recovery_at"),
                        rs.getLong("operational_version")) : null,
                roomId);
    }

    private List<RoomOperationsView.ImpactedAssignment> impactedAssignments(UUID roomId, ZoneId zoneId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneId);
        return jdbc.query("""
                select r.id, r.guest_name, r.status, r.check_in, r.check_out
                  from reservation_room_assignment a
                  join reservation r on r.id = a.reservation_id
                 where a.physical_room_id = ?
                   and (r.status = 'CHECKED_IN' or (r.status = 'CONFIRMED' and r.check_out > ?))
                 order by r.check_in, r.id
                """, (rs, rowNum) -> new RoomOperationsView.ImpactedAssignment(
                        rs.getObject("id", UUID.class), rs.getString("guest_name"), rs.getString("status"),
                        rs.getObject("check_in", LocalDate.class), rs.getObject("check_out", LocalDate.class)),
                roomId, today);
    }

    private List<RoomOperationsView.EventItem> recentEvents(UUID roomId) {
        return jdbc.query("""
                select id, previous_status, status, reason, expected_recovery_at, staff_id, created_at
                  from physical_room_operational_event
                 where physical_room_id = ?
                 order by created_at desc, id desc
                 limit 5
                """, (rs, rowNum) -> new RoomOperationsView.EventItem(
                        rs.getObject("id", UUID.class), rs.getString("previous_status"), rs.getString("status"),
                        rs.getString("reason"), instant(rs, "expected_recovery_at"),
                        rs.getObject("staff_id", UUID.class), instant(rs, "created_at")),
                roomId);
    }

    private NormalizedRequest normalize(RoomOperationalTransitionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("운영 상태 변경 요청은 필수입니다.");
        }
        String targetStatus = request.targetStatus() == null ? "" : request.targetStatus().trim();
        if (!STATUSES.contains(targetStatus)) {
            throw new IllegalArgumentException("지원하지 않는 객실 운영 상태입니다.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new IllegalArgumentException("변경 사유는 1자 이상 500자 이하로 입력해야 합니다.");
        }
        if (request.expectedVersion() < 0) {
            throw new IllegalArgumentException("운영 상태 버전은 0 이상이어야 합니다.");
        }
        if (request.expectedRecoveryAt() != null && request.expectedRecoveryAt().isBefore(clock.instant())) {
            throw new IllegalArgumentException("예상 복구 시각은 과거일 수 없습니다.");
        }
        return new NormalizedRequest(targetStatus, reason, request.expectedRecoveryAt(), request.expectedVersion());
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.trim().length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key는 1자 이상 100자 이하로 입력해야 합니다.");
        }
    }

    private String requestHash(UUID staffId, NormalizedRequest request) {
        String value = staffId + "\n" + request.targetStatus() + "\n" + request.reason() + "\n"
                + (request.expectedRecoveryAt() == null ? "" : request.expectedRecoveryAt()) + "\n"
                + request.expectedVersion();
        return reservationAccess.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private record LockedRoom(
            UUID id,
            UUID hotelId,
            String roomNumber,
            String housekeepingStatus,
            String operationalStatus,
            String operationalReason,
            Instant expectedRecoveryAt,
            long operationalVersion,
            ZoneId zoneId) {}

    private record ExistingEvent(String requestHash, String status, String reason, Instant expectedRecoveryAt) {}

    private record NormalizedRequest(
            String targetStatus, String reason, Instant expectedRecoveryAt, long expectedVersion) {}
}
