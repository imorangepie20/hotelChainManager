package team.hotelchain.reservationchange;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import team.hotelchain.payment.TossReservationPaymentView.*;

@RestController
@RequestMapping("/api/reservation-change-payments/current/toss")
@ConditionalOnProperty(name="reservation.change.gateway",havingValue="toss-test")
public class TossChangePaymentController {
    private final TossPaymentAdjustmentGateway gateway;
    public TossChangePaymentController(TossPaymentAdjustmentGateway gateway){this.gateway=gateway;}
    @PostMapping("/checkout") public CheckoutView checkout(@CookieValue("reservation_change_session")String session){return gateway.checkout(session);}
    @PostMapping("/confirm") public StatusView confirm(@CookieValue("reservation_change_session")String session,@RequestBody ConfirmPaymentRequest request){return gateway.confirm(session,request);}
    @GetMapping("/status") public StatusView status(@CookieValue("reservation_change_session")String session){return gateway.status(session);}
}
