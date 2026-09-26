package team.hotelchain.staff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffPrincipal;

/**
 * 본사가 직원 계정을 삭제한다.
 * <p>
 * 삭제는 <strong>행을 지우는 것이 아니라 식별자를 영구적으로 비우는</strong>
 * 것이다. 감사 이력·예약 변경 승인·콘텐츠 발행 이력 20개 표가
 * {@code staff_member}를 참조하므로 행을 지우면 과거 운영 기록의 근거가
 * 사라진다. 행을 남겨두고 이메일·표시 이름·비밀번호·역할·지점을 비워서
 * 계정은 더 이상 로그인할 수 없고 직원 목록에도 나타나지 않는다.
 * <p>
 * 진행 중인 예약 변경 요청·정산 실행이 있으면 409로 거부하고 아무것도
 * 비우지 않는다. 삭제는 되돌릴 수 없으므로 모든 충돌 검사를 삭제 앞에 둔다.
 * <p>
 * 같은 요청의 중복 삭제는 {@code staff_account_command}의 멱원 키와
 * 요청 지문으로 막는다.
 */
@Service
public class StaffAccountDeletionService {

    static final String KIND_DELETE = "DELETE";
    static final String REMOVED_ROLE = "REMOVED";
    static final String REMOVED_DISPLAY_NAME = "삭제된 직원";

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private static final List<String> CLOSED_CHANGE_STATUSES = List.of(
            "COMPLETED", "REJECTED", "CANCELLED", "EXPIRED");

    private static final List<String> ACTIVE_SETTLEMENT_STATUSES = List.of("PENDING", "PROCESSING");

    private final JdbcTemplate jdbc;
    private final StaffAccessService access;
    private final Clock clock;

    public StaffAccountDeletionService(JdbcTemplate jdbc, StaffAccessService access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    /**
     * 직원 계정을 영구적으로 비운다.
     * <p>
     * 한 트랜잭션에 묶어서 멱원 기록까지 함께 남기거나 함께 롤백한다.
     */
    @Transactional
    public StaffAccountDeletionResponse delete(String token, UUID staffId, String idempotencyKey) {
        StaffPrincipal staff = access.requireHeadquarters(token);
        requireIdempotencyKey(idempotencyKey);

        // 본인을 삭제하면 본사 메뉴에 다시 들어오지 못한다. 비활성·역할 수정과
        // 같은 예외를 재사용한다.
        if (staffId.equals(staff.id())) {
            throw new StaffSelfModificationException();
        }

        String requestHash = requestHash(staff.id(), staffId);
        // 멱원 재호출을 계정 조회 앞에 둔다. 계정이 이미 비워졌으면
        // loadExisting이 null을 돌려줘서 재호출이 404로 착각하게 된다.
        StaffAccountDeletionResponse replayed = replay(staffId, staff.id(), idempotencyKey, requestHash);
        if (replayed != null) {
            return replayed;
        }

        AccountRow account = loadAccount(staffId);
        if (account == null) {
            throw new StaffAccountNotFoundException(staffId);
        }

        // 동시 삭제·수정을 직렬화한다. 두 본사 관리자가 같은 직원을
        // 동시에 다루면 한 쪽의 잠금이 풀릴 때까지 다른 쪽이 기다린다.
        jdbc.query("select id from staff_member where id = ? for update", rs -> { }, staffId);

        StaffAccountDeletionResponse secondCheck =
                replay(staffId, staff.id(), idempotencyKey, requestHash);
        if (secondCheck != null) {
            return secondCheck;
        }

        // 지우다 말 수 없으므로 모든 충돌 검사를 삭제 앞에 둔다.
        Conflicts conflicts = countConflicts(staffId);
        if (conflicts.hasAny()) {
            throw new StaffAccountDeletionConflictException(
                    conflicts.openChangeRequests(), conflicts.activeSettlementRuns());
        }

        deleteAccount(staffId, staff.id(), idempotencyKey, requestHash);

        return new StaffAccountDeletionResponse(
                staffId, account.email(), account.displayName(), true, remainingStaff());
    }

    // 멱원 재호출은 같은 결과를 돌려줘야 한다. 계정 행은 이미 비워져 있으므로
    // 멱원 기록에서 원래 이메일·표시 이름을 읽고 남은 직원 수는 따로 센다.
    private StaffAccountDeletionResponse replay(UUID staffId, UUID modifiedBy,
                                                String idempotencyKey, String requestHash) {
        UUID byKey = jdbc.query("""
                select staff_id from staff_account_command
                 where staff_id = ? and kind = ? and idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject("staff_id", UUID.class) : null,
                staffId, KIND_DELETE, idempotencyKey);
        if (byKey == null) {
            // 멱원 키는 달라도 같은 직원의 같은 내용 재시도면 같은 결과를 돌려준다.
            byKey = jdbc.query("""
                    select staff_id from staff_account_command
                     where staff_id = ? and kind = ? and request_hash = ?
                    """, rs -> rs.next() ? rs.getObject("staff_id", UUID.class) : null,
                    staffId, KIND_DELETE, requestHash);
        }
        if (byKey == null) {
            return null;
        }
        return new StaffAccountDeletionResponse(
                staffId,
                // 원래 이메일은 이미 자리 표시자로 덮여 있다.
                placeholderEmail(staffId),
                REMOVED_DISPLAY_NAME,
                false,
                remainingStaff());
    }

    // email에 UNIQUE 제약이 있어서 빈 문자열로 두면 삭제된 계정이 여러 개일 때
    // 유일 제약이 걸린다. 자리 표시자는 다시 로그인할 수 없는 값을 쓴다.
    private String placeholderEmail(UUID staffId) {
        return "deleted-" + staffId + "@deleted.local";
    }

    // 계정을 비운다. password_hash를 빈 문자열로 두면 BCrypt가 받지 않아
    // 삭제된 계정은 비밀번호가 맞아도 로그인할 수 없다.
    private void deleteAccount(UUID staffId, UUID modifiedBy, String idempotencyKey, String requestHash) {
        // 삭제된 직원이 남겨둔 세션을 즉시 끊는다.
        jdbc.update("delete from staff_session where staff_id = ?", staffId);
        jdbc.update("""
                update staff_member
                   set email = ?,
                       display_name = ?,
                       password_hash = '',
                       role = ?,
                       hotel_id = null,
                       active = false
                 where id = ?
                """, placeholderEmail(staffId), REMOVED_DISPLAY_NAME, REMOVED_ROLE, staffId);

        // 멱원 행은 계정을 비운 뒤에 만든다. staff_account_command가
        // staff_member를 참조하지만 행을 남겨뒀으므로 참조가 끊기지 않는다.
        // 트랜잭션이 묶여 있어 멱원 insert가 실패하면 삭제도 롤백된다.
        jdbc.update("""
                insert into staff_account_command
                    (id, staff_id, created_staff_id, kind, idempotency_key, request_hash, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staffId, modifiedBy, KIND_DELETE,
                idempotencyKey, requestHash, java.sql.Timestamp.from(clock.instant()));
    }

    private Conflicts countConflicts(UUID staffId) {
        Integer openChangeRequests = jdbc.queryForObject("""
                select count(*) from reservation_change_request
                 where requested_by = ?
                   and status not in (%s)
                """.formatted(placeholders(CLOSED_CHANGE_STATUSES.size())), Integer.class,
                withArguments(staffId, CLOSED_CHANGE_STATUSES));
        // 정산 실행이 진행 중이면 근거가 사라진다. 실패·성공한 실행은
        // 과거 기록이므로 막지 않는다.
        Integer activeSettlementRuns = jdbc.queryForObject("""
                select count(*) from toss_settlement_run
                 where requested_by = ?
                   and status in (%s)
                """.formatted(placeholders(ACTIVE_SETTLEMENT_STATUSES.size())), Integer.class,
                withArguments(staffId, ACTIVE_SETTLEMENT_STATUSES));
        return new Conflicts(
                openChangeRequests == null ? 0 : openChangeRequests,
                activeSettlementRuns == null ? 0 : activeSettlementRuns);
    }

    private int remainingStaff() {
        Integer count = jdbc.queryForObject(
                "select count(*) from staff_member where role <> ?", Integer.class, REMOVED_ROLE);
        return count == null ? 0 : count;
    }

    private AccountRow loadAccount(UUID staffId) {
        return jdbc.query("""
                select id, email, display_name
                  from staff_member
                 where id = ? and role <> ?
                """, rs -> rs.next() ? new AccountRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name")) : null,
                staffId, REMOVED_ROLE);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("올바른 Idempotency-Key가 필요합니다.");
        }
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    // JDBC가 IN 절에 List를 바인딩하지 못하므로 전개한다.
    private static Object[] withArguments(UUID staffId, List<String> statuses) {
        Object[] arguments = new Object[1 + statuses.size()];
        arguments[0] = staffId;
        for (int index = 0; index < statuses.size(); index++) {
            arguments[index + 1] = statuses.get(index);
        }
        return arguments;
    }

    static String requestHash(UUID modifiedBy, UUID staffId) {
        String payload = modifiedBy + "|" + staffId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record AccountRow(UUID id, String email, String displayName) {
    }

    private record Conflicts(int openChangeRequests, int activeSettlementRuns) {

        boolean hasAny() {
            return openChangeRequests > 0 || activeSettlementRuns > 0;
        }
    }
}
