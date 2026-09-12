from datetime import date, timedelta

from app.graph import check_missing, extract_conditions


def test_extracts_core_conditions() -> None:
    result = extract_conditions({"message": "2026-09-17부터 2박, 속초 성인 두 명 조식 포함", "criteria": {}})

    assert result["criteria"] == {
        "region": "속초", "adults": 2, "breakfastIncluded": True,
        "checkIn": "2026-09-17", "checkOut": "2026-09-19",
    }


def test_requests_only_missing_conditions() -> None:
    missing = check_missing({"criteria": {"region": "속초", "adults": 2}})

    assert missing["missing"] == ["체크인 날짜", "체크아웃 날짜"]


def test_parses_next_saturday_with_stay_length() -> None:
    result = extract_conditions({"message": "다음 주 토요일부터 2박, 속초 성인 두 명", "criteria": {}})

    check_in = result["criteria"]["checkIn"]
    check_out = result["criteria"]["checkOut"]
    assert date.fromisoformat(check_out) - date.fromisoformat(check_in) == timedelta(days=2)
    assert date.fromisoformat(check_in).weekday() == 5
