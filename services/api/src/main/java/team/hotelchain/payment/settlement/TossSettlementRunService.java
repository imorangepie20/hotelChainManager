package team.hotelchain.payment.settlement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import team.hotelchain.payment.TossPaymentsProperties;

@Service
@ConditionalOnExpression("${payment.toss.settlement-enabled:false} and '${payment.provider:fake}' == 'toss-live'")
public class TossSettlementRunService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String merchantAccount;
    private final int pageSize;

    public TossSettlementRunService(JdbcTemplate jdbc, Clock clock, TossPaymentsProperties properties,
            @Value("${payment.toss.settlement-page-size:500}") int pageSize) {
        if (pageSize < 1 || pageSize > 5000) throw new IllegalArgumentException("정산 페이지 크기가 올바르지 않습니다.");
        this.jdbc = jdbc;
        this.clock = clock;
        this.merchantAccount = properties.merchantAccount();
        this.pageSize = pageSize;
    }

    public UUID create(LocalDate from, LocalDate to, UUID requestedBy) {
        validate(from, to, requestedBy);
        UUID id = UUID.randomUUID();
        String key = "auto-" + id;
        jdbc.update("""
                insert into toss_settlement_run(id,provider,merchant_account,sold_date_from,sold_date_to,
                    current_sold_date,page_size,requested_by,idempotency_key,request_hash)
                values (?,'TOSS_LIVE',?,?,?,?,?,?,?,?)
                """, id, merchantAccount, from, to, from, pageSize, requestedBy, key, hash(from + ":" + to));
        return id;
    }

    private void validate(LocalDate from, LocalDate to, UUID requestedBy) {
        if (from == null || to == null || requestedBy == null || from.isAfter(to)
                || to.isAfter(LocalDate.now(clock)) || ChronoUnit.DAYS.between(from, to) >= 31) {
            throw new IllegalArgumentException("정산 조회 기간은 과거 31일 이내여야 합니다.");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
