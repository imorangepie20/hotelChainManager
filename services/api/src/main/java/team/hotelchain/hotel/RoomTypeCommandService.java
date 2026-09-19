package team.hotelchain.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 객실 유형을 추가한다.
 * <p>
 * 가격·재고·예약 확정에 닿는 첫 쓰기 동작이므로 권한은 {@link StaffAccessService}가 검증하고,
 * 같은 요청의 중복 생성은 {@code room_type_command}의 멱원 키와 요청 지문으로 막는다.
 */
@Service
public class RoomTypeCommandService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;

    public RoomTypeCommandService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public RoomTypeCreateResponse create(String token, UUID hotelId, String idempotencyKey, RoomTypeCreateRequest request) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        if (!hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        // 지점 단위로 쓰기를 직렬화해 같은 내용의 동시 재시도가 한 쪽만 저장되게 한다.
        jdbc.query("select id from hotel where id = ? for update", rs -> { }, hotelId);

        String requestHash = requestHash(staff.id(), hotelId, request);
        UUID existing = findRoomTypeId(hotelId, staff.id(), idempotencyKey, requestHash);
        if (existing != null) {
            return loadExisting(existing, hotelId);
        }

        UUID roomTypeId = UUID.randomUUID();
        jdbc.update("insert into room_type (id, hotel_id, name, max_occupancy) values (?, ?, ?, ?)",
                roomTypeId, hotelId, request.name().trim(), request.maxOccupancy());
        jdbc.update("""
                insert into room_type_command
                    (id, hotel_id, staff_id, room_type_id, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hotelId, staff.id(), roomTypeId, idempotencyKey, requestHash,
                java.sql.Timestamp.from(clock.instant()));

        return new RoomTypeCreateResponse(roomTypeId, hotelId, request.name().trim(), request.maxOccupancy(), true);
    }

    private UUID findRoomTypeId(UUID hotelId, UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 객실 유형을 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select room_type_id from room_type_command
                 where hotel_id = ? and staff_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                hotelId, staffId, idempotencyKey);
        if (byKey != null) return byKey;
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        return jdbc.query("""
                select room_type_id from room_type_command
                 where hotel_id = ? and staff_id = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("room_type_id", UUID.class) : null,
                hotelId, staffId, requestHash);
    }

    private RoomTypeCreateResponse loadExisting(UUID roomTypeId, UUID hotelId) {
        return jdbc.query("""
                select id, name, max_occupancy from room_type where id = ? and hotel_id = ?
                """, rs -> rs.next() ? new RoomTypeCreateResponse(
                        rs.getObject("id", UUID.class), hotelId,
                        rs.getString("name"), rs.getInt("max_occupancy"), false) : null,
                roomTypeId, hotelId);
    }

    private boolean hotelExists(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, UUID hotelId, RoomTypeCreateRequest request) {
        String payload = staffId + "|" + hotelId + "|" + request.name().trim() + "|" + request.maxOccupancy();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
