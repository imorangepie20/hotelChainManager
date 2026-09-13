package team.hotelchain.reservationchange;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

@Service
public class ReservationChangeApprovalService {
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;
    private final ReservationChangeRequestService requests;
    private final ReservationChangeViews views;
    private final Clock clock;

    public ReservationChangeApprovalService(
            JdbcTemplate jdbc,
            StaffAccessService staffAccess,
            ReservationChangeRequestService requests,
            ReservationChangeViews views,
            Clock clock) {
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
        this.requests = requests;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public ReservationChangeRequestView approve(
            String token,
            UUID requestId,
            String idempotencyKey,
            ReservationChangeApprovalRequest request) {
        requireMutation(idempotencyKey, request == null ? -1 : request.version());
        StaffPrincipal staff = staffAccess.requireHeadquarters(token);
        ReservationChangeRequestService.LockedRequest locked = requests.lockRequestWithAccess(staff, requestId);
        String dedupeKey = requests.dedupeKey(requestId, "approve", staff.id(), idempotencyKey);
        if (requests.eventExists(dedupeKey)) return views.get(staff, requestId);
        requests.requireVersion(locked, request.version());
        requirePendingAndUnexpired(locked);

        long limit = Math.abs(jdbc.queryForObject(
                "select difference_krw from reservation_change_quote where id = ?",
                Long.class, locked.currentQuoteId()));
        requests.insertApproval(requestId, locked.currentQuoteId(), staff, "HQ_APPROVED",
                locked.settlementDirection(), limit, null);
        jdbc.update("""
                update reservation_change_request
                set status = 'APPROVED', approval_limit_krw = ?, version = version + 1, updated_at = ?
                where id = ?
                """, limit, Timestamp.from(clock.instant()), requestId);
        requests.insertEvent(requestId, "REQUEST_APPROVED", locked.status(), "APPROVED",
                staff.id(), dedupeKey, null);
        return views.get(staff, requestId);
    }

    @Transactional
    public ReservationChangeRequestView reject(
            String token,
            UUID requestId,
            String idempotencyKey,
            ReservationChangeRejectionRequest request) {
        requireMutation(idempotencyKey, request == null ? -1 : request.version());
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("반려 사유를 입력해 주세요.");
        }
        StaffPrincipal staff = staffAccess.requireHeadquarters(token);
        ReservationChangeRequestService.LockedRequest locked = requests.lockRequestWithAccess(staff, requestId);
        String dedupeKey = requests.dedupeKey(requestId, "reject", staff.id(), idempotencyKey);
        if (requests.eventExists(dedupeKey)) return views.get(staff, requestId);
        requests.requireVersion(locked, request.version());
        requirePendingAndUnexpired(locked);

        long limit = Math.abs(jdbc.queryForObject(
                "select difference_krw from reservation_change_quote where id = ?",
                Long.class, locked.currentQuoteId()));
        requests.insertApproval(requestId, locked.currentQuoteId(), staff, "REJECTED",
                locked.settlementDirection(), limit, request.reason().trim());
        jdbc.update("""
                update reservation_change_request
                set status = 'REJECTED', version = version + 1, updated_at = ?
                where id = ?
                """, Timestamp.from(clock.instant()), requestId);
        requests.insertEvent(requestId, "REQUEST_REJECTED", locked.status(), "REJECTED",
                staff.id(), dedupeKey, request.reason().trim());
        return views.get(staff, requestId);
    }

    private void requirePendingAndUnexpired(ReservationChangeRequestService.LockedRequest request) {
        if (!"PENDING_APPROVAL".equals(request.status())) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_STATE_CONFLICT", "승인 대기 중인 요청만 처리할 수 있습니다.");
        }
        if (!clock.instant().isBefore(request.approvalExpiresAt())) {
            throw new BusinessConflictException(
                    "RESERVATION_CHANGE_APPROVAL_EXPIRED", "승인 요청이 만료되었습니다.");
        }
    }

    private void requireMutation(String idempotencyKey, long version) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        if (version < 0) throw new IllegalArgumentException("올바른 요청 version이 필요합니다.");
    }
}
