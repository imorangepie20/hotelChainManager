package team.hotelchain.hotel;

import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 지점의 판매를 중지·재개한다.
 * <p>
 * 판매 중지는 고객 {@code GET /api/hotels}·{@code GET /api/availability}에서 지점을
 * 빼는 것이다. **이미 확정된 예약은 그대로 둔다.** 중지가 예약을 취소하면 본사가
 * 고객에게 알리지 않은 채 환불 의무가 생기므로, 중지는 신규 판매에만 적용한다.
 * 취소는 전용 API가 있다.
 * <p>
 * 멱원은 두 갈래로 동작한다. 같은 {@code Idempotency-Key} 재호출은 200에
 * {@code changed=false}로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을
 * 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다.
 */
@Service
public class HotelActivationService {

    static final String KIND_ACTIVATE = "ACTIVATE";
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StaffAccessService access;

    public HotelActivationService(JdbcTemplate jdbc, Clock clock, StaffAccessService access) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
    }

    @Transactional
    public HotelActivationResponse setActive(String token, UUID hotelId, String idempotencyKey,
            HotelActivationRequest input) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        if (input == null || input.active() == null) {
            throw new IllegalArgumentException("판매 상태를 입력해 주세요.");
        }

        ExistingHotel existing = loadHotel(hotelId);
        if (existing == null) {
            throw new HotelNotFoundException(hotelId);
        }

        // 동시 전환을 직렬화한다. 두 본사 관리자가 같은 지점을 동시에 바꾸면
        // 한 쪽의 잠금이 풀릴 때까지 다른 쪽이 기다린다.
        jdbc.query("select id from hotel where id = ? for update", rs -> { }, hotelId);

        String requestHash = requestHash(staff.id(), hotelId, input.active());
        UUID handled = findHandled(hotelId, staff.id(), idempotencyKey, requestHash);
        if (handled != null) {
            return response(handled, false);
        }

        // 이미 같은 상태면 값을 바꾸지 않고 멱원 기록만 남겨서
        // 재시도가 200으로 같은 결과를 돌려주게 한다.
        boolean changed = existing.active() != input.active();
        if (changed) {
            jdbc.update("update hotel set active = ? where id = ?", input.active(), hotelId);
        }

        jdbc.update("""
                insert into hotel_command (id, staff_id, hotel_id, kind, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staff.id(), hotelId, KIND_ACTIVATE, idempotencyKey, requestHash,
                java.sql.Timestamp.from(clock.instant()));

        return response(hotelId, changed);
    }

    private HotelActivationResponse response(UUID hotelId, boolean changed) {
        return jdbc.query("""
                select id, name, region, timezone, active from hotel where id = ?
                """, rs -> rs.next() ? new HotelActivationResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("region"),
                        rs.getString("timezone"),
                        rs.getBoolean("active"),
                        changed) : null, hotelId);
    }

    private ExistingHotel loadHotel(UUID hotelId) {
        return jdbc.query("""
                select id, name, region, timezone, active from hotel where id = ?
                """, rs -> rs.next() ? new ExistingHotel(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("region"),
                        rs.getString("timezone"),
                        rs.getBoolean("active")) : null, hotelId);
    }

    private UUID findHandled(UUID hotelId, UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 지점을 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select hotel_id from hotel_command
                 where hotel_id = ? and staff_id = ? and kind = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("hotel_id", UUID.class) : null,
                hotelId, staffId, KIND_ACTIVATE, idempotencyKey);
        if (byKey != null) {
            return byKey;
        }
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        UUID byHash = jdbc.query("""
                select hotel_id from hotel_command
                 where hotel_id = ? and staff_id = ? and kind = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("hotel_id", UUID.class) : null,
                hotelId, staffId, KIND_ACTIVATE, requestHash);
        return byHash;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, boolean active) {
        String payload = staffId + "|" + hotelId + "|" + active;
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ExistingHotel(UUID id, String name, String region, String timezone, boolean active) {
    }
}
