package team.hotelchain.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 새 지점을 만든다.
 * <p>
 * 지점 행만 만들고 객실 유형·요금·재고는 심지 않는다. 빈 지점은 고객 검색 결과에
 * 객실을 내놓지 않으므로, 본사가 {@code POST /api/staff/hotels/{hotelId}/room-types}로
 * 유형을 추가할 때 시드가 함께 만들어진다. 두 동작을 분리하면 지점 생성이
 * 요금·재고 설정 없이도 가능하고, 시드 실패가 지점 생성을 막지 않는다.
 * <p>
 * 멱원은 두 갈래로 동작한다. 같은 {@code Idempotency-Key} 재호출은 200에
 * {@code created=false}로 같은 지점을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을
 * 보내도 {@code staff_id}·이름·지역·시간대의 SHA-256 지문이 같아 같은 결과를
 * 돌려준다.
 * <p>
 * 지점 삭제는 이 범위가 아니다. {@code room_type}·{@code reservation}·
 * {@code reservation_change_request}·{@code website_page_room_type}·
 * {@code staff_member} 외래키가 걸려 있어 안전한 삭제 조건을 따로 설계해야 한다.
 */
@Service
public class HotelCommandService {

    static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
    static final String KIND_CREATE = "CREATE";
    private static final int NAME_MAX_LENGTH = 100;
    private static final int REGION_MAX_LENGTH = 50;
    private static final int TIMEZONE_MAX_LENGTH = 50;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StaffAccessService access;

    public HotelCommandService(JdbcTemplate jdbc, Clock clock, StaffAccessService access) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
    }

    @Transactional
    public HotelCreateResponse create(String token, String idempotencyKey, HotelCreateRequest input) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        requireInput(input);

        String name = input.normalizedName();
        String region = input.normalizedRegion();
        String timezone = input.timezone().trim();

        // 지점은 체인 전체에 하나뿐이므로 직렬화 범위가 다른 쓰기 동작보다 넓다.
        // 이름 중복 검사와 insert 사이에 다른 트랜잭션이 끼어드는 것을 막는다.
        jdbc.query("select id from hotel where lower(name) = lower(?) for update", rs -> { }, name);

        String requestHash = requestHash(staff.id(), input);
        Existing handled = findExisting(staff.id(), idempotencyKey, requestHash);
        if (handled != null) {
            return new HotelCreateResponse(handled.hotelId().toString(), name, region, timezone, roomTypeCount(handled.hotelId()), false);
        }

        if (nameExists(name)) {
            throw new HotelNameConflictException(name);
        }

        UUID hotelId = UUID.randomUUID();
        try {
            jdbc.update("insert into hotel (id, name, region, timezone) values (?, ?, ?, ?)",
                    hotelId, name, region, timezone);
        } catch (DataIntegrityViolationException conflict) {
            // 동시에 같은 내용이 만들어졌으면 이미 저장된 지점을 돌려준다.
            Existing replay = findExisting(staff.id(), idempotencyKey, requestHash);
            if (replay != null) {
                return new HotelCreateResponse(
                        replay.hotelId().toString(), name, region, timezone, roomTypeCount(replay.hotelId()), false);
            }
            throw conflict;
        }

        jdbc.update("""
                insert into hotel_command (id, staff_id, hotel_id, kind, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staff.id(), hotelId, KIND_CREATE, idempotencyKey, requestHash,
                java.sql.Timestamp.from(clock.instant()));

        return new HotelCreateResponse(hotelId.toString(), name, region, timezone, 0, true);
    }

    // 빈 지점은 객실 유형이 없다. 카탈로그 본문과 같은 SELECT만 사용한다.
    int roomTypeCount(UUID hotelId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from room_type where hotel_id = ?", Integer.class, hotelId);
        return count == null ? 0 : count;
    }

    private boolean nameExists(String name) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where lower(name) = lower(?)", Integer.class, name);
        return count != null && count > 0;
    }

    private Existing findExisting(UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 지점을 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select hotel_id from hotel_command
                 where staff_id = ? and kind = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("hotel_id", UUID.class) : null,
                staffId, KIND_CREATE, idempotencyKey);
        if (byKey != null) {
            return new Existing(byKey);
        }
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        UUID byHash = jdbc.query("""
                select hotel_id from hotel_command
                 where staff_id = ? and kind = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("hotel_id", UUID.class) : null,
                staffId, KIND_CREATE, requestHash);
        return byHash == null ? null : new Existing(byHash);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (isBlank(idempotencyKey) || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private void requireInput(HotelCreateRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("지점 정보를 입력해 주세요.");
        }
        String name = input.normalizedName();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("지점 이름을 입력해 주세요.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("지점 이름이 너무 깁니다. 최대 " + NAME_MAX_LENGTH + "자입니다.");
        }
        String region = input.normalizedRegion();
        if (region.isEmpty()) {
            throw new IllegalArgumentException("지역을 입력해 주세요.");
        }
        if (region.length() > REGION_MAX_LENGTH) {
            throw new IllegalArgumentException("지역이 너무 깁니다. 최대 " + REGION_MAX_LENGTH + "자입니다.");
        }
        if (input.timezone() == null || isBlank(input.timezone())) {
            throw new IllegalArgumentException("시간대를 입력해 주세요.");
        }
        if (input.timezone().length() > TIMEZONE_MAX_LENGTH) {
            throw new IllegalArgumentException("시간대가 너무 깁니다. 최대 " + TIMEZONE_MAX_LENGTH + "자입니다.");
        }
        // 잘못된 시간대는 재고 시드와 취소 마감 시각이 엉뚱한 날짜를 가리킨다.
        // 잘못된 값을 저장하고 나중에 고치면 이미 만든 객실 유형의 시드 시작일이 틀어진다.
        if (!input.isValidTimezone()) {
            throw new IllegalArgumentException("알 수 없는 시간대입니다. 예: Asia/Seoul");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String requestHash(UUID staffId, HotelCreateRequest input) {
        String payload = String.join("|",
                staffId.toString(),
                input.normalizedName(),
                input.normalizedRegion(),
                input.timezone().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Existing(UUID hotelId) {
    }
}
