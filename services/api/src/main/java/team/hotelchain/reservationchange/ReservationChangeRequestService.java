package team.hotelchain.reservationchange;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationAccess;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.reservationchange.ReservationStayQuote.SelectedStayOffer;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class ReservationChangeRequestService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationAccess reservationAccess;
    private final ReservationStayQuoteService quoteService;
    private final ReservationChangePolicy policy;
    private final ReservationChangeViews views;
    private final ReservationChangeHoldService holds;
    private final Clock clock;

    public ReservationChangeRequestService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationAccess reservationAccess,
            ReservationStayQuoteService quoteService,
            ReservationChangePolicy policy,
            ReservationChangeViews views,
            ReservationChangeHoldService holds,
            Clock clock) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.reservationAccess = reservationAccess;
        this.quoteService = quoteService;
        this.policy = policy;
        this.views = views;
        this.holds = holds;
        this.clock = clock;
    }

    @Transactional
    public ReservationChangeRequestView create(
            String token,
            UUID reservationId,
            String idempotencyKey,
            CreateReservationChangeRequest request) {
        StaffPrincipal staff = staffAccess.current(token);
        validateCreate(idempotencyKey, request);
        String requestHash = requestHash(staff.id(), reservationId, request);
        ReservationStayQuote quote = quoteService.quote(
                reservationId, request.checkIn(), request.checkOut(), true);
        staffAccess.requireHotel(staff, quote.hotelId());
        team.hotelchain.reservation.PaymentProviderSafety.requireFakeSettlement(jdbc, reservationId);

        ExistingRequest existing = findExisting(reservationId, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException(
                        "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 예약 변경 요청이 사용되었습니다.");
            }
            return views.get(staff, existing.id());
        }

        requireChangeable(quote);
        SelectedStayOffer offer = quote.selected(request.roomTypeId(), request.ratePlanId());
        if (offer.totalKrw() != request.expectedTotal()) {
            throw new BusinessConflictException(
                    "PRICE_CHANGED", "요금이 변경되었습니다. 최신 변경안을 다시 확인해 주세요.");
        }
        if (quote.roomTypeId().equals(request.roomTypeId())
                && quote.ratePlanId().equals(request.ratePlanId())
                && quote.previousCheckIn().equals(request.checkIn())
                && quote.previousCheckOut().equals(request.checkOut())) {
            throw new BusinessConflictException(
                    "RESERVATION_STAY_UNCHANGED", "변경할 날짜 또는 객실 유형을 선택해 주세요.");
        }
        if (activeRequestExists(reservationId)) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_ACTIVE", "진행 중인 예약 변경 요청을 먼저 처리해 주세요.");
        }

        UUID requestId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        long absoluteDifference = Math.abs(offer.differenceKrw());
        boolean automaticallyApproved = "HQ_ADMIN".equals(staff.role())
                || absoluteDifference <= policy.directLimitKrw();
        String status = automaticallyApproved ? "APPROVED" : "PENDING_APPROVAL";
        String direction = direction(offer.differenceKrw());
        Instant now = clock.instant();
        Instant approvalExpiresAt = policy.approvalExpiresAt(now, request.checkIn(), quote.timezone());

        try {
            jdbc.update("""
                    insert into reservation_change_request (
                        id, reservation_id, hotel_id, base_operation_revision, status,
                        settlement_direction, requested_by, idempotency_key, request_hash,
                        previous_check_in, previous_check_out, previous_room_type_id, previous_rate_plan_id,
                        target_check_in, target_check_out, target_room_type_id, target_rate_plan_id,
                        rooms, adults, children, approval_limit_krw, approval_expires_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, requestId, reservationId, quote.hotelId(), quote.operationRevision(), status,
                    direction, staff.id(), idempotencyKey, requestHash,
                    quote.previousCheckIn(), quote.previousCheckOut(), quote.roomTypeId(), quote.ratePlanId(),
                    request.checkIn(), request.checkOut(), request.roomTypeId(), request.ratePlanId(),
                    quote.rooms(), quote.adults(), quote.children(),
                    automaticallyApproved ? absoluteDifference : null, Timestamp.from(approvalExpiresAt));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_ACTIVE", "진행 중인 예약 변경 요청을 먼저 처리해 주세요.");
        }
        insertQuote(requestId, quoteId, 1, quote, offer);
        jdbc.update("update reservation_change_request set current_quote_id = ? where id = ?", quoteId, requestId);
        if (automaticallyApproved) {
            insertApproval(
                    requestId, quoteId, staff,
                    "HQ_ADMIN".equals(staff.role()) ? "HQ_APPROVED" : "AUTO_APPROVED",
                    direction, absoluteDifference, null);
        }
        insertEvent(requestId, "REQUEST_CREATED", null, status, staff.id(), null, null);
        return views.get(staff, requestId);
    }

    public ReservationChangePolicyView policy(String token) {
        staffAccess.current(token);
        return new ReservationChangePolicyView(
                policy.settlementEnabled(), policy.directLimitKrw(),
                policy.approvalTtl().toSeconds(), policy.holdTtl().toSeconds());
    }

    public ReservationChangeRequestView get(String token, UUID requestId) {
        return views.get(staffAccess.current(token), requestId);
    }

    public List<ReservationChangeRequestView> list(String token, String status, UUID hotelId) {
        return views.list(staffAccess.current(token), status, hotelId);
    }

    @Transactional
    public ReservationChangeRequestView cancel(
            String token,
            UUID requestId,
            String idempotencyKey,
            ReservationChangeVersionRequest request) {
        requireMutation(idempotencyKey, request == null ? -1 : request.version());
        StaffPrincipal staff = staffAccess.current(token);
        LockedRequest locked = lockRequestWithAccess(staff, requestId);
        String dedupeKey = dedupeKey(requestId, "cancel", staff.id(), idempotencyKey);
        if (eventExists(dedupeKey)) {
            return views.get(staff, requestId);
        }
        requireVersion(locked, request.version());
        if (!List.of("PENDING_APPROVAL", "APPROVED").contains(locked.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_STATE_CONFLICT", "정산 시작 전 변경 요청만 취소할 수 있습니다.");
        }
        jdbc.update("""
                update reservation_change_request
                set status = 'CANCELLED', version = version + 1, updated_at = ?
                where id = ?
                """, Timestamp.from(clock.instant()), requestId);
        insertEvent(requestId, "REQUEST_CANCELLED", locked.status(), "CANCELLED", staff.id(), dedupeKey, null);
        return views.get(staff, requestId);
    }

    @Transactional
    public ReservationChangeRequestView reprice(
            String token,
            UUID requestId,
            String idempotencyKey,
            ReservationChangeVersionRequest request) {
        requireMutation(idempotencyKey, request == null ? -1 : request.version());
        StaffPrincipal staff = staffAccess.current(token);
        RequestTarget target = findTarget(requestId);
        staffAccess.requireHotel(staff, target.hotelId());
        team.hotelchain.reservation.PaymentProviderSafety.requireFakeSettlement(jdbc, target.reservationId());
        ReservationStayQuote quote = quoteService.quote(
                target.reservationId(), target.checkIn(), target.checkOut(), true);
        LockedRequest locked = lockRequestWithAccess(staff, requestId);
        String dedupeKey = dedupeKey(requestId, "reprice", staff.id(), idempotencyKey);
        if (eventExists(dedupeKey)) return views.get(staff, requestId);
        requireVersion(locked, request.version());
        if (!List.of("PENDING_APPROVAL", "APPROVED").contains(locked.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_STATE_CONFLICT", "승인 전후의 변경 요청만 재견적할 수 있습니다.");
        }
        if (quote.operationRevision() != locked.baseOperationRevision()) {
            throw new BusinessConflictException(
                    "RESERVATION_REVISION_CONFLICT", "예약 조건이 변경되었습니다. 새 요청을 만들어 주세요.");
        }
        SelectedStayOffer offer = quote.selected(target.roomTypeId(), target.ratePlanId());
        long revision = nextQuoteRevision(requestId);
        UUID quoteId = UUID.randomUUID();
        insertQuote(requestId, quoteId, revision, quote, offer);

        String newDirection = direction(offer.differenceKrw());
        long absoluteDifference = Math.abs(offer.differenceKrw());
        boolean branchAutoApproval = "BRANCH_STAFF".equals(staff.role())
                && absoluteDifference <= policy.directLimitKrw();
        boolean preservesApproval = "APPROVED".equals(locked.status())
                && locked.settlementDirection().equals(newDirection)
                && locked.approvalLimitKrw() != null
                && absoluteDifference <= locked.approvalLimitKrw();
        String newStatus = branchAutoApproval || preservesApproval || "HQ_ADMIN".equals(staff.role())
                ? "APPROVED" : "PENDING_APPROVAL";
        Long newLimit = "APPROVED".equals(newStatus)
                ? (preservesApproval ? locked.approvalLimitKrw() : absoluteDifference) : null;
        jdbc.update("""
                update reservation_change_request
                set current_quote_id = ?, settlement_direction = ?, status = ?, approval_limit_krw = ?,
                    version = version + 1, updated_at = ?
                where id = ?
                """, quoteId, newDirection, newStatus, newLimit, Timestamp.from(clock.instant()), requestId);
        if (!preservesApproval && "APPROVED".equals(locked.status())) {
            insertApproval(requestId, quoteId, staff, "INVALIDATED", newDirection,
                    absoluteDifference, "재견적으로 기존 승인이 무효화되었습니다.");
        }
        if (!"APPROVED".equals(locked.status()) && "APPROVED".equals(newStatus)) {
            insertApproval(requestId, quoteId, staff,
                    "HQ_ADMIN".equals(staff.role()) ? "HQ_APPROVED" : "AUTO_APPROVED",
                    newDirection, absoluteDifference, "재견적 승인");
        }
        insertEvent(requestId, "REQUEST_REPRICED", locked.status(), newStatus, staff.id(), dedupeKey, null);
        if ("APPROVED".equals(newStatus) && "NONE".equals(newDirection)) {
            ReservationChangeHoldService.HoldResult hold = holds.acquire(requestId, request.version() + 1);
            jdbc.update("""
                    update reservation_change_request
                    set status = 'READY_TO_APPLY', version = version + 1, updated_at = ?
                    where id = ? and version = ?
                    """, Timestamp.from(clock.instant()), requestId, hold.requestVersion());
            insertEvent(requestId, "ZERO_DIFFERENCE_READY", "APPROVED", "READY_TO_APPLY",
                    staff.id(), dedupeKey + ":ready", null);
        }
        return views.get(staff, requestId);
    }

    void insertApproval(
            UUID requestId,
            UUID quoteId,
            StaffPrincipal staff,
            String decisionType,
            String direction,
            long limit,
            String reason) {
        jdbc.update("""
                insert into reservation_change_approval (
                    id, request_id, quote_id, decision_type, decided_by, decided_role,
                    settlement_direction, max_abs_difference_krw, reason)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), requestId, quoteId, decisionType, staff.id(), staff.role(),
                direction, limit, reason);
    }

    void insertEvent(
            UUID requestId,
            String eventType,
            String fromStatus,
            String toStatus,
            UUID actor,
            String dedupeKey,
            String reason) {
        jdbc.update("""
                insert into reservation_change_event (
                    id, request_id, event_type, from_status, to_status,
                    actor_staff_id, dedupe_key, payload)
                values (?, ?, ?, ?, ?, ?, ?, jsonb_build_object('reason', ?::text))
                """, UUID.randomUUID(), requestId, eventType, fromStatus, toStatus,
                actor, dedupeKey, reason);
    }

    LockedRequest lockRequestWithAccess(StaffPrincipal staff, UUID requestId) {
        LockedRequest request = jdbc.query("""
                select id, hotel_id, status, settlement_direction, version, base_operation_revision,
                       current_quote_id, approval_limit_krw, approval_expires_at
                from reservation_change_request where id = ? for update
                """, rs -> rs.next() ? new LockedRequest(
                        rs.getObject("id", UUID.class), rs.getObject("hotel_id", UUID.class),
                        rs.getString("status"), rs.getString("settlement_direction"), rs.getLong("version"),
                        rs.getLong("base_operation_revision"), rs.getObject("current_quote_id", UUID.class),
                        rs.getObject("approval_limit_krw", Long.class),
                        rs.getTimestamp("approval_expires_at").toInstant()) : null, requestId);
        if (request == null) throw new ReservationNotFoundException();
        staffAccess.requireHotel(staff, request.hotelId());
        return request;
    }

    void requireVersion(LockedRequest request, long expectedVersion) {
        if (request.version() != expectedVersion) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_VERSION_CONFLICT", "예약 변경 요청이 갱신되었습니다. 다시 확인해 주세요.");
        }
    }

    boolean eventExists(String dedupeKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from reservation_change_event where dedupe_key = ?)",
                Boolean.class, dedupeKey));
    }

    String dedupeKey(UUID requestId, String action, UUID staffId, String idempotencyKey) {
        return requestId + ":" + action + ":" + staffId + ":" + idempotencyKey;
    }

    private void insertQuote(
            UUID requestId,
            UUID quoteId,
            long revision,
            ReservationStayQuote quote,
            SelectedStayOffer offer) {
        jdbc.update("""
                insert into reservation_change_quote (
                    id, request_id, revision, previous_total_krw, total_krw,
                    difference_krw, currency, rooms)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, quoteId, requestId, revision, quote.previousTotalKrw(), offer.totalKrw(),
                offer.differenceKrw(), offer.currency(), quote.rooms());
        offer.nightlyPrices().forEach(night -> jdbc.update("""
                insert into reservation_change_quote_night (quote_id, stay_date, amount_krw)
                values (?, ?, ?)
                """, quoteId, night.date(), night.amount()));
    }

    private ExistingRequest findExisting(UUID reservationId, String idempotencyKey) {
        return jdbc.query("""
                select id, request_hash from reservation_change_request
                where reservation_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new ExistingRequest(
                        rs.getObject("id", UUID.class), rs.getString("request_hash")) : null,
                reservationId, idempotencyKey);
    }

    private boolean activeRequestExists(UUID reservationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from reservation_change_request
                    where reservation_id = ?
                      and status not in ('COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'))
                """, Boolean.class, reservationId));
    }

    private RequestTarget findTarget(UUID requestId) {
        RequestTarget target = jdbc.query("""
                select reservation_id, hotel_id, target_check_in, target_check_out,
                       target_room_type_id, target_rate_plan_id
                from reservation_change_request where id = ?
                """, rs -> rs.next() ? new RequestTarget(
                        rs.getObject("reservation_id", UUID.class), rs.getObject("hotel_id", UUID.class),
                        rs.getDate("target_check_in").toLocalDate(), rs.getDate("target_check_out").toLocalDate(),
                        rs.getObject("target_room_type_id", UUID.class),
                        rs.getObject("target_rate_plan_id", UUID.class)) : null, requestId);
        if (target == null) throw new ReservationNotFoundException();
        return target;
    }

    private long nextQuoteRevision(UUID requestId) {
        return jdbc.queryForObject(
                "select coalesce(max(revision), 0) + 1 from reservation_change_quote where request_id = ?",
                Long.class, requestId);
    }

    private void validateCreate(String idempotencyKey, CreateReservationChangeRequest request) {
        requireIdempotencyKey(idempotencyKey);
        if (request == null || request.checkIn() == null || request.checkOut() == null
                || request.roomTypeId() == null || request.ratePlanId() == null
                || request.expectedTotal() == null || request.expectedTotal() < 0) {
            throw new IllegalArgumentException("변경할 숙박 조건과 예상 금액을 확인해 주세요.");
        }
    }

    private void requireMutation(String idempotencyKey, long version) {
        requireIdempotencyKey(idempotencyKey);
        if (version < 0) throw new IllegalArgumentException("올바른 요청 version이 필요합니다.");
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private void requireChangeable(ReservationStayQuote quote) {
        if (!"CONFIRMED".equals(quote.reservationStatus())) {
            throw new BusinessConflictException(
                    "RESERVATION_STATE_CONFLICT", "확정된 예약의 숙박 조건만 변경할 수 있습니다.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(quote.timezone())));
        if (!today.isBefore(quote.previousCheckIn()) || !quote.targetCheckIn().isAfter(today)) {
            throw new BusinessConflictException(
                    "RESERVATION_STAY_CHANGE_TOO_LATE", "체크인 전 예약만 변경할 수 있습니다.");
        }
        if (quote.assignments() > 0) {
            throw new BusinessConflictException(
                    "RESERVATION_HAS_ROOM_ASSIGNMENT", "배정 객실이 있는 예약은 숙박 조건을 변경할 수 없습니다.");
        }
    }

    private String requestHash(UUID staffId, UUID reservationId, CreateReservationChangeRequest request) {
        String value = String.join(":", staffId.toString(), reservationId.toString(),
                request.checkIn().toString(), request.checkOut().toString(),
                request.roomTypeId().toString(), request.ratePlanId().toString(),
                request.expectedTotal().toString());
        return reservationAccess.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String direction(long differenceKrw) {
        if (differenceKrw > 0) return "CHARGE";
        if (differenceKrw < 0) return "REFUND";
        return "NONE";
    }

    record LockedRequest(
            UUID id,
            UUID hotelId,
            String status,
            String settlementDirection,
            long version,
            long baseOperationRevision,
            UUID currentQuoteId,
            Long approvalLimitKrw,
            Instant approvalExpiresAt) {
    }

    private record ExistingRequest(UUID id, String requestHash) {
    }

    private record RequestTarget(
            UUID reservationId,
            UUID hotelId,
            LocalDate checkIn,
            LocalDate checkOut,
            UUID roomTypeId,
            UUID ratePlanId) {
    }
}
