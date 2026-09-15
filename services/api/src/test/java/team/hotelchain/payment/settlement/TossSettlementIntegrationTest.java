package team.hotelchain.payment.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
        "payment.provider=toss-live", "payment.toss.settlement-enabled=true",
        "payment.toss.settlement-scan-delay=1h", "payment.toss.client-key=live_gck_fixture",
        "payment.toss.secret-key=live_gsk_fixture", "payment.toss.merchant-account=hotel-live",
        "payment.toss.customer-origin=https://hotel.example"
})
@Import(TossSettlementIntegrationTest.ProviderConfiguration.class)
class TossSettlementIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    private static final UUID STAFF = UUID.fromString("98500000-0000-0000-0000-000000000044");
    @Autowired JdbcTemplate jdbc;
    @Autowired TossSettlementRunService runs;
    @Autowired TossSettlementWorker worker;
    @Autowired StubSettlementClient provider;

    @BeforeEach
    void seed() {
        clean();
        jdbc.update("insert into staff_member(id,email,display_name,password_hash,role) values (?,?,?,?, 'HQ_ADMIN')",
                STAFF, "settlement@example.com", "정산 담당", "unused");
        provider.failPage = 0;
        provider.forceSecondPage = false;
    }

    @AfterEach
    void clean() {
        jdbc.execute("delete from toss_settlement_reconciliation");
        jdbc.execute("delete from toss_settlement_snapshot");
        jdbc.execute("delete from toss_settlement_run");
        jdbc.execute("delete from staff_session");
        jdbc.execute("delete from staff_member where id='" + STAFF + "'");
    }

    @Test
    void stores_provider_transactions_once_across_overlapping_runs() {
        UUID first = runs.create(DAY, DAY, STAFF);
        assertThat(worker.processNext()).isTrue();
        UUID second = runs.create(DAY, DAY, STAFF);
        assertThat(worker.processNext()).isTrue();

        assertThat(count("toss_settlement_snapshot")).isEqualTo(2);
        assertThat(status(first)).isEqualTo("SUCCEEDED");
        assertThat(status(second)).isEqualTo("SUCCEEDED");
        assertThat(count("toss_settlement_reconciliation")).isEqualTo(4);
    }

    @Test
    void resumes_failed_page_without_deleting_previous_snapshot() {
        provider.forceSecondPage = true;
        provider.failPage = 2;
        UUID run = runs.create(DAY, DAY, STAFF);
        assertThat(worker.processNext()).isTrue();
        assertThat(currentPage(run)).isEqualTo(2);
        assertThat(count("toss_settlement_snapshot")).isEqualTo(2);

        assertThat(worker.processNext()).isTrue();
        assertThat(status(run)).isEqualTo("PENDING");
        assertThat(currentPage(run)).isEqualTo(2);
        provider.failPage = 0;
        jdbc.update("update toss_settlement_run set next_attempt_at=now()-interval '1 second' where id=?", run);

        assertThat(worker.processNext()).isTrue();
        assertThat(status(run)).isEqualTo("SUCCEEDED");
        assertThat(count("toss_settlement_snapshot")).isEqualTo(2);
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private String status(UUID id) { return jdbc.queryForObject("select status from toss_settlement_run where id=?", String.class, id); }
    private int currentPage(UUID id) { return jdbc.queryForObject("select current_page from toss_settlement_run where id=?", Integer.class, id); }

    @TestConfiguration
    static class ProviderConfiguration {
        @Bean @Primary StubSettlementClient settlementProvider() { return new StubSettlementClient(); }
    }

    static class StubSettlementClient implements TossSettlementClient {
        int failPage;
        boolean forceSecondPage;
        @Override public SettlementPage fetch(LocalDate soldDate, int page, int size) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (page == failPage) throw new SettlementException("HTTP_503", true);
            if (page > 1) return new SettlementPage(List.of(), false);
            return new SettlementPage(List.of(record("pay-1", "txn-1"), record("pay-2", "txn-2")), forceSecondPage);
        }
        private SettlementRecord record(String payment, String transaction) {
            return new SettlementRecord("hotel-live", payment, transaction, "order-" + payment,
                    "KRW", "카드", 100000, 3000, 2727, 273, 97000,
                    Instant.parse("2026-09-01T01:00:00Z"), DAY, DAY.plusDays(7), false);
        }
    }
}
