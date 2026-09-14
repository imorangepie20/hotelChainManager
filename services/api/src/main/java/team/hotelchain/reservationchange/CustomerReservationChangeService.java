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
import team.hotelchain.reservation.ReservationService;
import team.hotelchain.reservation.ReservationView;
import team.hotelchain.reservationchange.ReservationStayQuote.SelectedStayOffer;

@Service
public class CustomerReservationChangeService {
    private final JdbcTemplate jdbc;
    private final ReservationService reservations;
    private final ReservationAccess access;
    private final ReservationStayQuoteService quotes;
    private final ReservationChangeSettlementService settlements;
    private final ReservationChangeHoldService holds;
    private final ReservationChangePolicy policy;
    private final Clock clock;

    public CustomerReservationChangeService(JdbcTemplate jdbc, ReservationService reservations,
            ReservationAccess access, ReservationStayQuoteService quotes,
            ReservationChangeSettlementService settlements, ReservationChangeHoldService holds,
            ReservationChangePolicy policy, Clock clock) {
        this.jdbc = jdbc;
        this.reservations = reservations;
        this.access = access;
        this.quotes = quotes;
        this.settlements = settlements;
        this.holds = holds;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CustomerReservationChangeEligibility eligibility(String token, UUID reservationId) {
        reservations.get(reservationId, token);
        EligibilityRow row = jdbc.query("""
                select r.status, r.check_in, h.timezone,
                       exists(select 1 from reservation_room_assignment a where a.reservation_id=r.id) assigned,
                       exists(select 1 from reservation_change_request c where c.reservation_id=r.id
                         and c.status not in ('COMPLETED','REJECTED','CANCELLED','EXPIRED')) active
                from reservation r join room_type rt on rt.id=r.room_type_id
                join hotel h on h.id=rt.hotel_id where r.id=?
                """, rs -> rs.next() ? new EligibilityRow(rs.getString("status"),
                        rs.getDate("check_in").toLocalDate(), rs.getString("timezone"),
                        rs.getBoolean("assigned"), rs.getBoolean("active")) : null, reservationId);
        if (row == null) throw new ReservationNotFoundException();
        String reason = !"CONFIRMED".equals(row.status()) ? "RESERVATION_NOT_CONFIRMED"
                : row.assigned() ? "ROOM_ALREADY_ASSIGNED"
                : row.active() ? "RESERVATION_CHANGE_ACTIVE"
                : !LocalDate.now(clock.withZone(ZoneId.of(row.timezone()))).isBefore(row.checkIn())
                    ? "RESERVATION_STAY_CHANGE_TOO_LATE" : null;
        return new CustomerReservationChangeEligibility(reason == null, reason);
    }

    @Transactional
    public CustomerReservationChangeQuoteView quote(String token, UUID reservationId,
            CustomerReservationChangeQuoteRequest input) {
        reservations.get(reservationId, token);
        if (input == null || input.checkIn() == null || input.checkOut() == null
                || input.adults() < 1 || input.children() < 0) {
            throw new IllegalArgumentException("변경할 일정과 투숙 인원을 확인해 주세요.");
        }
        requireEligible(eligibility(token, reservationId));
        ReservationStayQuote quote = quotes.quote(reservationId, input.checkIn(), input.checkOut(),
                input.adults(), input.children(), false);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(quote.timezone())));
        if (!today.isBefore(input.checkIn())) {
            throw new BusinessConflictException("RESERVATION_STAY_CHANGE_TOO_LATE", "체크인 당일에는 예약을 변경할 수 없습니다.");
        }
        UUID quoteId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(policy.holdTtl());
        jdbc.update("""
                insert into reservation_change_customer_quote
                    (id,reservation_id,base_operation_revision,check_in,check_out,adults,children,expires_at)
                values (?,?,?,?,?,?,?,?)
                """, quoteId, reservationId, quote.operationRevision(), input.checkIn(), input.checkOut(),
                input.adults(), input.children(), Timestamp.from(expiresAt));
        List<CustomerReservationChangeQuoteView.Offer> offers = quote.offers().stream().map(offer ->
                new CustomerReservationChangeQuoteView.Offer(offer.roomTypeId(), offer.roomTypeName(),
                        offer.ratePlanId(), offer.ratePlanName(), offer.breakfastIncluded(), offer.remaining(),
                        offer.nightlyPrices(), offer.totalKrw(), offer.differenceKrw(), offer.currency())).toList();
        return new CustomerReservationChangeQuoteView(quoteId, quote.operationRevision(), expiresAt,
                quote.targetCheckIn(), quote.targetCheckOut(), quote.adults(), quote.children(), quote.rooms(),
                quote.previousTotalKrw(), quote.currency(), offers);
    }

    @Transactional
    public StartResult create(String token, UUID reservationId, String idempotencyKey,
            CustomerReservationChangeRequest input) {
        ReservationView current = reservations.get(reservationId, token);
        validateCreate(idempotencyKey, input);
        CustomerQuote saved = lockQuote(input.quoteId(), reservationId);
        String hash = requestHash(reservationId, saved, input);
        Existing existing = findExisting(reservationId, idempotencyKey);
        if (existing != null) {
            if (!existing.hash().equals(hash)) {
                throw new BusinessConflictException("IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 변경 요청이 사용되었습니다.");
            }
            return new StartResult(view(existing.id()),
                    settlements.resumeCustomerSession(reservationId, existing.id(), token));
        }
        if (!saved.expiresAt().isAfter(clock.instant())) {
            throw new BusinessConflictException("CHANGE_QUOTE_EXPIRED", "변경 견적이 만료되었습니다. 다시 조회해 주세요.");
        }
        ReservationStayQuote latest = quotes.quote(reservationId, saved.checkIn(), saved.checkOut(),
                saved.adults(), saved.children(), true);
        if (!"CONFIRMED".equals(latest.reservationStatus())) {
            throw new BusinessConflictException("RESERVATION_NOT_CONFIRMED", "확정된 예약만 변경할 수 있습니다.");
        }
        if (latest.assignments() > 0) {
            throw new BusinessConflictException("ROOM_ALREADY_ASSIGNED", "객실 배정 후에는 예약을 변경할 수 없습니다.");
        }
        if (!LocalDate.now(clock.withZone(ZoneId.of(latest.timezone()))).isBefore(latest.previousCheckIn())) {
            throw new BusinessConflictException("RESERVATION_STAY_CHANGE_TOO_LATE", "체크인 당일에는 예약을 변경할 수 없습니다.");
        }
        if (latest.operationRevision() != saved.baseOperationRevision()) {
            throw new BusinessConflictException("RESERVATION_REVISION_CONFLICT", "예약 조건이 변경되었습니다. 다시 조회해 주세요.");
        }
        SelectedStayOffer offer = latest.selected(input.roomTypeId(), input.ratePlanId());
        if (offer.totalKrw() != input.expectedTotal()) {
            throw new BusinessConflictException("PRICE_CHANGED", "요금이 변경되었습니다. 새 금액을 확인해 주세요.");
        }
        if (latest.roomTypeId().equals(input.roomTypeId()) && latest.ratePlanId().equals(input.ratePlanId())
                && latest.previousCheckIn().equals(saved.checkIn()) && latest.previousCheckOut().equals(saved.checkOut())
                && current.adults() == saved.adults() && current.children() == saved.children()) {
            throw new BusinessConflictException("RESERVATION_STAY_UNCHANGED", "변경할 조건을 선택해 주세요.");
        }
        UUID requestId = UUID.randomUUID();
        UUID changeQuoteId = UUID.randomUUID();
        String direction = offer.differenceKrw() > 0 ? "CHARGE" : offer.differenceKrw() < 0 ? "REFUND" : "NONE";
        try {
            jdbc.update("""
                    insert into reservation_change_request (
                        id,reservation_id,hotel_id,base_operation_revision,status,settlement_direction,
                        requested_by,request_origin,idempotency_key,request_hash,
                        previous_check_in,previous_check_out,previous_room_type_id,previous_rate_plan_id,
                        target_check_in,target_check_out,target_room_type_id,target_rate_plan_id,
                        rooms,adults,children,approval_limit_krw,approval_expires_at)
                    values (?,?,?,?,? ,?,?,?, ?,?, ?,?,?,?, ?,?,?,?, ?,?,?,?,?)
                    """, requestId, reservationId, latest.hotelId(), latest.operationRevision(), "APPROVED", direction,
                    null, "CUSTOMER", idempotencyKey, hash, latest.previousCheckIn(), latest.previousCheckOut(),
                    latest.roomTypeId(), latest.ratePlanId(), saved.checkIn(), saved.checkOut(), input.roomTypeId(),
                    input.ratePlanId(), latest.rooms(), saved.adults(), saved.children(), Math.abs(offer.differenceKrw()),
                    Timestamp.from(saved.expiresAt()));
        } catch (DataIntegrityViolationException conflict) {
            Existing replay = findExisting(reservationId, idempotencyKey);
            if (replay != null && replay.hash().equals(hash)) return new StartResult(view(replay.id()),
                    settlements.resumeCustomerSession(reservationId, replay.id(), token));
            throw new BusinessConflictException("RESERVATION_CHANGE_ACTIVE", "진행 중인 예약 변경을 먼저 처리해 주세요.");
        }
        insertQuote(requestId, changeQuoteId, latest, offer);
        jdbc.update("update reservation_change_request set current_quote_id=? where id=?", changeQuoteId, requestId);
        insertEvent(requestId, "CUSTOMER_REQUEST_CREATED", null, "APPROVED", "customer-request:" + requestId);
        ReservationChangeSettlementService.CustomerSettlementResult settlement =
                settlements.startCustomerSettlement(reservationId, requestId, token, idempotencyKey + ":settle");
        return new StartResult(view(requestId), settlement.sessionToken());
    }

    @Transactional
    public CustomerReservationChangeStartView cancel(String token, UUID reservationId, UUID requestId,
            String idempotencyKey) {
        reservations.get(reservationId, token);
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key가 필요합니다.");
        RequestRow row = jdbc.query("select id,reservation_id,status,settlement_direction,version,settlement_expires_at,request_origin from reservation_change_request where id=? for update",
                rs -> rs.next() ? mapRequest(rs) : null, requestId);
        if (row == null || !row.reservationId().equals(reservationId)) throw new ReservationNotFoundException();
        if (!"CUSTOMER".equals(row.origin())) throw new ReservationNotFoundException();
        if ("CANCELLED".equals(row.status())) return view(requestId);
        if (!List.of("APPROVED", "AWAITING_PAYMENT").contains(row.status())) {
            throw new BusinessConflictException("RESERVATION_CHANGE_STATE_CONFLICT", "현재 변경 요청은 취소할 수 없습니다.");
        }
        if ("AWAITING_PAYMENT".equals(row.status())) {
            Boolean started = jdbc.queryForObject("""
                    select exists(select 1 from payment_adjustment_attempt
                      where request_id=? and (checkout_started_at is not null or status in ('SUCCEEDED','UNKNOWN')))
                    """, Boolean.class, requestId);
            if (Boolean.TRUE.equals(started)) {
                throw new BusinessConflictException("RESERVATION_CHANGE_STATE_CONFLICT", "결제가 시작된 변경 요청은 취소할 수 없습니다.");
            }
            jdbc.update("update reservation_change_outbox set status='FAILED',last_error='CUSTOMER_CANCELLED',updated_at=? where request_id=? and status in ('PENDING','PROCESSING')",
                    Timestamp.from(clock.instant()), requestId);
            jdbc.update("update payment_adjustment_attempt set status='FAILED',public_token_hash=null,error_code='CUSTOMER_CANCELLED',updated_at=? where request_id=? and status in ('NEW','PROCESSING')",
                    Timestamp.from(clock.instant()), requestId);
            jdbc.update("delete from reservation_change_customer_session where request_id=?", requestId);
        }
        jdbc.update("update reservation_change_request set status='CANCELLED',version=version+1,updated_at=? where id=?",
                Timestamp.from(clock.instant()), requestId);
        holds.release(requestId, "CUSTOMER_CANCELLED");
        insertEvent(requestId, "CUSTOMER_REQUEST_CANCELLED", row.status(), "CANCELLED",
                "customer-cancel:" + requestId + ":" + idempotencyKey);
        return view(requestId);
    }

    private void requireEligible(CustomerReservationChangeEligibility eligibility) {
        if (!eligibility.allowed()) throw new BusinessConflictException(eligibility.reasonCode(), "현재 예약은 변경할 수 없습니다.");
    }
    private void validateCreate(String key, CustomerReservationChangeRequest input) {
        if (key == null || key.isBlank() || key.length() > 100 || input == null || input.quoteId() == null
                || input.roomTypeId() == null || input.ratePlanId() == null || input.expectedTotal() == null
                || input.expectedTotal() < 0) throw new IllegalArgumentException("변경 견적과 Idempotency-Key를 확인해 주세요.");
    }
    private String requestHash(UUID reservationId, CustomerQuote saved, CustomerReservationChangeRequest input) {
        return access.sha256(String.join(":", reservationId.toString(), saved.checkIn().toString(),
                saved.checkOut().toString(), Integer.toString(saved.adults()), Integer.toString(saved.children()),
                input.roomTypeId().toString(), input.ratePlanId().toString(), Long.toString(input.expectedTotal()))
                .getBytes(StandardCharsets.UTF_8));
    }
    private CustomerQuote lockQuote(UUID quoteId, UUID reservationId) {
        CustomerQuote value = jdbc.query("select base_operation_revision,check_in,check_out,adults,children,expires_at from reservation_change_customer_quote where id=? and reservation_id=? for update",
                rs -> rs.next() ? new CustomerQuote(rs.getLong(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(),
                        rs.getInt(4), rs.getInt(5), rs.getTimestamp(6).toInstant()) : null, quoteId, reservationId);
        if (value == null) throw new ReservationNotFoundException();
        return value;
    }
    private void insertQuote(UUID requestId, UUID quoteId, ReservationStayQuote quote, SelectedStayOffer offer) {
        jdbc.update("insert into reservation_change_quote(id,request_id,revision,previous_total_krw,total_krw,difference_krw,currency,rooms) values (?,?,?,?,?,?,?,?)",
                quoteId, requestId, 1, quote.previousTotalKrw(), offer.totalKrw(), offer.differenceKrw(), offer.currency(), quote.rooms());
        offer.nightlyPrices().forEach(night -> jdbc.update(
                "insert into reservation_change_quote_night(quote_id,stay_date,amount_krw) values (?,?,?)",
                quoteId, night.date(), night.amount()));
    }
    private void insertEvent(UUID id, String type, String from, String to, String dedupe) {
        jdbc.update("insert into reservation_change_event(id,request_id,event_type,from_status,to_status,dedupe_key,payload) values (?,?,?,?,?,?,jsonb_build_object('actor','CUSTOMER'))",
                UUID.randomUUID(), id, type, from, to, dedupe);
    }
    private Existing findExisting(UUID reservationId, String key) {
        return jdbc.query("select id,request_hash from reservation_change_request where reservation_id=? and idempotency_key=?",
                rs -> rs.next() ? new Existing(rs.getObject(1, UUID.class), rs.getString(2)) : null, reservationId, key);
    }
    private CustomerReservationChangeStartView view(UUID requestId) {
        RequestRow row = jdbc.query("select id,reservation_id,status,settlement_direction,version,coalesce(settlement_expires_at,approval_expires_at),request_origin from reservation_change_request where id=?",
                rs -> rs.next() ? mapRequest(rs) : null, requestId);
        if (row == null) throw new ReservationNotFoundException();
        return new CustomerReservationChangeStartView(row.id(), row.status(), row.direction(), row.version(), row.expiresAt());
    }
    private RequestRow mapRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp expiresAt = rs.getTimestamp(6);
        return new RequestRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getLong(5), expiresAt == null ? null : expiresAt.toInstant(), rs.getString(7));
    }

    public record StartResult(CustomerReservationChangeStartView view, String sessionToken) {}
    private record CustomerQuote(long baseOperationRevision, LocalDate checkIn, LocalDate checkOut,
            int adults, int children, Instant expiresAt) {}
    private record Existing(UUID id, String hash) {}
    private record RequestRow(UUID id, UUID reservationId, String status, String direction, long version,
            Instant expiresAt, String origin) {}
    private record EligibilityRow(String status, LocalDate checkIn, String timezone, boolean assigned, boolean active) {}
}
