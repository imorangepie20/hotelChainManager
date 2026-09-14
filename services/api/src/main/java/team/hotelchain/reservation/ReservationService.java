package team.hotelchain.reservation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final JdbcTemplate jdbc;
    private final ReservationAccess access;
    private final Clock clock;
    private final Duration holdTtl;

    public ReservationService(
            JdbcTemplate jdbc,
            ReservationAccess access,
            Clock clock,
            @Value("${reservation.hold-ttl:10m}") Duration holdTtl) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.holdTtl = holdTtl;
    }

    @Transactional
    public ReservationView create(String idempotencyKey, String managementToken, ReservationRequest request) {
        validate(request, idempotencyKey);
        String tokenHash = access.hashToken(managementToken);
        String requestHash = fingerprint(request);

        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0)) IS NULL", Boolean.class,
                tokenHash + ":" + idempotencyKey);

        ExistingRequest existing = findExisting(tokenHash, idempotencyKey);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessConflictException("IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 예약 내용이 사용되었습니다.");
            }
            return getAuthorized(existing.reservationId(), tokenHash);
        }

        RatePlanInfo plan = jdbc.query("""
                SELECT rt.max_occupancy, rp.policy_version
                  FROM room_type rt
                  JOIN rate_plan rp ON rp.room_type_id = rt.id
                 WHERE rt.id = ? AND rp.id = ?
                """, rs -> rs.next() ? new RatePlanInfo(rs.getInt(1), rs.getString(2)) : null,
                request.roomTypeId(), request.ratePlanId());
        if (plan == null || plan.maxOccupancy() * request.rooms() < request.adults() + request.children()) {
            throw new BusinessConflictException("SOLD_OUT", "선택한 객실을 현재 예약할 수 없습니다.");
        }

        List<InventoryNight> nights = jdbc.query("""
                SELECT i.stay_date, i.capacity, i.held, i.confirmed, rd.amount_krw
                  FROM inventory_day i
                  LEFT JOIN rate_day rd ON rd.rate_plan_id = ? AND rd.stay_date = i.stay_date
                 WHERE i.room_type_id = ? AND i.stay_date >= ? AND i.stay_date < ?
                 ORDER BY i.stay_date
                 FOR UPDATE OF i
                """, this::mapInventoryNight, request.ratePlanId(), request.roomTypeId(), request.checkIn(), request.checkOut());

        int expectedNights = Math.toIntExact(ChronoUnit.DAYS.between(request.checkIn(), request.checkOut()));
        if (nights.size() != expectedNights || nights.stream().anyMatch(night -> night.amount() == null)
                || nights.stream().anyMatch(night -> night.capacity() - night.held() - night.confirmed() < request.rooms())) {
            throw new BusinessConflictException("SOLD_OUT", "선택한 객실의 재고가 부족합니다.");
        }

        long actualTotal = nights.stream().mapToLong(InventoryNight::amount).sum() * request.rooms();
        if (actualTotal != request.expectedTotal()) {
            throw new BusinessConflictException("PRICE_CHANGED", "요금이 변경되었습니다. 최신 요금을 확인해 주세요.");
        }

        UUID reservationId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(holdTtl);
        String policySnapshot = "{\"version\":\"" + plan.policyVersion().replace("\"", "")
                + "\",\"timezone\":\"Asia/Seoul\",\"refundCutoffDaysBefore\":1,"
                + "\"refundCutoffLocalTime\":\"18:00\"}";
        jdbc.update("""
                INSERT INTO reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?, 'KRW', ?, ?, ?, ?, CAST(? AS jsonb))
                """, reservationId, request.roomTypeId(), request.ratePlanId(), request.checkIn(), request.checkOut(),
                request.adults(), request.children(), request.rooms(), actualTotal, Timestamp.from(expiresAt),
                request.guest().name().trim(), request.guest().email().trim(), tokenHash, policySnapshot);

        for (InventoryNight night : nights) {
            jdbc.update("insert into reservation_night values (?, ?, ?)", reservationId, night.date(), night.amount());
            jdbc.update("update inventory_day set held = held + ? where room_type_id = ? and stay_date = ?",
                    request.rooms(), request.roomTypeId(), night.date());
        }
        jdbc.update("insert into reservation_idempotency (management_token_hash, idempotency_key, request_hash, reservation_id) values (?, ?, ?, ?)",
                tokenHash, idempotencyKey, requestHash, reservationId);
        return getAuthorized(reservationId, tokenHash);
    }

    @Transactional(readOnly = true)
    public ReservationView get(UUID reservationId, String managementToken) {
        return getAuthorized(reservationId, access.hashToken(managementToken));
    }

    private ReservationView getAuthorized(UUID reservationId, String tokenHash) {
        ReservationRow row = jdbc.query("""
                SELECT id, status, check_in, check_out, rooms, expires_at, total_krw, currency,
                       policy_snapshot::text, guest_name, guest_email
                  FROM reservation
                 WHERE id = ? AND management_token_hash = ?
                """, rs -> rs.next() ? mapReservation(rs) : null, reservationId, tokenHash);
        if (row == null) {
            throw new ReservationNotFoundException();
        }
        List<ReservationNight> nights = jdbc.query("""
                SELECT stay_date, amount_krw FROM reservation_night
                 WHERE reservation_id = ? ORDER BY stay_date
                """, (rs, index) -> new ReservationNight(rs.getDate(1).toLocalDate(), rs.getInt(2)), reservationId);
        var details = jdbc.queryForObject("""
                select rt.name room_type_name, rp.name rate_plan_name, r.adults, r.children,
                  coalesce((r.policy_snapshot->>'refundCutoffDaysBefore')::integer, 1) cutoff_days,
                  coalesce(r.policy_snapshot->>'refundCutoffLocalTime', '18:00') cutoff_time,
                  coalesce(r.policy_snapshot->>'timezone', 'Asia/Seoul') timezone,
                  case when exists(select 1 from payment_transaction t where t.reservation_id=r.id)
                    then 'SUCCEEDED'
                    else coalesce((select p.status from payment_provider_attempt p where p.reservation_id=r.id
                      order by p.created_at desc limit 1),
                      (select p.payment_status from payment_attempt p where p.reservation_id=r.id
                      order by p.created_at desc limit 1), 'NOT_STARTED') end payment_status
                from reservation r join room_type rt on rt.id=r.room_type_id
                join rate_plan rp on rp.id=r.rate_plan_id where r.id=?
                """, (rs, n) -> new DisplayDetails(rs.getString("room_type_name"), rs.getString("rate_plan_name"),
                    rs.getInt("adults"), rs.getInt("children"), rs.getString("payment_status"),
                    new CancellationPolicyDetails(rs.getInt("cutoff_days"), rs.getString("cutoff_time"), rs.getString("timezone"))), reservationId);
        return new ReservationView(row.id(), row.status(), row.checkIn(), row.checkOut(), row.rooms(),
                row.expiresAt(), row.total(), row.currency(), nights, row.policySnapshot(),
                new ReservationGuest(row.guestName(), row.guestEmail()), details.roomTypeName(), details.ratePlanName(),
                details.adults(), details.children(), details.paymentStatus(), details.policy());
    }

    private ExistingRequest findExisting(String tokenHash, String idempotencyKey) {
        return jdbc.query("""
                SELECT request_hash, reservation_id FROM reservation_idempotency
                 WHERE management_token_hash = ? AND idempotency_key = ?
                """, rs -> rs.next() ? new ExistingRequest(rs.getString(1), rs.getObject(2, UUID.class)) : null,
                tokenHash, idempotencyKey);
    }

    private void validate(ReservationRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
        if (request == null || request.roomTypeId() == null || request.ratePlanId() == null
                || request.checkIn() == null || request.checkOut() == null || request.guest() == null) {
            throw new IllegalArgumentException("예약 필수 정보를 확인해 주세요.");
        }
        if (!request.checkOut().isAfter(request.checkIn()) || request.adults() < 1 || request.children() < 0
                || request.rooms() < 1 || request.expectedTotal() < 0) {
            throw new IllegalArgumentException("숙박 날짜, 인원, 객실 수 또는 금액을 확인해 주세요.");
        }
        if (request.guest().name() == null || request.guest().name().isBlank()
                || request.guest().email() == null || request.guest().email().isBlank()) {
            throw new IllegalArgumentException("예약자 이름과 이메일은 필수입니다.");
        }
    }

    private String fingerprint(ReservationRequest request) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(buffer)) {
                data.writeUTF(request.roomTypeId().toString());
                data.writeUTF(request.ratePlanId().toString());
                data.writeUTF(request.checkIn().toString());
                data.writeUTF(request.checkOut().toString());
                data.writeInt(request.adults());
                data.writeInt(request.children());
                data.writeInt(request.rooms());
                data.writeLong(request.expectedTotal());
                data.writeUTF(request.guest().name().trim());
                data.writeUTF(request.guest().email().trim());
            }
            return access.sha256(buffer.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("예약 요청 해시를 생성할 수 없습니다.", exception);
        }
    }

    private InventoryNight mapInventoryNight(ResultSet rs, int rowNumber) throws SQLException {
        Integer amount = (Integer) rs.getObject("amount_krw");
        return new InventoryNight(rs.getDate("stay_date").toLocalDate(), rs.getInt("capacity"),
                rs.getInt("held"), rs.getInt("confirmed"), amount);
    }

    private ReservationRow mapReservation(ResultSet rs) throws SQLException {
        return new ReservationRow(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getDate("check_in").toLocalDate(), rs.getDate("check_out").toLocalDate(), rs.getInt("rooms"),
                rs.getTimestamp("expires_at").toInstant(), rs.getLong("total_krw"), rs.getString("currency").trim(),
                rs.getString("policy_snapshot"), rs.getString("guest_name"), rs.getString("guest_email"));
    }

    private record DisplayDetails(String roomTypeName, String ratePlanName, int adults, int children,
            String paymentStatus, CancellationPolicyDetails policy) {}

    private record InventoryNight(LocalDate date, int capacity, int held, int confirmed, Integer amount) {
    }

    private record RatePlanInfo(int maxOccupancy, String policyVersion) {
    }

    private record ExistingRequest(String requestHash, UUID reservationId) {
    }

    private record ReservationRow(UUID id, String status, LocalDate checkIn, LocalDate checkOut, int rooms,
            Instant expiresAt, long total, String currency, String policySnapshot, String guestName, String guestEmail) {
    }
}
