from __future__ import annotations

from typing import Any, Callable

# 도우미가 지키는 불변 정책. extract 노드가 LLM·정책 임베딩으로 조건을 해석하더라도
# 이 가드를 통과하지 못하면 안내만 반환하고 조회·추천으로 넘어가지 않는다.
POLICY_TEXT = (
    "AI는 Spring Boot 예약 API가 반환한 객실·가격·잔여 수만 안내한다. "
    "임의 가격·재고·예약 가능 여부를 만들지 않고 예약 생성·결제·취소를 실행하지 않는다."
)

# 누락 시追问할 필수 조건 키와 한국어 라벨.
REQUIRED_FIELDS: dict[str, str] = {
    "region": "지점",
    "checkIn": "체크인 날짜",
    "checkOut": "체크아웃 날짜",
    "adults": "성인 인원",
}

# 빈 문자열·0·None을 모두 "누락"으로 본다.
_IS_MISSING: Callable[[Any], bool] = lambda value: not value


def missing_fields(criteria: dict[str, Any]) -> list[str]:
    """필수 조건 중 비어 있는 항목의 라벨을 순서대로 반환한다."""
    return [label for key, label in REQUIRED_FIELDS.items() if _IS_MISSING(criteria.get(key))]


def assert_no_forbidden_fields(criteria: dict[str, Any]) -> None:
    """정책상 도우미가 다루지 않는 필드가 조건에 들어가면 거부한다."""
    for forbidden in ("payment", "paymentMethod", "confirm", "reservationId", "roomId"):
        if forbidden in criteria:
            raise ValueError(f"AI는 {forbidden} 조건을 처리할 수 없습니다.")


def assert_offers_from_api(offers: list[dict[str, Any]]) -> None:
    """추천 후보가 API 응답의 필수 금액·잔여 필드를 그대로 들고 있는지 확인한다."""
    for offer in offers:
        for field in ("roomTypeName", "ratePlanName", "total", "remaining"):
            if offer.get(field) is None:
                raise ValueError(f"추천 후보에 {field} 값이 없습니다. API 응답만 사용해야 합니다.")
