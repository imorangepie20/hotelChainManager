package team.hotelchain.payment.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TossSettlementReconciliationServiceTest {
    @Test
    void classifies_amount_and_fee_mismatches_before_match() {
        assertThat(TossSettlementReconciliationService.classify(100000, 90000, 3000, 2727, 273, 97000))
                .isEqualTo("AMOUNT_MISMATCH");
        assertThat(TossSettlementReconciliationService.classify(100000, 100000, 3001, 2727, 273, 97000))
                .isEqualTo("FEE_MISMATCH");
        assertThat(TossSettlementReconciliationService.classify(100000, 100000, 3000, 2727, 273, 96999))
                .isEqualTo("FEE_MISMATCH");
        assertThat(TossSettlementReconciliationService.classify(100000, 100000, 3000, 2727, 273, 97000))
                .isEqualTo("MATCHED");
    }
}
