package team.hotelchain.staff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직원이 본인 계정의 표시 이름·비밀번호를 바꾼다.
 * <p>
 * 전 역할이 쓸 수 있다. 역할·소속 지점은 본사 전용 권한이라 받지 않는다.
 * 본인 계정 수정이 역할을 바꾸면 본인 권한을 올리는 것이 가능해진다.
 * <p>
 * 비밀번호를 바꾸면 <strong>현재 세션 하나만 남기고 나머지를 끊는다</strong>.
 * 비밀번호 변경은 계정 통제권이 바뀌는 것이므로 다른 기기의 세션을
 * 끊어야 한다. 세션은 {@code token_hash} 기반이라 비밀번호를 바꿔도
 * 현재 세션은 살려둔다.
 * <p>
 * 현재 비밀번호 확인이 필수다. 세션을 탈취한 사람이 비밀번호를 바꾸는
 * 것을 막으려면 계정 통제권이 넘어가기 전에 다시 물어봐야 한다.
 * <p>
 * 같은 요청의 중복 처리는 {@code staff_account_command}의 멱원 키와
 * 요청 지문으로 막는다.
 */
@Service
public class StaffSelfUpdateService {

    static final String KIND_SELF_UPDATE = "SELF_UPDATE";

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock;

    public StaffSelfUpdateService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    /**
     * 본인 계정의 표시 이름·비밀번호를 바꾼다.
     * <p>
     * 한 트랜잭션에 묶어서 멱원 기록까지 함께 남기거나 함께 롤백한다.
     */
    @Transactional
    public StaffSelfUpdateResponse update(String token, String idempotencyKey, StaffSelfUpdateRequest request) {
        // 전 역할이 본인 계정을 고칠 수 있다. current()가 active를 검사하므로
        // 비활성·삭제된 계정은 401로 거부된다.
        StaffPrincipal staff = access.current(token);
        requireIdempotencyKey(idempotencyKey);
        request.validate();

        // 동시 수정을 직렬화한다.
        jdbc.query("select id from staff_member where id = ? for update", rs -> { }, staff.id());

        String requestHash = requestHash(staff.id(), request);
        UUID handled = findHandled(staff.id(), idempotencyKey, requestHash);
        if (handled != null) {
            return loadResponse(handled, false);
        }

        // 비밀번호를 바꿀 때는 현재 비밀번호를 다시 확인한다.
        String currentHash = jdbc.queryForObject(
                "select password_hash from staff_member where id = ?", String.class, staff.id());
        if (request.wantsPasswordChange()
                && (currentHash == null || currentHash.isBlank()
                        || !passwordEncoder.matches(request.currentPassword(), currentHash))) {
            throw new StaffSelfUpdateException();
        }

        if (request.wantsPasswordChange()) {
            // 비밀번호를 바꾸면 계정 통제권이 넘어간다. 현재 세션 하나만 남기고
            // 나머지 세션을 끊는다. token_hash로 현재 세션을 찾는다.
            String tokenHash = sha256(token);
            jdbc.update("""
                    delete from staff_session
                     where staff_id = ?
                       and token_hash <> ?
                    """, staff.id(), tokenHash);
        }

        if (request.displayName() != null && !request.displayName().isBlank()) {
            jdbc.update("update staff_member set display_name = ? where id = ?",
                    request.displayName().trim(), staff.id());
        }
        if (request.wantsPasswordChange()) {
            // 비밀번호 원문은 저장하지 않는다.
            jdbc.update("update staff_member set password_hash = ? where id = ?",
                    passwordEncoder.encode(request.newPassword()), staff.id());
        }

        jdbc.update("""
                insert into staff_account_command
                    (id, staff_id, created_staff_id, kind, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staff.id(), staff.id(), KIND_SELF_UPDATE,
                idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));

        return loadResponse(staff.id(), true);
    }

    private StaffSelfUpdateResponse loadResponse(UUID staffId, boolean changed) {
        return jdbc.query("""
                select m.id, m.email, m.display_name, m.role
                  from staff_member m
                 where m.id = ?
                """, rs -> rs.next() ? new StaffSelfUpdateResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        changed) : null,
                staffId);
    }

    // 같은 키 재호출과 같은 내용의 다른 키 재시도를 모두 잡는다.
    // 응답을 유실한 뒤 새 키로 같은 내용을 보내면 비밀번호가 두 번
    // 바뀌거나 세션이 두 번 끊기므로 지문까지 비교한다.
    private UUID findHandled(UUID staffId, String idempotencyKey, String requestHash) {
        UUID byKey = jdbc.query("""
                select staff_id from staff_account_command
                 where staff_id = ? and kind = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("staff_id", UUID.class) : null,
                staffId, KIND_SELF_UPDATE, idempotencyKey);
        if (byKey != null) {
            return byKey;
        }
        return jdbc.query("""
                select staff_id from staff_account_command
                 where staff_id = ? and kind = ? and request_hash = ?
                """, rs -> rs.next() ? rs.getObject("staff_id", UUID.class) : null,
                staffId, KIND_SELF_UPDATE, requestHash);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    static String requestHash(UUID staffId, StaffSelfUpdateRequest request) {
        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        // 비밀번호 원문은 지문에 넣지 않는다. 비밀번호를 바꿨다는 사실만으로
        // 같은 요청인지 판별할 수 있다.
        boolean wantsPassword = request.wantsPasswordChange();
        String payload = staffId + "|" + displayName + "|password=" + wantsPassword;
        return sha256(payload);
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
