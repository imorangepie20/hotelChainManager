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

import team.hotelchain.payment.settlement.TossSettlementQueryService.ReconciliationRow;
import team.hotelchain.payment.settlement.TossSettlementQueryService.SettlementRunSummary;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAuthenticationException;

@SpringBootTest(properties = {
        "payment.provider=toss-live", "payment.toss.settlement-enabled=true",
        "payment.toss.settlement-scan-delay=1h", "payment.toss.settlement-delay-days=2",
        "payment.toss.client-key=live_gck_fixture", "payment.toss.secret-key=live_gsk_fixture",
        "payment.toss.merchant-account=hotel-live", "payment.toss.customer-origin=https://hotel.example",
        "staff.dev.enabled=false"
})
@Import(TossSettlementQueryIntegrationTest.ClockConfiguration.class)
@Transactional
class TossSettlementQueryIntegrationTest {
    private static final String MERCHANT = "hotel-live";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 9);
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID STAFF = UUID.fromString("95500000-0000-0000-0000-000000000001");
    private static final UUID BRANCH = UUID.fromString("95500000-0000-0000-0000-000000000002");
    private static final UUID CLAIM = UUID.fromString("95500000-0000-0000-0000-000000000003");
    private static final UUID RUN_A = UUID.fromString("95500000-0000-0000-0000-000000000010");
    private static final UUID RUN_B = UUID.fromString("95500000-0000-0000-0000-000000000011");

    @Autowired JdbcTemplate jdbc;
    @Autowired TossSettlementQueryService query;
    @Autowired TossSettlementController controller;
    @Autowired StaffAccessService access;

    @Test
    void lists_runs_newest_first_without_mutating_tables() {
        seedStaff();
        insertRun(RUN_B, "SUCCEEDED");
        insertRun(RUN_A, "PROCESSING");
        insertReconciliation(RUN_B, "matched-a", "MATCHED", 100_000L, 100_000L);

        var view = controller.runs(session(), 20);

        assertThat(view.runs()).extracting(SettlementRunSummary::id)
                .containsExactly(RUN_B, RUN_A);
        assertThat(view.runs()).extracting(SettlementRunSummary::status)
                .containsExactly("SUCCEEDED", "PROCESSING");
        assertThat(view.serverAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject(
                "select count(*) from toss_settlement_reconciliation where run_id=?", Integer.class, RUN_B))
                .isOne();
    }

    @Test
    void run_detail_joins_snapshot_and_keeps_amounts_nullable() {
        seedStaff();
        insertRun(RUN_A, "SUCCEEDED");
        UUID snapshot = insertSnapshot(RUN_A, "pay-detail", "txn-detail");
        insertReconciliation(RUN_A, "provider:" + snapshot, snapshot, "MATCHED",
                100_000L, 100_000L, 3_000L, 2_727L, 273L, 97_000L, null);
        insertReconciliation(RUN_A, "refund-only", null, "MISSING_PROVIDER",
                30_000L, null, null, null, null, null, "PROVIDER_EVENT_NOT_FOUND");

        var view = controller.run(RUN_A, session(), null, 100);

        assertThat(view.run()).isNotNull();
        assertThat(view.run().id()).isEqualTo(RUN_A);
        var byKey = view.rows().stream()
                .collect(java.util.stream.Collectors.toMap(ReconciliationRow::reconciliationKey, row -> row));
        var matched = byKey.get("provider:" + snapshot);
        assertThat(matched.status()).isEqualTo("MATCHED");
        assertThat(matched.snapshotPaymentKey()).isEqualTo("pay-detail");
        assertThat(matched.snapshotTransactionKey()).isEqualTo("txn-detail");
        assertThat(matched.snapshotOrderId()).isEqualTo("snapshot-txn-detail");
        assertThat(matched.snapshotCancellation()).isFalse();
        assertThat(matched.snapshotSoldDate()).isEqualTo(FROM);
        assertThat(matched.providerAmountKrw()).isEqualTo(100_000L);
        var missing = byKey.get("refund-only");
        assertThat(missing.status()).isEqualTo("MISSING_PROVIDER");
        assertThat(missing.snapshotId()).isNull();
        assertThat(missing.providerAmountKrw()).isNull();
        assertThat(missing.feeKrw()).isNull();
        assertThat(missing.detailCode()).isEqualTo("PROVIDER_EVENT_NOT_FOUND");
        assertThat(view.serverAt()).isEqualTo(NOW);
    }

    @Test
    void filters_by_status_and_rejects_unknown_status_values() {
        seedStaff();
        insertRun(RUN_A, "SUCCEEDED");
        UUID snapshot = insertSnapshot(RUN_A, "pay-filter", "txn-filter");
        insertReconciliation(RUN_A, "provider:" + snapshot, snapshot, "MATCHED",
                100_000L, 100_000L, null, null, null, null, null);
        insertReconciliation(RUN_A, "missing-one", null, "MISSING_PROVIDER", 30_000L, null, null, null, null, null, null);

        assertThat(controller.run(RUN_A, session(), "MATCHED", 100).rows())
                .extracting(ReconciliationRow::status).containsOnly("MATCHED");
        assertThat(controller.run(RUN_A, session(), "PENDING", 100).rows()).isEmpty();
        // 알 수 없는 상태 필터는 전체를 반환하지 않고 안전하게 무시한다.
        assertThat(controller.run(RUN_A, session(), "MATCHED';--", 100).rows()).hasSize(2);
    }

    @Test
    void limits_clamp_out_of_range_request_sizes() {
        seedStaff();
        insertRun(RUN_A, "SUCCEEDED");
        insertRun(RUN_B, "SUCCEEDED");
        for (int index = 0; index < 3; index++) {
            insertReconciliation(RUN_A, "row-" + index, null, "PENDING", 10_000L, null, null, null, null, null, null);
        }

        assertThat(controller.runs(session(), 0).runs()).hasSize(1);
        assertThat(controller.runs(session(), 5_000).runs()).hasSize(2);
        assertThat(controller.run(RUN_A, session(), null, 1).rows()).hasSize(1);
        assertThat(controller.run(RUN_A, session(), null, -1).rows()).hasSize(1);
        assertThat(controller.run(RUN_A, session(), null, 10_000).rows()).hasSize(3);
    }

    @Test
    void unknown_run_returns_empty_detail_without_error() {
        seedStaff();
        var view = controller.run(UUID.randomUUID(), session(), null, 100);
        assertThat(view.run()).isNull();
        assertThat(view.rows()).isEmpty();
        assertThat(view.serverAt()).isEqualTo(NOW);
    }

    @Test
    void headquarters_session_is_required_and_branch_staff_is_denied() {
        seedStaff();
        insertRun(RUN_A, "SUCCEEDED");
        String hq = session();
        String branch = session("branch@hotel-chain.local");

        assertThatThrownBySubclass(() -> controller.runs(branch, 20), StaffAccessDeniedException.class);
        assertThatThrownBySubclass(() -> controller.run(RUN_A, branch, null, 100), StaffAccessDeniedException.class);
        assertThatThrownBySubclass(() -> controller.runs("not-a-session", 20), StaffAuthenticationException.class);
        assertThatThrownBySubclass(() -> controller.run(RUN_A, "not-a-session", null, 100),
                StaffAuthenticationException.class);

        assertThat(controller.runs(hq, 20).runs()).extracting(SettlementRunSummary::id).containsExactly(RUN_A);
        assertThat(controller.run(RUN_A, hq, null, 100).run()).isNotNull();
    }

    @Test
    void query_service_does_not_depend_on_http_layer() {
        seedStaff();
        insertRun(RUN_A, "SUCCEEDED");
        assertThat(query.runs(10).runs()).extracting(SettlementRunSummary::id).containsExactly(RUN_A);
        assertThat(query.run(RUN_A, null, 10).run()).isNotNull();
    }

    private void seedStaff() {
        UUID hotel = UUID.fromString("11000000-0000-0000-0000-000000000001");
        int hotelRows = jdbc.update("insert into hotel values (?,?,?,?) on conflict do nothing",
                hotel, "정산 조회 테스트", "속초", "Asia/Seoul");
        int hqRows = jdbc.update(
                "insert into staff_member(id,email,display_name,password_hash,role) values (?,?,?,?,'HQ_ADMIN') "
                        + "on conflict (id) do update set email=excluded.email,password_hash=excluded.password_hash",
                STAFF, "hq-query@example.com", "본사 정산", accessHash("hq-password"));
        int branchRows = jdbc.update(
                "insert into staff_member(id,email,display_name,password_hash,role,hotel_id) values (?,?,?,?, 'BRANCH_STAFF',?) "
                        + "on conflict (id) do update set email=excluded.email,password_hash=excluded.password_hash",
                BRANCH, "branch@hotel-chain.local", "지점 직원", accessHash("branch-password"), hotel);
        assert hotelRows >= 0 && hqRows >= 0 && branchRows >= 0;
    }

    private String session() {
        return session("hq-query@example.com");
    }

    private String session(String email) {
        return access.login(email, switch (email) {
            case "branch@hotel-chain.local" -> "branch-password";
            default -> "hq-password";
        }).token();
    }

    private void insertRun(UUID id, String status) {
        jdbc.update("""
                insert into toss_settlement_run(id,provider,merchant_account,sold_date_from,sold_date_to,
                    status,current_sold_date,claim_token,lease_expires_at,requested_by,idempotency_key,request_hash)
                values (?,'TOSS_LIVE',?,?,?, ?,?,?,?,?, ?, ?)
                """, id, MERCHANT, FROM, TO, status, FROM,
                CLAIM, java.sql.Timestamp.from(NOW.plusSeconds(90)), STAFF, "query-test-" + id, "a".repeat(64));
    }

    private UUID insertSnapshot(UUID runId, String paymentKey, String transactionKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into toss_settlement_snapshot(id,first_run_id,merchant_account,payment_key,transaction_key,
                    order_id,currency,method,amount_krw,fee_krw,fee_supply_krw,fee_vat_krw,payout_krw,
                    approved_at,sold_date,paid_out_date,cancellation)
                values (?,?,?,?,?,?, 'KRW','카드',100000,3000,2727,273,97000,?,?,?,false)
                """, id, runId, MERCHANT, paymentKey, transactionKey, "snapshot-" + transactionKey,
                java.sql.Timestamp.from(FROM.atStartOfDay().toInstant(ZoneOffset.UTC)), FROM, FROM.plusDays(7));
        return id;
    }

    private void insertReconciliation(UUID runId, String key, String status,
            Long expected, Long provider) {
        insertReconciliation(runId, key, null, status, expected, provider,
                null, null, null, null, null);
    }

    private void insertReconciliation(UUID runId, String key, UUID snapshotId, String status,
            Long expected, Long provider, Long fee, Long supply, Long vat, Long payout, String detail) {
        jdbc.update("""
                insert into toss_settlement_reconciliation(id,run_id,reconciliation_key,snapshot_id,
                    payment_transaction_id,refund_command_id,status,expected_amount_krw,provider_amount_krw,
                    fee_krw,fee_supply_krw,fee_vat_krw,payout_krw,detail_code)
                values (?,?,?,?, null,null,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), runId, key, snapshotId, status, expected, provider,
                fee, supply, vat, payout, detail);
    }

    private String accessHash(String password) {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password);
    }

    private void assertThatThrownBySubclass(org.junit.jupiter.api.function.Executable executable,
            Class<? extends RuntimeException> expected) {
        org.junit.jupiter.api.Assertions.assertThrows(expected, executable);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
