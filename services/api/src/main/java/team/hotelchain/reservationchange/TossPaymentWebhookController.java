package team.hotelchain.reservationchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import team.hotelchain.payment.TossReservationPaymentService;
import team.hotelchain.payment.TossPaymentEnvironment;
import team.hotelchain.payment.TossWebhookRateLimiter;
import team.hotelchain.payment.TossWebhookRequest;

/** 공개 webhook은 재조회 힌트이며 본문의 결제 상태는 읽지 않는다. */
@RestController
@ConditionalOnExpression("'${payment.provider:fake}' == 'toss-test' or '${payment.provider:fake}' == 'toss-live'")
public class TossPaymentWebhookController {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json=new ObjectMapper();
    private final TossReservationPaymentService payments;
    private final TossPaymentEnvironment environment;
    private final TossWebhookRateLimiter rateLimiter;
    public TossPaymentWebhookController(JdbcTemplate jdbc,TransactionTemplate tx,TossReservationPaymentService payments,
            TossPaymentEnvironment environment,TossWebhookRateLimiter rateLimiter){this.jdbc=jdbc;this.tx=tx;this.payments=payments;this.environment=environment;this.rateLimiter=rateLimiter;}

    @Scheduled(fixedDelayString="${payment.toss.webhook-scan-delay:30s}",initialDelayString="${payment.toss.webhook-scan-delay:30s}")
    public void processLookups() {
        for(var row:jdbc.queryForList("select order_id,requested_at from toss_webhook_lookup where provider=? and processed_at is null order by requested_at limit 20",environment.providerCode())) {
            payments.reconcileStoredOrder((String)row.get("order_id"));
            jdbc.update("update toss_webhook_lookup set processed_at=CURRENT_TIMESTAMP where provider=? and order_id=? and requested_at=?",
                    environment.providerCode(),row.get("order_id"),row.get("requested_at"));
        }
    }
    @PostMapping(value="/api/payments/toss/webhook",consumes="application/json")
    public ResponseEntity<Void> receive(HttpServletRequest request,
            @RequestHeader(value="tosspayments-webhook-transmission-id",required=false) String transmissionId) throws IOException {
        if(!rateLimiter.tryAcquire())return ResponseEntity.status(429).build();
        byte[] body=request.getInputStream().readNBytes(65_537);
        if(body.length>65_536)return ResponseEntity.status(413).build();
        if(transmissionId==null||!transmissionId.matches("[A-Za-z0-9_-]{1,100}"))return ResponseEntity.badRequest().build();
        TossWebhookRequest webhook;
        try{webhook=TossWebhookRequest.parse(body,json);}
        catch(RuntimeException|IOException ex){return ResponseEntity.badRequest().build();}
        tx.executeWithoutResult(t -> {
            boolean known=Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from payment_provider_attempt where provider=? and order_id=?
                      union all select 1 from toss_adjustment_order o join payment_adjustment_attempt a on a.id=o.attempt_id
                        where a.provider=? and o.order_id=?)
                    """,Boolean.class,environment.providerCode(),webhook.orderId(),environment.providerCode(),webhook.orderId()));
            if(!known)return;
            int delivery=jdbc.update("""
                    insert into toss_webhook_delivery(id,provider,transmission_id,event_type,order_id)
                    values (?,?,?,?,?) on conflict(provider,transmission_id) do nothing
                    """,UUID.randomUUID(),environment.providerCode(),transmissionId,webhook.eventType(),webhook.orderId());
            if(delivery==0)return;
            int accepted=jdbc.update("""
                    insert into toss_webhook_lookup(provider,order_id) values (?,?) on conflict(provider,order_id) do update
                    set requested_at=CURRENT_TIMESTAMP,processed_at=null
                    """,environment.providerCode(),webhook.orderId());
            if(accepted==0)return;
            jdbc.update("""
                    insert into reservation_change_outbox(id,request_id,attempt_id,command_type,dedupe_key)
                    select ?,a.request_id,a.id,'QUERY',? from toss_adjustment_order o
                    join payment_adjustment_attempt a on a.id=o.attempt_id where a.provider=? and o.order_id=?
                    on conflict(dedupe_key) do nothing
                    """,UUID.randomUUID(),"webhook:"+environment.providerCode()+":"+transmissionId,
                    environment.providerCode(),webhook.orderId());
        });
        return ResponseEntity.accepted().build();
    }
}
