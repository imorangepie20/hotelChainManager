package team.hotelchain.reservation;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 외부 승인 예약은 실제 환불/변경 어댑터를 연결하기 전에는 변경하지 않는다. */
public final class PaymentProviderSafety {
    private PaymentProviderSafety() {}
    public static boolean supportsFakeSettlement(JdbcTemplate jdbc, UUID reservationId) {
        return !Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from payment_transaction
                  where reservation_id = ? and provider <> 'FAKE')
                """, Boolean.class, reservationId));
    }
    public static void requireFakeSettlement(JdbcTemplate jdbc, UUID reservationId) {
        if (!supportsFakeSettlement(jdbc, reservationId)) {
            throw new BusinessConflictException("PAYMENT_PROVIDER_ACTION_UNSUPPORTED",
                    "이 결제 공급자의 예약 취소·변경 정산은 아직 지원하지 않습니다. 호텔에 문의해 주세요.");
        }
    }
}
