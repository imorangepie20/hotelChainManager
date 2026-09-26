package team.hotelchain.guestrequest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 고객이 호텔에 예약 관련 요청·문의를 보낸다.
 * <p>
 * 예약 변경·취소는 전용 API가 있으므로 여기서 받지 않는다. 이 endpoint는
 * 객실 요청·편의 요청·환불 문의·일반 문의를 접수만 한다. 가격·재고·예약 상태를
 * 바꾸지 않는다.
 */
@RestController
@RequestMapping("/api/hotels/{hotelId}/guest-requests")
public class CustomerGuestRequestController {

    private final GuestRequestCommandService commands;

    public CustomerGuestRequestController(GuestRequestCommandService commands) {
        this.commands = commands;
    }

    // 고객이 요청을 보낸다. 같은 Idempotency-Key 재호출은 200으로 같은 요청을 돌려준다.
    @PostMapping
    public ResponseEntity<GuestRequestCreateResponse> submit(
            @PathVariable UUID hotelId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GuestRequestCreateRequest request) {
        GuestRequestCreateResponse response = commands.submit(hotelId, null, idempotencyKey, request);
        if (response.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
