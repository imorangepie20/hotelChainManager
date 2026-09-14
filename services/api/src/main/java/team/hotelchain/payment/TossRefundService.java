package team.hotelchain.payment;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import team.hotelchain.payment.TossPaymentsClient.*;
import team.hotelchain.reservation.BusinessConflictException;

/** 저장 원 거래와 특정 취소 이벤트를 연결하는 공통 환불 원장. 외부 호출은 잠금 밖에서 수행한다. */
@Service
@ConditionalOnProperty(name="payment.provider", havingValue="toss-test")
public class TossRefundService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TossPaymentsClient client;
    private final TossPaymentsProperties properties;
    private final Clock clock;
    public TossRefundService(JdbcTemplate jdbc, TransactionTemplate tx, TossPaymentsClient client,
            TossPaymentsProperties properties, Clock clock) {
        this.jdbc=jdbc; this.tx=tx; this.client=client; this.properties=properties; this.clock=clock;
    }

    public UUID prepare(UUID transactionId, long amount, UUID adjustmentId, UUID cancellationId) {
        return tx.execute(t -> {
            var payment=jdbc.queryForMap("select * from payment_transaction where id=? for update",transactionId);
            String key=(String)payment.get("gateway_transaction_id");
            if (!"TOSS_TEST".equals(payment.get("provider")) || !properties.merchantAccount().equals(payment.get("merchant_account"))
                    || !"KRW".equals(payment.get("currency")) || key == null || key.isBlank() || amount<=0
                    || (long)payment.get("captured_amount_krw")-(long)payment.get("refunded_amount_krw")<amount) throw conflict();
            List<String> orders=jdbc.queryForList("""
                    select order_id from payment_provider_attempt where payment_key=? and provider='TOSS_TEST'
                      and merchant_account=? and status='SUCCEEDED' and reservation_id=?
                    union all select order_id from toss_adjustment_order where payment_key=? and merchant_account=?
                      and status='SUCCEEDED' and reservation_id=?
                    """,String.class,key,properties.merchantAccount(),payment.get("reservation_id"),key,properties.merchantAccount(),payment.get("reservation_id"));
            if(orders.size()!=1) throw conflict();
            UUID id=UUID.randomUUID();
            jdbc.update("""
                    insert into toss_refund_command(id,transaction_id,adjustment_attempt_id,cancellation_attempt_id,
                        merchant_account,payment_key,order_id,amount_krw,idempotency_key,reason)
                    values (?,?,?,?,?,?,?,?,?,?)
                    """,id,transactionId,adjustmentId,cancellationId,properties.merchantAccount(),key,orders.getFirst(),amount,
                    "refund-"+id,"예약 환불 "+id);
            return id;
        });
    }

    public RefundResult execute(UUID id, boolean lookupOnly) {
        Claim claim=tx.execute(t -> {
            Map<String,Object> r=jdbc.queryForMap("select * from toss_refund_command where id=? for update",id);
            String state=(String)r.get("status");
            if(List.of("SUCCEEDED","FAILED").contains(state)) return new Claim(r,null,false);
            Timestamp lease=(Timestamp)r.get("lease_expires_at");
            if("PROCESSING".equals(state) && lease!=null && lease.toInstant().isAfter(clock.instant())) return new Claim(r,null,false);
            UUID token=UUID.randomUUID();
            jdbc.update("update toss_refund_command set status='PROCESSING',claim_token=?,lease_expires_at=?,updated_at=? where id=?",
                    token,Timestamp.from(clock.instant().plusSeconds(30)),Timestamp.from(clock.instant()),id);
            boolean lookup=lookupOnly || !"NEW".equals(state)
                    || ((Timestamp)r.get("created_at")).toInstant().plus(Duration.ofDays(15)).isBefore(clock.instant());
            return new Claim(r,token,lookup);
        });
        if(claim.token()==null) return result(claim.row());
        var row=claim.row();
        var command=new CancelCommand((String)row.get("payment_key"),(long)row.get("amount_krw"),(String)row.get("reason"),(String)row.get("idempotency_key"));
        ProviderPayment outcome;
        try {outcome=claim.lookup()?client.lookupCancel(command):client.cancel(command);}
        catch(RuntimeException ex){outcome=null;}
        final ProviderPayment verified=outcome;
        return tx.execute(t -> apply(id,claim.token(),verified));
    }

    private RefundResult apply(UUID id, UUID token, ProviderPayment result) {
        var row=jdbc.queryForMap("select * from toss_refund_command where id=? for update",id);
        if(!token.equals(row.get("claim_token"))) return result(row);
        String state="UNKNOWN";
        String event=null;
        if(result!=null && result.status()==ProviderStatus.FAILED) state="FAILED";
        if(result!=null && result.status()==ProviderStatus.DONE && Objects.equals(row.get("payment_key"),result.paymentKey())
                && Objects.equals(row.get("order_id"),result.orderId()) && (long)row.get("amount_krw")==result.amountKrw()
                && "KRW".equals(result.currency()) && properties.merchantAccount().equals(row.get("merchant_account"))
                && result.transactionKey()!=null && !result.transactionKey().isBlank()) {
            // 거래 전체 잔액이 아니라 이 command만의 취소 이벤트가 아직 반영되지 않았는지 확인한다.
            var payment=jdbc.queryForMap("select * from payment_transaction where id=? for update",row.get("transaction_id"));
            boolean duplicate=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from toss_refund_command where merchant_account=? and provider_event_id=? and id<>?)",
                    Boolean.class,properties.merchantAccount(),result.transactionKey(),id));
            if(!duplicate && "TOSS_TEST".equals(payment.get("provider")) && properties.merchantAccount().equals(payment.get("merchant_account"))) {
                int updated=jdbc.update("""
                        update payment_transaction set refunded_amount_krw=refunded_amount_krw+?,updated_at=CURRENT_TIMESTAMP
                        where id=? and captured_amount_krw-refunded_amount_krw>=?
                        """,result.amountKrw(),row.get("transaction_id"),result.amountKrw());
                if(updated==1){state="SUCCEEDED";event=result.transactionKey();}
            }
        }
        jdbc.update("update toss_refund_command set status=?,provider_event_id=?,claim_token=null,lease_expires_at=null,updated_at=? where id=?",
                state,event,Timestamp.from(clock.instant()),id);
        return new RefundResult(state,event,(String)row.get("payment_key"));
    }
    private RefundResult result(Map<String,Object> row){return new RefundResult((String)row.get("status"),(String)row.get("provider_event_id"),(String)row.get("payment_key"));}
    private BusinessConflictException conflict(){return new BusinessConflictException("PAYMENT_TRANSACTION_NOT_SETTLEABLE","같은 상점의 토스 원 거래를 확인해야 환불할 수 있습니다.");}
    private record Claim(Map<String,Object> row,UUID token,boolean lookup){}
    public record RefundResult(String status,String eventId,String paymentKey){}
}
