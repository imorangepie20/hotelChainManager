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
 * 본사가 지점의 이름·지역·시간대를 바꾼다.
 * <p>
 * 지점 수정은 본사가 오타를 바로잡거나 브랜드가 바뀌었을 때 필요하다. 생성 API가
 * 이름 중복을 409로 막고 있으므로, 수정 수단이 없으면 한 번 잘못 만든 지점 이름을
 * 고칠 방법이 없다.
 * <p>
 * 시간대는 재고 시드 시작일·취소 마감 시각·운영 상태 전환의 기준이지만, 이미 지나간
 * 날짜의 재고·예약을 다시 계산하지는 않는다. {@code inventory_day}·
 * {@code rate_day}·{@code reservation}은 날짜를 {@code LocalDate}로 저장하므로
 * 시간대를 바꿔도 행이 이동하지 않는다. 새 시간대는 그 이후의 시드·마감 판정에
 * 적용된다.
 * <p>
 * 멱원은 두 갈래로 동작한다. 같은 {@code Idempotency-Key} 재호출은 200에
 * {@code changed=false}로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을
 * 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다.
 */
@Service
public class HotelUpdateService {

    static final String KIND_UPDATE = "UPDATE";
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int REGION_MAX_LENGTH = 50;
    private static final int TIMEZONE_MAX_LENGTH = 50;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StaffAccessService access;
    private final HotelCommandService commands;

    public HotelUpdateService(JdbcTemplate jdbc, Clock clock, StaffAccessService access,
            HotelCommandService commands) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
        this.commands = commands;
    }

    @Transactional
    public HotelUpdateResponse update(String token, UUID hotelId, String idempotencyKey,
            HotelUpdateRequest input) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        requireInput(input);

        ExistingHotel existing = loadHotel(hotelId);
        if (existing == null) {
            throw new HotelNotFoundException(hotelId);
        }

        // 동시 수정을 직렬화한다. 두 본사 관리자가 같은 지점을 동시에 바꾸면
        // 한 쪽의 잠금이 풀릴 때까지 다른 쪽이 기다린다.
        jdbc.query("select id from hotel where id = ? for update", rs -> { }, hotelId);

        String requestHash = requestHash(staff.id(), hotelId, input);
        UUID handled = findHandled(hotelId, staff.id(), idempotencyKey, requestHash);
        if (handled != null) {
            return response(handled, false);
        }

        String newName = resolveName(input, existing);
        String newRegion = resolveRegion(input, existing);
        String newTimezone = resolveTimezone(input, existing);

        // 이름을 바꿀 때만 다른 지점과의 중복을 검사한다. 자기 자신은 제외한다.
        if (newName != null && !newName.equalsIgnoreCase(existing.name())
                && nameUsedByAnother(hotelId, newName)) {
            throw new HotelNameConflictException(newName);
        }

        // 실제로 바뀌는 값이 없으면 DB를 건드리지 않고 멱원 기록만 남긴다.
        // 재시도가 200으로 같은 결과를 돌려주게 한다.
        boolean changed = !newName.equals(existing.name())
                || !newRegion.equals(existing.region())
                || !newTimezone.equals(existing.timezone());

        if (changed) {
            jdbc.update("update hotel set name = ?, region = ?, timezone = ? where id = ?",
                    newName, newRegion, newTimezone, hotelId);
        }

        jdbc.update("""
                insert into hotel_command (id, staff_id, hotel_id, kind, idempotency_key, request_hash,
                                           previous_name, previous_region, previous_timezone, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staff.id(), hotelId, KIND_UPDATE, idempotencyKey, requestHash,
                existing.name(), existing.region(), existing.timezone(),
                java.sql.Timestamp.from(clock.instant()));

        return response(hotelId, changed);
    }

    private HotelUpdateResponse response(UUID hotelId, boolean changed) {
        return jdbc.query("""
                select id, name, region, timezone from hotel where id = ?
                """, rs -> rs.next() ? HotelUpdateResponse.of(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("region"),
                        rs.getString("timezone"),
                        commands.roomTypeCount(hotelId),
                        changed) : null, hotelId);
    }

    private ExistingHotel loadHotel(UUID hotelId) {
        return jdbc.query("""
                select id, name, region, timezone from hotel where id = ?
                """, rs -> rs.next() ? new ExistingHotel(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("region"),
                        rs.getString("timezone")) : null, hotelId);
    }

    private UUID findHandled(UUID hotelId, UUID staffId, String idempotencyKey, String requestHash) {
        // 같은 멱원 키로 이미 처리됐으면 저장된 지점을 그대로 돌려준다.
        UUID byKey = jdbc.query("""
                select hotel_id from hotel_command
                 where hotel_id = ? and staff_id = ? and kind = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("hotel_id", UUID.class) : null,
                hotelId, staffId, KIND_UPDATE, idempotencyKey);
        if (byKey != null) {
            return byKey;
        }
        // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
        UUID byHash = jdbc.query("""
                select hotel_id from hotel_command
                 where hotel_id = ? and staff_id = ? and kind = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("hotel_id", UUID.class) : null,
                hotelId, staffId, KIND_UPDATE, requestHash);
        return byHash;
    }

    private boolean nameUsedByAnother(UUID hotelId, String name) {
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where lower(name) = lower(?) and id <> ?",
                Integer.class, name, hotelId);
        return count != null && count > 0;
    }

    private String resolveName(HotelUpdateRequest input, ExistingHotel existing) {
        String value = input.normalizedName();
        return value == null || value.isEmpty() ? existing.name() : value;
    }

    private String resolveRegion(HotelUpdateRequest input, ExistingHotel existing) {
        String value = input.normalizedRegion();
        return value == null || value.isEmpty() ? existing.region() : value;
    }

    private String resolveTimezone(HotelUpdateRequest input, ExistingHotel existing) {
        String value = input.normalizedTimezone();
        return value == null || value.isEmpty() ? existing.timezone() : value;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (isBlank(idempotencyKey) || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private void requireInput(HotelUpdateRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("지점 정보를 입력해 주세요.");
        }
        // 본문이 비어 있으면 아무것도 바꾸지 않는다. 빈 PATCH가 지점을 초기화하지 않는다.
        if (!input.hasAnyField()) {
            throw new IllegalArgumentException("바꿀 지점 정보를 입력해 주세요.");
        }
        String name = input.normalizedName();
        if (name != null && !name.isEmpty()) {
            if (name.length() > NAME_MAX_LENGTH) {
                throw new IllegalArgumentException("지점 이름이 너무 깁니다. 최대 " + NAME_MAX_LENGTH + "자입니다.");
            }
        }
        String region = input.normalizedRegion();
        if (region != null && !region.isEmpty()) {
            if (region.length() > REGION_MAX_LENGTH) {
                throw new IllegalArgumentException("지역이 너무 깁니다. 최대 " + REGION_MAX_LENGTH + "자입니다.");
            }
        }
        String timezone = input.normalizedTimezone();
        if (timezone != null && !timezone.isEmpty()) {
            if (timezone.length() > TIMEZONE_MAX_LENGTH) {
                throw new IllegalArgumentException("시간대가 너무 깁니다. 최대 " + TIMEZONE_MAX_LENGTH + "자입니다.");
            }
            // 잘못된 시간대는 재고 시드와 취소 마감 시각이 엉뚱한 날짜를 가리킨다.
            // 저장하기 전에 막지 않으면 이미 심은 객실 유형의 시드 시작일이 틀어진다.
            if (!input.isValidTimezone()) {
                throw new IllegalArgumentException("알 수 없는 시간대입니다. 예: Asia/Seoul");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String requestHash(UUID staffId, UUID hotelId, HotelUpdateRequest input) {
        String payload = String.join("|",
                staffId.toString(),
                hotelId.toString(),
                String.valueOf(input.normalizedName()),
                String.valueOf(input.normalizedRegion()),
                String.valueOf(input.normalizedTimezone()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ExistingHotel(UUID id, String name, String region, String timezone) {
    }
}
