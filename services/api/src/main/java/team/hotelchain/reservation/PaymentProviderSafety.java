package team.hotelchain.reservation;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 예약의 거래 공급자와 활성 정산 어댑터가 일치해야 한다. */
public final class PaymentProviderSafety {
    private PaymentProviderSafety() {}
    public static boolean supportsFakeSettlement(JdbcTemplate jdbc, UUID reservationId) {
        return !Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from payment_transaction
                  where reservation_id = ? and provider <> 'FAKE')
                """, Boolean.class, reservationId));
    }
    public static boolean supportsSettlement(JdbcTemplate jdbc, UUID reservationId, String gatewayMode) {
        if ("fake".equals(gatewayMode)) return supportsFakeSettlement(jdbc, reservationId);
        String provider = switch (gatewayMode) {
            case "toss-test" -> "TOSS_TEST";
            case "toss-live" -> "TOSS_LIVE";
            default -> null;
        };
        if (provider == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from payment_transaction where reservation_id=? and provider=?)
                   and not exists(select 1 from payment_transaction where reservation_id=? and provider<>?)
                """, Boolean.class, reservationId, provider, reservationId, provider));
    }
    public static void requireCompatibleSettlement(JdbcTemplate jdbc, UUID reservationId, String gatewayMode) {
        if (!supportsSettlement(jdbc, reservationId, gatewayMode)) {
            throw new BusinessConflictException("PAYMENT_PROVIDER_ACTION_UNSUPPORTED",
                    "현재 활성 정산 공급자로 이 예약을 처리할 수 없습니다. 호텔에 문의해 주세요.");
        }
    }
    public static void requireFakeSettlement(JdbcTemplate jdbc, UUID reservationId) {
        if (!supportsFakeSettlement(jdbc, reservationId)) {
            throw new BusinessConflictException("PAYMENT_PROVIDER_ACTION_UNSUPPORTED",
                    "이 결제 공급자의 예약 취소·변경 정산은 아직 지원하지 않습니다. 호텔에 문의해 주세요.");
        }
    }
}
