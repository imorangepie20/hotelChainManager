package team.hotelchain.reservationchange;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/reservation-change-payments")
@ConditionalOnExpression("${reservation.change.settlement-enabled:false} and '${reservation.change.gateway:disabled}' == 'fake'")
public class FakePaymentAdjustmentController {
    private final ReservationChangeSettlementService settlements;

    public FakePaymentAdjustmentController(ReservationChangeSettlementService settlements) {
        this.settlements = settlements;
    }

    @GetMapping(value = "/{attemptId}", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentPage(@PathVariable UUID attemptId) {
        return """
                <!doctype html><html lang="ko"><meta charset="utf-8"><title>테스트 결제</title>
                <body><main><h1>테스트 결제</h1><p>개발 환경에서만 사용하는 결제 화면입니다.</p>
                <form method="post" action="%s/succeeded"><button type="submit">결제 성공</button></form>
                <form method="post" action="%s/failed"><button type="submit">결제 실패</button></form>
                </main></body></html>
                """.formatted(attemptId, attemptId);
    }

    @PostMapping("/{attemptId}/{result}")
    public void result(@PathVariable UUID attemptId, @PathVariable String result) {
        PaymentAdjustmentGateway.GatewayResultStatus status = settlements.mapResultStatus(result);
        settlements.recordGatewayResult(attemptId, "fake:" + attemptId + ":" + status, status);
    }
}
