package team.hotelchain.guestrequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 고객이 호텔에 예약 관련 요청·문의를 보내고 직원이 처리 상태를 바꾼다.
 * <p>
 * 예약 변경·취소는 전용 API가 있으므로 여기서 처리하지 않는다. 이 서비스는
 * 객실 요청·편의 요청·환불 문의·일반 문의를 접수만 한다. 가격·재고·예약 상태를
 * 바꾸지 않는다.
 * <p>
 * 멱원은 두 갈래로 동작한다. 같은 Idempotency-Key 재호출은 200에 created=false로
 * 같은 요청을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이
 * 같아 같은 결과를 돌려준다.
 */
@Service
public class GuestRequestCommandService {

    static final int MAX_BODY_LENGTH = 2_000;
    static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
    private static final int SUBJECT_MAX_LENGTH = 100;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int EMAIL_MAX_LENGTH = 254;
    private static final int PHONE_MAX_LENGTH = 30;
    private static final int RESOLUTION_NOTE_MAX_LENGTH = 500;

    private static final Set<String> REQUEST_TYPES = Set.of(
            "ROOM_REQUEST", "AMENITY_REQUEST", "REFUND_INQUIRY", "GENERAL_INQUIRY", "OTHER");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StaffAccessService access;
    private final GuestRequestQueryService queries;
    private final GuestRequestAssigneeService assignees;

    public GuestRequestCommandService(JdbcTemplate jdbc, Clock clock,
            StaffAccessService access, GuestRequestQueryService queries,
            GuestRequestAssigneeService assignees) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
        this.queries = queries;
        this.assignees = assignees;
    }

    @Transactional
    public GuestRequestCreateResponse submit(
            UUID hotelId,
            UUID reservationId,
            String idempotencyKey,
            GuestRequestCreateRequest input) {

        requireIdempotencyKey(idempotencyKey);
        requireInput(input);
        if (!REQUEST_TYPES.contains(input.requestType())) {
            throw new IllegalArgumentException("지원하지 않는 요청 유형입니다.");
        }

        UUID resolvedHotelId = resolveHotel(hotelId, reservationId);
        if (reservationId != null) {
            requireReservationBelongsToHotel(reservationId, resolvedHotelId);
        }

        String requestHash = requestHash(resolvedHotelId, reservationId, input);
        Instant now = clock.instant();
        UUID requestId = UUID.randomUUID();
        // 같은 내용의 서로 다른 키도 직렬화해 응답 유실 재시도가 한 요청을 가리키게 한다.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", requestHash);
        int claimed = jdbc.update("""
                insert into guest_request_submission_command
                    (idempotency_key, request_hash, request_id, created_at)
                values (?, ?, ?, ?)
                on conflict (idempotency_key) do nothing
                """, idempotencyKey, requestHash, requestId, java.sql.Timestamp.from(now));
        if (claimed == 0) {
            SubmissionReceipt receipt = findSubmissionCommand(idempotencyKey);
            if (receipt == null || !receipt.requestHash().equals(requestHash)) {
                throw new GuestRequestStateConflictException("같은 멱등 키에 다른 고객 요청을 사용할 수 없습니다.");
            }
            return replay(findExistingById(receipt.requestId()));
        }

        Existing duplicate = findExistingByHash(requestHash);
        if (duplicate != null) {
            jdbc.update("update guest_request_submission_command set request_id = ? where idempotency_key = ?",
                    duplicate.id(), idempotencyKey);
            return replay(duplicate);
        }

        jdbc.update("""
                insert into guest_request
                    (id, hotel_id, reservation_id, request_type, subject, body,
                     guest_name, guest_email, guest_phone, status, priority,
                     idempotency_key, request_hash, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 'NORMAL', ?, ?, ?, ?)
                """, requestId, resolvedHotelId, reservationId,
                input.requestType(), input.subject().trim(), input.body().trim(),
                input.guestName().trim(), input.guestEmail().trim().toLowerCase(),
                isBlank(input.guestPhone()) ? null : input.guestPhone().trim(),
                idempotencyKey, requestHash,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        jdbc.update("""
                insert into guest_request_event
                    (id, request_id, event_type, from_status, to_status, from_priority, to_priority,
                     actor_staff_id, note, created_at)
                values (?, ?, 'CREATED', null, 'OPEN', null, 'NORMAL', null, null, ?)
                """, UUID.randomUUID(), requestId, java.sql.Timestamp.from(now));

        return new GuestRequestCreateResponse(
                requestId, input.requestType(), input.subject().trim(), "OPEN", now, true);
    }

    // 직원이 고객 요청의 처리 상태를 바꾼다. 이미 같은 상태면 값을 바꾸지 않고
    // 멱원 기록만 남겨서 재시도가 200으로 같은 결과를 돌려주게 한다.
    @Transactional
    public GuestRequestView transition(
            String token,
            UUID requestId,
            String idempotencyKey,
            GuestRequestTransitionRequest input) {
        requireIdempotencyKey(idempotencyKey);
        if (input == null || isBlank(input.status())) {
            throw new IllegalArgumentException("바꿀 처리 상태를 입력해 주세요.");
        }
        String nextStatus = input.status().trim();
        if (!STATUSES.contains(nextStatus)) {
            throw new IllegalArgumentException("알 수 없는 처리 상태입니다.");
        }
        String nextPriority = isBlank(input.priority()) ? null : input.priority().trim();
        if (nextPriority != null && !PRIORITIES.contains(nextPriority)) {
            throw new IllegalArgumentException("알 수 없는 우선순위입니다.");
        }
        if (input.resolutionNote() != null && input.resolutionNote().length() > RESOLUTION_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("처리 안내는 최대 " + RESOLUTION_NOTE_MAX_LENGTH + "자입니다.");
        }

        StaffPrincipal staff = access.current(token);
        Existing request = loadExistingForUpdate(requestId);
        requireHotelAccess(staff, request.hotelId());
        String commandHash = transitionHash(input, nextStatus, nextPriority);
        CommandReceipt receipt = findTransitionCommand(requestId, idempotencyKey);
        if (receipt != null) {
            if (!receipt.requestHash().equals(commandHash)) {
                throw new GuestRequestStateConflictException("같은 멱등 키에 다른 변경 요청을 사용할 수 없습니다.");
            }
            return queries.get(token, requestId);
        }
        if (input.assignTo() != null) {
            assignees.requireAssignable(staff, input.assignTo(), request.hotelId());
        }

        UUID nextAssignee = input.assignTo() == null ? request.assignedTo() : input.assignTo();
        String effectivePriority = nextPriority == null ? request.priority() : nextPriority;
        boolean assignmentChanged = !java.util.Objects.equals(request.assignedTo(), nextAssignee);
        boolean priorityChanged = !request.priority().equals(effectivePriority);
        boolean statusChanged = !request.status().equals(nextStatus);

        jdbc.update("""
                insert into guest_request_transition_command
                    (request_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?)
                """, requestId, idempotencyKey, commandHash,
                java.sql.Timestamp.from(clock.instant()));

        if (!assignmentChanged && !priorityChanged && !statusChanged) {
            return queries.get(token, requestId);
        }

        String previousStatus = request.status();
        Instant now = clock.instant();
        jdbc.update("""
                update guest_request
                   set status = ?, assigned_to = ?, priority = ?,
                       resolution_note = coalesce(?, resolution_note),
                       updated_at = ?
                 where id = ?
                """, nextStatus, nextAssignee, effectivePriority, input.resolutionNote(),
                java.sql.Timestamp.from(now), requestId);

        int sequence = 0;
        if (assignmentChanged) {
            insertAssignmentEvent(requestId, request.assignedTo(), nextAssignee, staff.id(),
                    input.resolutionNote(), now.plusNanos(sequence++ * 1_000L));
        }
        if (priorityChanged) {
            insertPriorityEvent(requestId, request.priority(), effectivePriority, staff.id(),
                    input.resolutionNote(), now.plusNanos(sequence++ * 1_000L));
        }
        if (statusChanged) {
            insertStatusEvent(requestId, previousStatus, nextStatus, staff.id(),
                    input.resolutionNote(), now.plusNanos(sequence * 1_000L));
        }

        return queries.get(token, requestId);
    }

    private UUID resolveHotel(UUID hotelId, UUID reservationId) {
        if (hotelId == null && reservationId == null) {
            throw new IllegalArgumentException("지점 또는 예약을 입력해 주세요.");
        }
        if (hotelId != null) {
            if (!hotelExists(hotelId)) {
                throw new HotelNotFoundException(hotelId);
            }
            return hotelId;
        }
        UUID found = jdbc.query(
                "select rt.hotel_id from reservation r"
                        + " join room_type rt on rt.id = r.room_type_id where r.id = ?",
                rs -> rs.next() ? rs.getObject("hotel_id", UUID.class) : null,
                reservationId);
        if (found == null) {
            throw new ReservationNotFoundException();
        }
        if (!hotelExists(found)) {
            throw new HotelNotFoundException(found);
        }
        return found;
    }

    private void requireReservationBelongsToHotel(UUID reservationId, UUID hotelId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from reservation r
                  join room_type rt on rt.id = r.room_type_id
                 where r.id = ? and rt.hotel_id = ?
                """, Integer.class, reservationId, hotelId);
        if (count == null || count == 0) {
            throw new ReservationNotFoundException();
        }
    }

    private Existing loadExistingForUpdate(UUID requestId) {
        Existing request = jdbc.query(
                "select id, hotel_id, request_type, subject, status, priority, assigned_to, created_at"
                        + " from guest_request where id = ? for update",
                rs -> rs.next() ? new Existing(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getString("request_type"),
                        rs.getString("subject"),
                        rs.getString("status"),
                        rs.getString("priority"),
                        rs.getObject("assigned_to", UUID.class),
                        rs.getTimestamp("created_at").toInstant()) : null,
                requestId);
        if (request == null) {
            throw new GuestRequestNotFoundException(requestId);
        }
        return request;
    }

    private Existing findExistingById(UUID requestId) {
        return jdbc.query(
                "select id, hotel_id, request_type, subject, status, priority, assigned_to, created_at from guest_request"
                        + " where id = ?",
                rs -> rs.next() ? new Existing(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getString("request_type"),
                        rs.getString("subject"),
                        rs.getString("status"),
                        rs.getString("priority"),
                        rs.getObject("assigned_to", UUID.class),
                        rs.getTimestamp("created_at").toInstant()) : null,
                requestId);
    }

    private Existing findExistingByHash(String requestHash) {
        return jdbc.query(
                "select id, hotel_id, request_type, subject, status, priority, assigned_to, created_at"
                        + " from guest_request where request_hash = ? order by created_at, id limit 1",
                rs -> rs.next() ? new Existing(
                        rs.getObject("id", UUID.class), rs.getObject("hotel_id", UUID.class),
                        rs.getString("request_type"), rs.getString("subject"), rs.getString("status"),
                        rs.getString("priority"), rs.getObject("assigned_to", UUID.class),
                        rs.getTimestamp("created_at").toInstant()) : null,
                requestHash);
    }

    private SubmissionReceipt findSubmissionCommand(String idempotencyKey) {
        return jdbc.query("""
                select request_hash, request_id
                  from guest_request_submission_command
                 where idempotency_key = ?
                """, rs -> rs.next() ? new SubmissionReceipt(
                        rs.getString("request_hash"), rs.getObject("request_id", UUID.class)) : null,
                idempotencyKey);
    }

    private GuestRequestCreateResponse replay(Existing existing) {
        if (existing == null) {
            throw new IllegalStateException("멱등 접수 결과를 찾을 수 없습니다.");
        }
        return new GuestRequestCreateResponse(
                existing.id(), existing.requestType(), existing.subject(), existing.status(),
                existing.createdAt(), false);
    }

    private void requireHotelAccess(StaffPrincipal staff, UUID hotelId) {
        if ("HQ_ADMIN".equals(staff.role())) {
            return;
        }
        if ("BRANCH_STAFF".equals(staff.role()) && hotelId.equals(staff.hotelId())) {
            return;
        }
        throw new StaffAccessDeniedException();
    }

    private CommandReceipt findTransitionCommand(UUID requestId, String idempotencyKey) {
        return jdbc.query("""
                select request_hash from guest_request_transition_command
                 where request_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new CommandReceipt(rs.getString("request_hash")) : null,
                requestId, idempotencyKey);
    }

    private void insertAssignmentEvent(UUID requestId, UUID from, UUID to, UUID actor,
            String note, Instant createdAt) {
        jdbc.update("""
                insert into guest_request_event
                    (id, request_id, event_type, from_assigned_to, to_assigned_to,
                     actor_staff_id, note, created_at)
                values (?, ?, 'ASSIGNED', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), requestId, from, to, actor, note,
                java.sql.Timestamp.from(createdAt));
    }

    private void insertPriorityEvent(UUID requestId, String from, String to, UUID actor,
            String note, Instant createdAt) {
        jdbc.update("""
                insert into guest_request_event
                    (id, request_id, event_type, from_priority, to_priority,
                     actor_staff_id, note, created_at)
                values (?, ?, 'PRIORITY_CHANGED', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), requestId, from, to, actor, note,
                java.sql.Timestamp.from(createdAt));
    }

    private void insertStatusEvent(UUID requestId, String from, String to, UUID actor,
            String note, Instant createdAt) {
        jdbc.update("""
                insert into guest_request_event
                    (id, request_id, event_type, from_status, to_status,
                     actor_staff_id, note, created_at)
                values (?, ?, 'STATUS_CHANGED', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), requestId, from, to, actor, note,
                java.sql.Timestamp.from(createdAt));
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (isBlank(idempotencyKey) || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private void requireInput(GuestRequestCreateRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("요청 내용을 입력해 주세요.");
        }
        requireField(input.subject(), "제목", SUBJECT_MAX_LENGTH);
        requireField(input.body(), "내용", MAX_BODY_LENGTH);
        requireField(input.guestName(), "이름", NAME_MAX_LENGTH);
        requireField(input.guestEmail(), "이메일", EMAIL_MAX_LENGTH);
        if (!isBlank(input.guestPhone()) && input.guestPhone().length() > PHONE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "전화번호가 너무 깁니다. 최대 " + PHONE_MAX_LENGTH + "자입니다.");
        }
    }

    private void requireField(String value, String label, int maxLength) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "이(가) 너무 깁니다. 최대 " + maxLength + "자입니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String requestHash(UUID hotelId, UUID reservationId, GuestRequestCreateRequest input) {
        String payload = String.join("|",
                hotelId.toString(),
                reservationId == null ? "none" : reservationId.toString(),
                input.requestType(),
                input.subject().trim(),
                input.body().trim(),
                input.guestName().trim(),
                input.guestEmail().trim().toLowerCase());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String transitionHash(GuestRequestTransitionRequest input, String status, String priority) {
        return sha256(String.join("|",
                status,
                input.assignTo() == null ? "unchanged" : input.assignTo().toString(),
                priority == null ? "unchanged" : priority,
                input.resolutionNote() == null ? "<null>" : "<value>" + input.resolutionNote()));
    }

    private String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private record Existing(UUID id, UUID hotelId, String requestType, String subject,
            String status, String priority, UUID assignedTo, Instant createdAt) {
    }

    private record CommandReceipt(String requestHash) {}

    private record SubmissionReceipt(String requestHash, UUID requestId) {}
}
