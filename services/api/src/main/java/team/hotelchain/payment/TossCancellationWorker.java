package team.hotelchain.payment;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import team.hotelchain.reservation.CancellationService;

@Service
@ConditionalOnProperty(name="payment.provider",havingValue="toss-test")
public class TossCancellationWorker {
    private final JdbcTemplate jdbc;
    private final TossRefundService refunds;
    private final CancellationService cancellations;
    public TossCancellationWorker(JdbcTemplate jdbc,TossRefundService refunds,CancellationService cancellations){
        this.jdbc=jdbc;this.refunds=refunds;this.cancellations=cancellations;
    }
    @Scheduled(fixedDelayString="${payment.toss.cancellation-scan-delay:30s}",initialDelayString="${payment.toss.cancellation-scan-delay:30s}")
    public void processPending(){
        for(UUID attempt:jdbc.queryForList("select id from cancellation_attempt where refund_status in ('PENDING','UNKNOWN') order by created_at limit 20",UUID.class)){
            for(UUID id:jdbc.queryForList("select id from toss_refund_command where cancellation_attempt_id=? and status<>'SUCCEEDED' order by created_at,id",UUID.class,attempt)){
                refunds.execute(id,false);
            }
            cancellations.completeTossCancellation(attempt);
        }
    }
}
