package team.hotelchain.guestrequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 고객이 예약과 관련해 호텔에 보내는 요청·문의 본문.
 * <p>
 * 예약 변경·취소는 이미 전용 API가 있으므로 받지 않는다. 이 API는 그 외의
 * 요청(객실 요청, 편의 요청, 환불 문의, 일반 문의)을 받는다.
 */
public record GuestRequestCreateRequest(

        @NotBlank
        @Size(max = 30)
        String requestType,

        @NotBlank
        @Size(max = 100)
        String subject,

        @NotBlank
        @Size(max = 2_000)
        String body,

        @NotBlank
        @Size(max = 100)
        String guestName,

        @NotBlank
        @Size(max = 254)
        String guestEmail,

        @Size(max = 30)
        String guestPhone) {
}
