package team.hotelchain.reservation;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
class PaymentProviderSafetyTest {
    @Test void externalCaptureCannotUseFakeSettlement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(id))).thenReturn(true);
        assertThat(PaymentProviderSafety.supportsFakeSettlement(jdbc, id)).isFalse();
        assertThatThrownBy(() -> PaymentProviderSafety.requireFakeSettlement(jdbc, id))
            .isInstanceOf(BusinessConflictException.class);
        verify(jdbc, never()).update(anyString());
    }
    @Test void fakeReservationKeepsExistingSettlementPath() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(id))).thenReturn(false);
        assertThatCode(() -> PaymentProviderSafety.requireFakeSettlement(jdbc, id)).doesNotThrowAnyException();
    }
}
