package team.hotelchain.payment.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.payment.TossPaymentEnvironment;

/**
 * 토스 test 환경에서도 본사 정산·대사 스택이 로드되고 provider 코드가 분리돼 저장되는지 확인한다.
 * 라이브 환경에서만 동작하면 개발·테스트 거래를 대사할 수 없다.
 */
@SpringBootTest(properties = {
        "payment.provider=toss-test", "payment.toss.settlement-enabled=true",
        "payment.toss.settlement-scan-delay=1h",
        "payment.toss.client-key=test_ck_fixture", "payment.toss.secret-key=test_sk_fixture",
        "payment.toss.merchant-account=hotel-test", "payment.toss.customer-origin=http://127.0.0.1:4000"
})
@Transactional
class TossSettlementModeIntegrationTest {
    private static final UUID STAFF = UUID.fromString("96600000-0000-0000-0000-000000000001");

    @Autowired TossPaymentEnvironment environment;
    @Autowired TossSettlementController controller;
    @Autowired TossSettlementQueryService query;
    @Autowired TossSettlementRunService runs;
    @Autowired TossSettlementWorker worker;
    @Autowired JdbcTemplate jdbc;

    @Test
    void settlement_stack_loads_in_toss_test_mode() {
        assertThat(environment).isEqualTo(TossPaymentEnvironment.TEST);
        assertThat(query).isNotNull();
        assertThat(worker).isNotNull();
        assertThat(controller).isNotNull();
    }

    @Test
    void run_records_test_provider_code() {
        jdbc.update("insert into staff_member(id,email,display_name,password_hash,role) values (?,?,?,?, 'HQ_ADMIN') "
                        + "on conflict (id) do nothing",
                STAFF, "settlement-mode@example.com", "정산 모드", "unused");

        UUID runId = runs.create(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), STAFF);

        assertThat(jdbc.queryForObject(
                "select provider from toss_settlement_run where id=?", String.class, runId))
                .isEqualTo("TOSS_TEST");
    }
}
