package team.hotelchain.payment.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;

/**
 * 蹂몄궗媛 ?뺤궛 ?ㅽ뻾??吏곸젒 留뚮뱾怨??ㅽ뙣???ㅽ뻾???ъ떆?꾪븯???숈옉??寃利앺븳??
 * ?ㅽ뻾 ?앹꽦쨌?ъ떆?꾨뒗 worker媛 泥섎━???ㅽ뻾留?以鍮꾪븯怨?寃곗젣쨌?ш퀬 ?곹깭瑜?諛붽씀吏 ?딅뒗??
 */
@SpringBootTest(properties = {
        "payment.provider=toss-test", "payment.toss.settlement-enabled=true",
        "payment.toss.settlement-scan-delay=1h", "payment.toss.settlement-delay-days=2",
        "payment.toss.client-key=test_ck_fixture", "payment.toss.secret-key=test_sk_fixture",
        "payment.toss.merchant-account=hotel-test", "payment.toss.customer-origin=http://127.0.0.1:4000",
        "staff.dev.enabled=false"
})
@Import(TossSettlementCommandIntegrationTest.ClockConfiguration.class)
@Transactional
class TossSettlementCommandIntegrationTest {
    private static final String MERCHANT = "hotel-test";
    private static final LocalDate NOW_DATE = LocalDate.of(2026, 9, 10);
    private static final Instant NOW = NOW_DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final UUID STAFF = UUID.fromString("97700000-0000-0000-0000-000000000001");
    private static final UUID BRANCH = UUID.fromString("97700000-0000-0000-0000-000000000002");
    private static final UUID HOTEL = UUID.fromString("11000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired TossSettlementController controller;
    @Autowired TossSettlementRunService runs;
    @Autowired StaffAccessService access;

    @Test
    void headquarters_creates_pending_run_for_the_requested_period() {
        seedStaff();

        var view = controller.createRun(session(), new TossSettlementController.CreateRunRequest(
                NOW_DATE.minusDays(3), NOW_DATE.minusDays(1)));

        assertThat(view.getBody().runId()).isNotNull();
        assertThat(jdbc.queryForObject(
                "select status from toss_settlement_run where id=?", String.class, view.getBody().runId()))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "select provider from toss_settlement_run where id=?", String.class, view.getBody().runId()))
                .isEqualTo("TOSS_TEST");
        assertThat(jdbc.queryForObject(
                "select merchant_account from toss_settlement_run where id=?", String.class, view.getBody().runId()))
                .isEqualTo(MERCHANT);
        // 寃곗젣쨌?ш퀬쨌????뚯씠釉붿? 洹몃?濡??붾떎.
        assertThat(count("toss_settlement_snapshot")).isZero();
        assertThat(count("toss_settlement_reconciliation")).isZero();
    }

    @Test
    void rejects_invalid_periods_and_future_ranges() {
        seedStaff();
        String token = session();

        // from ?댄썑 to
        assertThatThrownByClient(() -> controller.createRun(token, new TossSettlementController.CreateRunRequest(
                NOW_DATE.minusDays(1), NOW_DATE.minusDays(3))));
        // 誘몃옒 湲곌컙
        assertThatThrownByClient(() -> controller.createRun(token, new TossSettlementController.CreateRunRequest(
                NOW_DATE.plusDays(1), NOW_DATE.plusDays(3))));
        // 31??珥덇낵
        assertThatThrownByClient(() -> controller.createRun(token, new TossSettlementController.CreateRunRequest(
                NOW_DATE.minusDays(35), NOW_DATE.minusDays(1))));

        assertThat(count("toss_settlement_run")).isZero();
    }

    @Test
    void branch_staff_and_bad_sessions_cannot_create_runs() {
        seedStaff();

        org.junit.jupiter.api.Assertions.assertThrows(team.hotelchain.staff.StaffAccessDeniedException.class,
                () -> controller.createRun(session("branch@hotel-chain.local"),
                        new TossSettlementController.CreateRunRequest(NOW_DATE.minusDays(1), NOW_DATE)));
        org.junit.jupiter.api.Assertions.assertThrows(team.hotelchain.staff.StaffAuthenticationException.class,
                () -> controller.createRun("not-a-session",
                        new TossSettlementController.CreateRunRequest(NOW_DATE.minusDays(1), NOW_DATE)));

        assertThat(count("toss_settlement_run")).isZero();
    }

    @Test
    void retries_only_failed_runs_and_keeps_snapshots() {
        seedStaff();
        UUID runId = insertRun("FAILED", "retry-terminal");
        UUID succeeded = insertRun("SUCCEEDED", "retry-done");

        controller.retryRun(runId, session());

        assertThat(status(runId)).isEqualTo("PENDING");
        assertThat(attemptCount(runId)).isZero();
        assertThat(errorCode(runId)).isNull();

        // ?ㅽ뙣?섏? ?딆? ?ㅽ뻾? ?ъ떆?꾪븷 ???녿떎.
        org.junit.jupiter.api.Assertions.assertThrows(SettlementNotRetryableException.class,
                () -> controller.retryRun(succeeded, session()));
        assertThat(status(succeeded)).isEqualTo("SUCCEEDED");
    }

    @Test
    void retry_reports_unknown_run_and_leaves_tables_alone() {
        seedStaff();

        org.junit.jupiter.api.Assertions.assertThrows(SettlementRunNotFoundException.class,
                () -> controller.retryRun(UUID.randomUUID(), session()));
        assertThat(count("toss_settlement_run")).isZero();
    }

    private void seedStaff() {
        jdbc.update("insert into hotel values (?,?,?,?) on conflict do nothing",
                HOTEL, "정산 명령 테스트", "속초", "Asia/Seoul");
        jdbc.update("insert into staff_member(id,email,display_name,password_hash,role) values (?,?,?,?,'HQ_ADMIN') "
                        + "on conflict (id) do update set email=excluded.email,password_hash=excluded.password_hash",
                STAFF, "hq-command@example.com", "본사 정산", accessHash("hq-password"));
        jdbc.update("insert into staff_member(id,email,display_name,password_hash,role,hotel_id) values (?,?,?,?, 'BRANCH_STAFF',?) "
                        + "on conflict (id) do update set email=excluded.email,password_hash=excluded.password_hash",
                BRANCH, "branch@hotel-chain.local", "지점 직원", accessHash("branch-password"), HOTEL);
    }

    private String session() {
        return session("hq-command@example.com");
    }

    private String session(String email) {
        return access.login(email, switch (email) {
            case "branch@hotel-chain.local" -> "branch-password";
            default -> "hq-password";
        }).token();
    }

    private UUID insertRun(String status, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into toss_settlement_run(id,provider,merchant_account,sold_date_from,sold_date_to,
                    status,current_sold_date,page_size,attempt_count,next_attempt_at,requested_by,idempotency_key,
                    request_hash,error_code,created_at,updated_at)
                values (?,?,?,?,?,?, ?,?,?,?, ?,?,?, ?,now(),now())
                """, id, "TOSS_TEST", MERCHANT, NOW_DATE.minusDays(2), NOW_DATE.minusDays(1), status,
                NOW_DATE.minusDays(2), 500, status.equals("FAILED") ? 5 : 0,
                java.sql.Timestamp.from(NOW.minusSeconds(60)), STAFF, "command-" + suffix, "f".repeat(64),
                status.equals("FAILED") ? "SETTLEMENT_UNEXPECTED" : null);
        return id;
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("select status from toss_settlement_run where id=?", String.class, id);
    }

    private int attemptCount(UUID id) {
        return jdbc.queryForObject("select attempt_count from toss_settlement_run where id=?", Integer.class, id);
    }

    private String errorCode(UUID id) {
        return jdbc.queryForObject("select error_code from toss_settlement_run where id=?", String.class, id);
    }

    private void assertThatThrownByClient(org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, executable);
    }

    private String accessHash(String password) {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
