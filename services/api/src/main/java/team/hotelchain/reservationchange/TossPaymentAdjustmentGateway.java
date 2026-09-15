package team.hotelchain.reservationchange;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import team.hotelchain.payment.*;
import team.hotelchain.payment.TossPaymentsClient.*;
import team.hotelchain.payment.TossReservationPaymentView.*;
import team.hotelchain.reservation.*;

@Service
@ConditionalOnExpression("'${reservation.change.gateway:disabled}' == 'toss-test' or '${reservation.change.gateway:disabled}' == 'toss-live'")
public class TossPaymentAdjustmentGateway implements PaymentAdjustmentGateway {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TossPaymentsClient client;
    private final TossPaymentsProperties properties;
    private final TossPaymentEnvironment environment;
    private final TossRefundService refunds;
    private final ReservationAccess access;
    private final ReservationChangeSettlementService settlements;
    private final Clock clock;
    public TossPaymentAdjustmentGateway(JdbcTemplate jdbc,TransactionTemplate tx,TossPaymentsClient client,
            TossPaymentsProperties properties,TossPaymentEnvironment environment,TossRefundService refunds,ReservationAccess access,
            ReservationChangeSettlementService settlements,Clock clock,
            @Value("${reservation.change.gateway:disabled}") String gatewayMode) {
        if(!environment.mode().equals(gatewayMode)) throw new IllegalStateException("예약 변경과 결제의 Toss 환경이 일치해야 합니다.");
        this.jdbc=jdbc;this.tx=tx;this.client=client;this.properties=properties;this.refunds=refunds;
        this.environment=environment;this.access=access;this.settlements=settlements;this.clock=clock;
    }

    @Override public GatewayAdjustmentResult createCheckout(GatewayCheckoutCommand command) {
        return tx.execute(t -> {
            var attempt=jdbc.queryForMap("select a.*,r.reservation_id from payment_adjustment_attempt a join reservation_change_request r on r.id=a.request_id where a.id=?",command.attemptId());
            if(!environment.providerCode().equals(attempt.get("provider")) || command.amountKrw()<=0 || !"KRW".equals(command.currency())) throw conflict();
            jdbc.update("""
                    insert into toss_adjustment_order(attempt_id,reservation_id,merchant_account,order_id,idempotency_key,amount_krw)
                    values (?,?,?,?,?,?) on conflict(attempt_id) do nothing
                    """,command.attemptId(),attempt.get("reservation_id"),properties.merchantAccount(),"change-"+command.attemptId(),
                    "confirm-"+command.attemptId(),attempt.get("amount_krw"));
            return new GatewayAdjustmentResult(null,null,GatewayResultStatus.PENDING,
                    URI.create(properties.customerOrigin()+"/reservation-change-payment"),null);
        });
    }

    public CheckoutView checkout(String sessionToken) {
        return tx.execute(t -> {
            var order=sessionOrder(sessionToken);
            if(!"NEW".equals(order.get("status")) || !"AWAITING_PAYMENT".equals(order.get("request_status"))) throw conflict();
            String base=properties.customerOrigin()+"/reservation-change-payment";
            String label=environment==TossPaymentEnvironment.TEST?"토스 테스트 결제":"토스 결제";
            return new CheckoutView((String)order.get("order_id"),(long)order.get("amount_krw"),"KRW",properties.clientKey(),
                    base+"?result=success",base+"?result=fail",label);
        });
    }

    public StatusView confirm(String sessionToken, ConfirmPaymentRequest input) {
        if(input==null || input.orderId()==null || input.paymentKey()==null || !input.paymentKey().matches("[A-Za-z0-9_-]{1,160}")
                || input.amountKrw()==null) throw new IllegalArgumentException("주문과 결제 금액이 필요합니다.");
        Claim claim=tx.execute(t -> {
            var candidate=sessionOrder(sessionToken);
            UUID reservationId=(UUID)candidate.get("reservation_id");
            jdbc.query("select id from reservation where id=? for update",rs->{},reservationId);
            jdbc.query("select id from reservation_change_request where id=? for update",rs->{},candidate.get("request_id"));
            var order=sessionOrder(sessionToken);
            if(!input.orderId().equals(order.get("order_id")) || input.amountKrw()!=(long)order.get("amount_krw"))
                throw new IllegalArgumentException("저장된 주문 또는 결제 금액과 일치하지 않습니다.");
            if(order.get("payment_key")!=null && !input.paymentKey().equals(order.get("payment_key"))) throw conflict();
            String state=(String)order.get("status");
            if("SUCCEEDED".equals(state)||"FAILED".equals(state))return new Claim(order,false,false);
            Timestamp claimed=(Timestamp)order.get("claimed_at");
            if("APPROVING".equals(state)&&claimed!=null&&claimed.toInstant().plusSeconds(30).isAfter(clock.instant()))return new Claim(order,false,false);
            if("NEW".equals(state) && (!"AWAITING_PAYMENT".equals(order.get("request_status"))
                    || !((Timestamp)order.get("settlement_expires_at")).toInstant().isAfter(clock.instant())))throw conflict();
            boolean other=Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from payment_provider_attempt where provider=? and payment_key=?
                      union all select 1 from toss_adjustment_order o join payment_adjustment_attempt a on a.id=o.attempt_id
                        where a.provider=? and o.payment_key=? and o.attempt_id<>?)
                    """,Boolean.class,environment.providerCode(),input.paymentKey(),environment.providerCode(),
                    input.paymentKey(),order.get("attempt_id")));
            if(other)throw conflict();
            jdbc.update("update toss_adjustment_order set payment_key=?,status='APPROVING',claimed_at=?,updated_at=? where attempt_id=?",
                    input.paymentKey(),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()),order.get("attempt_id"));
            jdbc.update("update payment_adjustment_attempt set checkout_started_at=coalesce(checkout_started_at,?),status='PROCESSING' where id=?",
                    Timestamp.from(clock.instant()),order.get("attempt_id"));
            order.put("payment_key",input.paymentKey());
            return new Claim(order,true,!"NEW".equals(state));
        });
        UUID attempt=(UUID)claim.row().get("attempt_id");
        if(claim.execute()) {
            ProviderPayment result;
            try {result=claim.lookup()?client.lookup(input.paymentKey()):client.confirm(new ConfirmCommand(input.paymentKey(),
                    (String)claim.row().get("order_id"),(long)claim.row().get("amount_krw"),"KRW",(String)claim.row().get("idempotency_key")));}
            catch(RuntimeException ex){result=null;}
            final ProviderPayment outcome=result;
            try {
                tx.executeWithoutResult(t -> {
                    var applied=apply(attempt,outcome);
                    settlements.recordGatewayResult(attempt,event(attempt,applied),applied.status());
                });
            } catch(RuntimeException ex) {
                tx.executeWithoutResult(t -> {
                    jdbc.update("update toss_adjustment_order set status='UNKNOWN',updated_at=? where attempt_id=? and status<>'SUCCEEDED'",Timestamp.from(clock.instant()),attempt);
                    settlements.recordGatewayResult(attempt,"toss:"+attempt+":UNKNOWN",GatewayResultStatus.UNKNOWN);
                });
            }
        } else if("SUCCEEDED".equals(claim.row().get("status"))) {
            settlements.recordGatewayResult(attempt,(String)claim.row().get("provider_event_id"),GatewayResultStatus.SUCCEEDED);
        }
        return status(sessionToken);
    }

    public StatusView status(String sessionToken) {
        var order=sessionOrder(sessionToken);
        return new StatusView((UUID)order.get("reservation_id"),(String)order.get("order_id"),(String)order.get("request_status"),(String)order.get("status"));
    }

    @Override public GatewayAdjustmentResult refund(GatewayRefundCommand command) {
        UUID refundId=tx.execute(t -> {
            var a=jdbc.queryForMap("select * from payment_adjustment_attempt where id=? for update",command.attemptId());
            if(!environment.providerCode().equals(a.get("provider")))throw conflict();
            List<UUID> existing=jdbc.queryForList("select id from toss_refund_command where adjustment_attempt_id=?",UUID.class,command.attemptId());
            return existing.isEmpty()?refunds.prepare((UUID)a.get("original_payment_transaction_id"),(long)a.get("amount_krw"),command.attemptId(),null):existing.getFirst();
        });
        return refundResult(refunds.execute(refundId,false));
    }

    @Override public GatewayAdjustmentResult query(GatewayQueryCommand command) {
        List<UUID> refundIds=jdbc.queryForList("select id from toss_refund_command where adjustment_attempt_id=?",UUID.class,command.attemptId());
        if(!refundIds.isEmpty())return refundResult(refunds.execute(refundIds.getFirst(),true));
        var order=jdbc.queryForMap("select * from toss_adjustment_order where attempt_id=?",command.attemptId());
        if(order.get("payment_key")==null)return new GatewayAdjustmentResult(null,null,GatewayResultStatus.PENDING,null,null);
        if("SUCCEEDED".equals(order.get("status")))return new GatewayAdjustmentResult((String)order.get("provider_event_id"),(String)order.get("payment_key"),GatewayResultStatus.SUCCEEDED,null,null);
        ProviderPayment result;
        try{result=client.lookup((String)order.get("payment_key"));}catch(RuntimeException ex){result=null;}
        return apply(command.attemptId(),result);
    }

    private GatewayAdjustmentResult apply(UUID attempt,ProviderPayment result) {
        return tx.execute(t -> {
            var candidate=jdbc.queryForMap("select * from toss_adjustment_order where attempt_id=?",attempt);
            jdbc.query("select id from reservation where id=? for update",rs->{},candidate.get("reservation_id"));
            var row=jdbc.queryForMap("select * from toss_adjustment_order where attempt_id=? for update",attempt);
            if("SUCCEEDED".equals(row.get("status")))return new GatewayAdjustmentResult((String)row.get("provider_event_id"),(String)row.get("payment_key"),GatewayResultStatus.SUCCEEDED,null,null);
            GatewayResultStatus state=GatewayResultStatus.UNKNOWN;
            if(result!=null && result.status()==ProviderStatus.FAILED)state=GatewayResultStatus.FAILED;
            if(result!=null && result.status()==ProviderStatus.DONE && Objects.equals(row.get("payment_key"),result.paymentKey())
                    && Objects.equals(row.get("order_id"),result.orderId()) && (long)row.get("amount_krw")==result.amountKrw()
                    && "KRW".equals(result.currency()) && properties.merchantAccount().equals(row.get("merchant_account")))state=GatewayResultStatus.SUCCEEDED;
            String event=state==GatewayResultStatus.SUCCEEDED?"toss-charge:"+row.get("payment_key"):null;
            jdbc.update("update toss_adjustment_order set status=?,provider_event_id=?,updated_at=? where attempt_id=?",state.name(),event,Timestamp.from(clock.instant()),attempt);
            jdbc.update("update payment_adjustment_attempt set gateway_transaction_id=? where id=?",row.get("payment_key"),attempt);
            return new GatewayAdjustmentResult(event,(String)row.get("payment_key"),state,null,null);
        });
    }

    private Map<String,Object> sessionOrder(String token) {
        var rows=jdbc.queryForList("""
                select o.*,a.request_id,r.status as request_status,r.settlement_expires_at from toss_adjustment_order o
                join payment_adjustment_attempt a on a.id=o.attempt_id join reservation_change_request r on r.id=a.request_id
                join reservation_change_customer_session s on s.request_id=r.id
                where s.token_hash=? and s.expires_at>? and a.public_token_hash is not null
                order by o.created_at desc limit 1
                """,access.hashToken(token),Timestamp.from(clock.instant()));
        if(rows.isEmpty())throw new ReservationNotFoundException();
        return rows.getFirst();
    }
    private GatewayAdjustmentResult refundResult(TossRefundService.RefundResult r){return new GatewayAdjustmentResult(r.eventId(),r.paymentKey(),
            switch(r.status()){case "SUCCEEDED"->GatewayResultStatus.SUCCEEDED;case "FAILED"->GatewayResultStatus.FAILED;default->GatewayResultStatus.UNKNOWN;},null,null);}
    private String event(UUID id,GatewayAdjustmentResult r){return r.providerEventId()==null?"toss:"+id+":"+r.status():r.providerEventId();}
    private BusinessConflictException conflict(){return new BusinessConflictException("TOSS_PAYMENT_CONFLICT","저장된 토스 결제 주문과 상태를 확인해 주세요.");}
    private record Claim(Map<String,Object> row,boolean execute,boolean lookup){}
}
