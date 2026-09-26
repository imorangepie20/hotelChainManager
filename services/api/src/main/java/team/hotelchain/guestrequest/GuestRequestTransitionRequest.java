package team.hotelchain.guestrequest;

import java.util.UUID;

/**
 * 직원이 고객 요청의 처리 상태를 바꿀 때 쓰는 본문.
 * <p>
 * 상태 외에 담당자와 처리 안내를 함께 남길 수 있다. 둘 다 선택이고,
 * 보내지 않은 필드는 기존 값을 유지한다.
 */
public record GuestRequestTransitionRequest(

        String status,

        UUID assignTo,

        String resolutionNote) {
}
