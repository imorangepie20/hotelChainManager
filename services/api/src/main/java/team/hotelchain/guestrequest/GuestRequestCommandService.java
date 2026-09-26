package team.hotelchain.guestrequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Set<String> REQUEST_TYPES = Set.of(
            "ROOM_REQUEST", "AMENITY_REQUEST", "REFUND_INQUIRY", "GENERAL_INQUIRY", "OTHER");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StaffAccessService access;
    private final GuestRequestQueryService queries;

    public GuestRequestCommandService(JdbcTemplate jdbc, Clock clock,
            StaffAccessService access, GuestRequestQueryService queries) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
        this.queries = queries;
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
        Existing handled = findExisting(idempotencyKey, requestHash);
        if (handled != null) {
            return new GuestRequestCreateResponse(
                    handled.id(), handled.requestType(), handled.subject(), handled.status,
                    handled.createdAt(), false);
        }

        Instant now = clock.instant();
        UUID requestId = UUID.randomUUID();
        try {
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
        } catch (DataIntegrityViolationException conflict) {
            // 동시에 같은 내용이 접수됐으면 이미 저장된 요청을 돌려준다.
            Existing replay = findExisting(idempotencyKey, requestHash);
            if (replay != null) {
                return new GuestRequestCreateResponse(
                        replay.id(), replay.requestType(), replay.subject(), replay.status,
                        replay.createdAt(), false);
            }
            throw conflict;
        }

        jdbc.update("""
                insert into guest_request_event
                    (id, request_id, event_type, from_status, to_status, actor_staff_id, note, created_at)
                values (?, ?, 'CREATED', null, 'OPEN', null, null, ?)
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

        StaffPrincipal staff = access.current(token);
        Existing request = loadExisting(requestId);
        requireHotelAccess(staff, request.hotelId());
        if (nextStatus.equals(request.status())) {
            return queries.get(token, requestId);
        }

        String previousStatus = request.status();
        Instant now = clock.instant();
        jdbc.update("""
                update guest_request
                   set status = ?, assigned_to = coalesce(?, assigned_to),
                       resolution_note = coalesce(?, resolution_note),
                       updated_at = ?
                 where id = ?
                """, nextStatus, input.assignTo(), input.resolutionNote(),
                java.sql.Timestamp.from(now), requestId);

        jdbc.update("""
                insert into guest_request_event
                    (id, request_id, event_type, from_status, to_status, actor_staff_id, note, created_at)
                values (?, ?, 'STATUS_CHANGED', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), requestId, previousStatus, nextStatus, staff.id(),
                input.resolutionNote(), java.sql.Timestamp.from(now));

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

    private Existing loadExisting(UUID requestId) {
        Existing request = jdbc.query(
                "select id, hotel_id, request_type, subject, status, created_at from guest_request where id = ?",
                rs -> rs.next() ? new Existing(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getString("request_type"),
                        rs.getString("subject"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()) : null,
                requestId);
        if (request == null) {
            throw new GuestRequestNotFoundException(requestId);
        }
        return request;
    }

    private Existing findExisting(String idempotencyKey, String requestHash) {
        return jdbc.query(
                "select id, hotel_id, request_type, subject, status, created_at from guest_request"
                        + " where idempotency_key = ? and request_hash = ?",
                rs -> rs.next() ? new Existing(
                        rs.getObject("id", UUID.class),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getString("request_type"),
                        rs.getString("subject"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()) : null,
                idempotencyKey, requestHash);
    }

    private void requireHotelAccess(StaffPrincipal staff, UUID hotelId) {
        if (!"HQ_ADMIN".equals(staff.role()) && !hotelId.equals(staff.hotelId())) {
            throw new StaffAccessDeniedException();
        }
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

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private record Existing(UUID id, UUID hotelId, String requestType, String subject,
            String status, Instant createdAt) {
    }
}
