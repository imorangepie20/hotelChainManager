package team.hotelchain.inventory;

/**
 * 본사가 보낸 판매 상태 변경 요청이 이미 처리된 멱원 키와 내용이 다를 때
 * 발생한다.
 * <p>
 * 같은 {@code Idempotency-Key}를 재사용했지만 본문이 다르면 409로 거부한다.
 * 재호출이 다른 결과를 만들면 멱원이 성립하지 않기 때문이다.
 */
public class SalesStatusConflictException extends RuntimeException {

    public SalesStatusConflictException(String message) {
        super(message);
    }
}
