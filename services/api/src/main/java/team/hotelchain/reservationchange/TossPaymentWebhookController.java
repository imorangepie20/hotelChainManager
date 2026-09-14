package team.hotelchain.reservationchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import team.hotelchain.payment.TossReservationPaymentService;

/** 공개 webhook은 재조회 힌트이며 본문의 결제 상태는 읽지 않는다. */
@RestController
@ConditionalOnProperty(name="payment.provider",havingValue="toss-test")
public class TossPaymentWebhookController {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json=new ObjectMapper();
    private final TossReservationPaymentService payments;
    public TossPaymentWebhookController(JdbcTemplate jdbc,TransactionTemplate tx,TossReservationPaymentService payments){this.jdbc=jdbc;this.tx=tx;this.payments=payments;}

    @Scheduled(fixedDelayString="${payment.toss.webhook-scan-delay:30s}",initialDelayString="${payment.toss.webhook-scan-delay:30s}")
    public void processLookups() {
        for(var row:jdbc.queryForList("select order_id,requested_at from toss_webhook_lookup where processed_at is null order by requested_at limit 20")) {
            payments.reconcileStoredOrder((String)row.get("order_id"));
            jdbc.update("update toss_webhook_lookup set processed_at=CURRENT_TIMESTAMP where order_id=? and requested_at=?",row.get("order_id"),row.get("requested_at"));
        }
    }
    @PostMapping(value="/api/payments/toss/webhook",consumes="application/json")
    public ResponseEntity<Void> receive(HttpServletRequest request) throws IOException {
        byte[] body=request.getInputStream().readNBytes(8193);
        if(body.length>8192)return ResponseEntity.status(413).build();
        String order;
        try{order=json.readTree(body).path("data").path("orderId").asText("");}
        catch(RuntimeException|IOException ex){return ResponseEntity.badRequest().build();}
        if(!order.matches("[A-Za-z0-9_-]{1,64}"))return ResponseEntity.accepted().build();
        tx.executeWithoutResult(t -> {
            boolean known=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from payment_provider_attempt where provider='TOSS_TEST' and order_id=? union all select 1 from toss_adjustment_order where order_id=?)",Boolean.class,order,order));
            if(!known)return;
            int accepted=jdbc.update("""
                    insert into toss_webhook_lookup(order_id) values (?) on conflict(order_id) do update
                    set requested_at=CURRENT_TIMESTAMP,processed_at=null
                    where toss_webhook_lookup.requested_at<CURRENT_TIMESTAMP-interval '30 seconds'
                    """,order);
            if(accepted==0)return;
            jdbc.update("""
                    insert into reservation_change_outbox(id,request_id,attempt_id,command_type,dedupe_key)
                    select ?,a.request_id,a.id,'QUERY',? from toss_adjustment_order o
                    join payment_adjustment_attempt a on a.id=o.attempt_id where o.order_id=?
                    """,UUID.randomUUID(),"webhook:"+UUID.randomUUID(),order);
        });
        return ResponseEntity.accepted().build();
    }
}
