package team.hotelchain.hotel;

import java.time.LocalDate;

/**
 * 본사가 객실 유형에 두 번째 요금제를 만들 때 받는 요청이다.
 * <p>
 * 객실 유형 하나에 요금제가 여러 개일 수 있다. 조식 포함 여부·취소 규정
 * ({@code policyVersion})·금액이 다른 요금제를 같은 객실에 동시에 팔아야
 * 하기 때문이다. 예약은 {@code rate_plan_id}만 가지고 계약 조건을 판단하므로
 * 요금제마다 독립된 조건을 가진다.
 * <p>
 * 검증은 서버가 최종 판단한다. 금액은 0원 이상 10,000,000원 이하,
 * 일수는 1일 이상 92일 이하다.
 */
public record RatePlanCreateRequest(
        String name,
        Boolean breakfastIncluded,
        String policyVersion,
        Integer defaultRateKrw,
        LocalDate fromDate,
        Integer days) {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int POLICY_VERSION_MAX_LENGTH = 50;
    public static final int MIN_RATE_KRW = 0;
    public static final int MAX_RATE_KRW = 10_000_000;
    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 92;
    static final int DEFAULT_DAYS = 90;

    public void validate() {
        if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "요금제 이름은 1자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > POLICY_VERSION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "정책 버전은 1자 이상 " + POLICY_VERSION_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (defaultRateKrw == null || defaultRateKrw < MIN_RATE_KRW || defaultRateKrw > MAX_RATE_KRW) {
            throw new IllegalArgumentException(
                    "기본 요금은 " + MIN_RATE_KRW + "원 이상 " + MAX_RATE_KRW + "원 이하여야 합니다.");
        }
        if (days != null && (days < MIN_DAYS || days > MAX_DAYS)) {
            throw new IllegalArgumentException(
                    "요금을 심을 일수는 " + MIN_DAYS + "일 이상 " + MAX_DAYS + "일 이하여야 합니다.");
        }
    }

    public boolean breakfastIncludedOrFalse() {
        return Boolean.TRUE.equals(breakfastIncluded);
    }

    /**
     * 시작 날짜를 보내지 않으면 지점 현지 시간대 기준 오늘로 취급한다.
     * 유형 시드와 같은 기준이어야 오늘 도착 검색이 빈 결과를 돌려받지 않는다.
     */
    public int daysOrDefault() {
        return days == null ? DEFAULT_DAYS : days;
    }
}