package team.hotelchain.staff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.hotel.HotelNotFoundException;

/**
 * 본사가 직원을 만들고 임시 비밀번호를 재발급한다.
 * <p>
 * 임시 비밀번호는 서버가 생성해 응답에 한 번만 내보내고, 저장은 BCrypt 해시만 한다.
 * 같은 요청의 중복 처리는 {@code staff_account_command}의 멱원 키로 막는다.
 */
@Service
public class StaffAccountCommandService {

    static final String KIND_CREATE = "CREATE";
    static final String KIND_RESET_PASSWORD = "RESET_PASSWORD";

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration resetCooldown;

    public StaffAccountCommandService(
            JdbcTemplate jdbc,
            StaffAccessService access,
            Clock clock,
            @Value("${staff.password.reset-cooldown-seconds:300}") long resetCooldownSeconds) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.resetCooldown = Duration.ofSeconds(Math.max(0, resetCooldownSeconds));
    }

    @Transactional
    public StaffAccountCreateResponse create(String token, String idempotencyKey, StaffAccountCreateRequest request) {
        StaffPrincipal principal = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();
        if (!hotelExists(request.hotelId())) {
            throw new HotelNotFoundException(request.hotelId());
        }

        // 같은 멱원 키로 이미 처리됐으면 저장된 직원을 그대로 돌려준다.
        UUID existing = findStaffId(principal.id(), KIND_CREATE, idempotencyKey);
        if (existing != null) {
            return loadExisting(existing, null, false);
        }

        // 이메일 중복은 삽입 전에 확인한다. 삽입 실패 뒤에 다시 읽으면 트랜잭션이 중단된다.
        if (emailExists(request.email())) {
            throw new StaffEmailConflictException(request.email().trim().toLowerCase());
        }

        String temporaryPassword = newTemporaryPassword();
        UUID staffId = UUID.randomUUID();
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role, hotel_id)
                values (?, ?, ?, ?, ?, ?)
                """, staffId, request.email().trim().toLowerCase(), request.displayName().trim(),
                passwordEncoder.encode(temporaryPassword), request.role(), request.hotelId());
        jdbc.update("""
                insert into staff_account_command
                    (id, staff_id, created_staff_id, kind, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staffId, principal.id(), KIND_CREATE, idempotencyKey,
                requestHash(principal.id(), request), java.sql.Timestamp.from(clock.instant()));

        return loadExisting(staffId, temporaryPassword, true);
    }

    // 본사가 직원의 임시 비밀번호를 다시 만든다. 비밀번호 원문은 저장하지 않으므로
    // 멱원 재호출은 직원 요약만 내려주고 비밀번호는 다시 보여주지 않는다.
    @Transactional
    public StaffPasswordResetResponse resetPassword(String token, UUID staffId, String idempotencyKey) {
        StaffPrincipal principal = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);

        AccountRow account = loadAccount(staffId);
        if (account == null) {
            throw new StaffAccountNotFoundException(staffId);
        }

        Instant now = clock.instant();
        UUID existing = findStaffId(staffId, KIND_RESET_PASSWORD, idempotencyKey);
        if (existing != null) {
            // 응답을 유실해서 같은 키로 다시 물어보는 경우다. 비밀번호는 내려주지 않는다.
            return new StaffPasswordResetResponse(
                    account.id(), account.email(), account.displayName(), account.role(),
                    account.hotelId(), account.hotelName(), null, false, cooldownSeconds(now));
        }

        // 응답 유실 뒤 새 멱원 키로 또 발급하면 어떤 비밀번호가 유효한지 알 수 없다.
        // 최근 재발급이 쿨다운 안에 있으면 거부한다.
        Instant lastReset = lastPasswordReset(staffId);
        if (lastReset != null && lastReset.plus(resetCooldown).isAfter(now)) {
            throw new StaffPasswordCooldownException(
                    Math.max(1, Duration.between(now, lastReset.plus(resetCooldown)).getSeconds()));
        }

        String temporaryPassword = newTemporaryPassword();
        jdbc.update("update staff_member set password_hash = ? where id = ?",
                passwordEncoder.encode(temporaryPassword), staffId);
        jdbc.update("""
                insert into staff_account_command
                    (id, staff_id, created_staff_id, kind, idempotency_key, request_hash,
                     password_reset_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staffId, principal.id(), KIND_RESET_PASSWORD, idempotencyKey,
                resetRequestHash(staffId), java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        return new StaffPasswordResetResponse(
                account.id(), account.email(), account.displayName(), account.role(),
                account.hotelId(), account.hotelName(), temporaryPassword, true,
                resetCooldown.toSeconds());
    }

    private long cooldownSeconds(Instant now) {
        Instant lastReset = lastPasswordReset(null);
        if (lastReset == null) return 0;
        long remaining = Duration.between(now, lastReset.plus(resetCooldown)).getSeconds();
        return Math.max(0, remaining);
    }

    // staffId가 null이면 체인 전체에서 가장 최근 재발급을 찾는다. 응답 유실 뒤 새 멱원 키로
    // 다른 직원을 재발급하는 동안 비밀번호가 연속으로 바뀌는 것도 막기 위해서다.
    private Instant lastPasswordReset(UUID staffId) {
        String sql;
        Object[] args;
        if (staffId == null) {
            sql = """
                    select password_reset_at from staff_account_command
                     where kind = ? and password_reset_at is not null
                     order by password_reset_at desc, id desc
                     limit 1
                    """;
            args = new Object[] { KIND_RESET_PASSWORD };
        } else {
            sql = """
                    select password_reset_at from staff_account_command
                     where kind = ? and staff_id = ? and password_reset_at is not null
                     order by password_reset_at desc, id desc
                     limit 1
                    """;
            args = new Object[] { KIND_RESET_PASSWORD, staffId };
        }
        java.sql.Timestamp timestamp = jdbc.query(sql, rs -> rs.next() ? rs.getTimestamp(1) : null, args);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private AccountRow loadAccount(UUID staffId) {
        return jdbc.query("""
                select m.id, m.email, m.display_name, m.role, m.hotel_id, h.name as hotel_name
                  from staff_member m
                  left join hotel h on h.id = m.hotel_id
                 where m.id = ?
                """, rs -> rs.next() ? new AccountRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getObject("hotel_id", UUID.class) == null
                                ? null : rs.getObject("hotel_id", UUID.class).toString(),
                        rs.getString("hotel_name")) : null,
                staffId);
    }

    private boolean emailExists(String email) {
        Integer count = jdbc.queryForObject(
                "select count(*) from staff_member where email = ?", Integer.class,
                email.trim().toLowerCase());
        return count != null && count > 0;
    }

    // 생성은 만든 사람이 누구인지로, 재발급은 대상 직원이 누구인지로 멱원 키를 찾는다.
    private UUID findStaffId(UUID staffId, String kind, String idempotencyKey) {
        if (KIND_CREATE.equals(kind)) {
            return jdbc.query("""
                    select staff_id from staff_account_command
                     where created_staff_id = ? and kind = ? and idempotency_key = ?
                    """, rs -> rs.next() ? rs.getObject("staff_id", UUID.class) : null,
                    staffId, kind, idempotencyKey);
        }
        return jdbc.query("""
                select staff_id from staff_account_command
                 where staff_id = ? and kind = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("staff_id", UUID.class) : null,
                staffId, kind, idempotencyKey);
    }

    private StaffAccountCreateResponse loadExisting(UUID staffId, String temporaryPassword, boolean created) {
        return jdbc.query("""
                select m.id, m.email, m.display_name, m.role, m.hotel_id, h.name as hotel_name
                  from staff_member m
                  left join hotel h on h.id = m.hotel_id
                 where m.id = ?
                """, rs -> rs.next() ? new StaffAccountCreateResponse(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getObject("hotel_id", UUID.class) == null
                                ? null : rs.getObject("hotel_id", UUID.class).toString(),
                        rs.getString("hotel_name"),
                        temporaryPassword,
                        created) : null,
                staffId);
    }

    private boolean hotelExists(UUID hotelId) {
        if (hotelId == null) return true;
        Integer count = jdbc.queryForObject(
                "select count(*) from hotel where id = ?", Integer.class, hotelId);
        return count != null && count > 0;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private String newTemporaryPassword() {
        StringBuilder builder = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int index = 0; index < TEMPORARY_PASSWORD_LENGTH; index++) {
            builder.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return builder.toString();
    }

    static String requestHash(UUID createdBy, StaffAccountCreateRequest request) {
        String payload = createdBy + "|" + request.email().trim().toLowerCase() + "|"
                + request.displayName().trim() + "|" + request.role() + "|" + request.hotelId();
        return sha256(payload);
    }

    static String resetRequestHash(UUID staffId) {
        // 재발급은 본문이 없으므로 대상 직원과 시점이 아니라 멱원 키 자체가 요청 지문이다.
        return sha256("reset-password|" + staffId);
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record AccountRow(
            String id, String email, String displayName, String role, String hotelId, String hotelName) {
    }
}
