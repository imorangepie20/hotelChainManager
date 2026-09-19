from app.policy import (
    POLICY_TEXT,
    assert_no_forbidden_fields,
    assert_offers_from_api,
    missing_fields,
)


def test_lists_only_missing_required_fields() -> None:
    assert missing_fields({"region": "속초", "adults": 2}) == ["체크인 날짜", "체크아웃 날짜"]
    assert missing_fields({"region": "속초", "checkIn": "2026-09-22", "checkOut": "2026-09-24", "adults": 2}) == []
    # 빈 문자열·0·None 모두 누락이다.
    assert missing_fields({"region": "", "checkIn": None, "adults": 0}) == ["지점", "체크인 날짜", "체크아웃 날짜", "성인 인원"]


def test_policy_text_pins_pricing_and_booking_to_spring() -> None:
    assert "예약 생성" in POLICY_TEXT
    assert "결제" in POLICY_TEXT
    assert "Spring Boot" in POLICY_TEXT


def test_rejects_conditions_the_concierge_must_not_handle() -> None:
    for forbidden in ("payment", "confirm", "reservationId", "roomId"):
        try:
            assert_no_forbidden_fields({forbidden: "value"})
        except ValueError as error:
            assert forbidden in str(error)
        else:
            raise AssertionError(f"{forbidden} 조건은 거부되어야 합니다.")


def test_allows_booking_conditions_only() -> None:
    assert_no_forbidden_fields(
        {"region": "속초", "checkIn": "2026-09-22", "checkOut": "2026-09-24", "adults": 2, "rooms": 1}
    ) is None


def test_rejects_offers_missing_api_pricing_fields() -> None:
    broken = [{"roomTypeName": "스탠다드 시티", "ratePlanName": "유연 취소", "total": 360000}]
    try:
        assert_offers_from_api(broken)
    except ValueError as error:
        assert "remaining" in str(error)
    else:
        raise AssertionError("잔여 객실이 없는 후보는 거부되어야 합니다.")


def test_accepts_complete_api_offers() -> None:
    complete = [{
        "roomTypeName": "스탠다드 시티",
        "ratePlanName": "유연 취소",
        "total": 360000,
        "remaining": 3,
    }]
    assert assert_offers_from_api(complete) is None
