from app.llm import (
    extract_with_patterns,
    guard,
    is_configured,
    measure_llm_outcome,
    merge,
)


def test_reports_when_the_llm_key_is_absent() -> None:
    import os

    original = os.environ.pop("GOOGLE_API_KEY", None)
    try:
        assert is_configured() is False
    finally:
        if original is not None:
            os.environ["GOOGLE_API_KEY"] = original


def test_reports_when_the_llm_key_is_present() -> None:
    import os

    original = os.environ.get("GOOGLE_API_KEY")
    os.environ["GOOGLE_API_KEY"] = "test-key"
    try:
        assert is_configured() is True
    finally:
        if original is None:
            os.environ.pop("GOOGLE_API_KEY", None)
        else:
            os.environ["GOOGLE_API_KEY"] = original


def test_pattern_fallback_extracts_core_conditions() -> None:
    result = extract_with_patterns("2026-09-17부터 2박, 속초 성인 두 명 조식 포함", {})

    assert result == {
        "region": "속초", "adults": 2, "breakfastIncluded": True,
        "checkIn": "2026-09-17", "checkOut": "2026-09-19",
    }


def test_pattern_fallback_keeps_existing_criteria() -> None:
    result = extract_with_patterns("조식 포함", {"region": "제주도", "adults": 3, "rooms": 2})

    assert result == {
        "region": "제주도", "adults": 3, "rooms": 2, "breakfastIncluded": True,
    }


def test_pattern_fallback_parses_next_saturday_with_stay_length() -> None:
    from datetime import date, timedelta

    result = extract_with_patterns("다음 주 토요일부터 2박, 속초 성인 두 명", {})
    check_in = date.fromisoformat(result["checkIn"])
    check_out = date.fromisoformat(result["checkOut"])

    assert check_out - check_in == timedelta(days=2)
    assert check_in.weekday() == 5


def test_guard_rejects_conditions_the_concierge_must_not_handle() -> None:
    for forbidden in ("payment", "confirm", "reservationId", "roomId"):
        try:
            guard({forbidden: "value"})
        except ValueError as error:
            assert forbidden in str(error)
        else:
            raise AssertionError(f"{forbidden} 조건은 거부되어야 합니다.")


def test_merge_falls_back_to_patterns_without_an_llm_key() -> None:
    import os

    original = os.environ.pop("GOOGLE_API_KEY", None)
    try:
        result = merge("2026-09-17부터 2박, 속초 성인 두 명 조식 포함", {})
    finally:
        if original is not None:
            os.environ["GOOGLE_API_KEY"] = original

    assert result["region"] == "속초"
    assert result["adults"] == 2
    assert result["checkIn"] == "2026-09-17"
    assert result["checkOut"] == "2026-09-19"
    assert result["breakfastIncluded"] is True


def test_merge_does_not_invent_missing_values() -> None:
    import os

    original = os.environ.pop("GOOGLE_API_KEY", None)
    try:
        result = merge("속초로 가고 싶어요", {})
    finally:
        if original is not None:
            os.environ["GOOGLE_API_KEY"] = original

    assert result == {"region": "속초"}


class _Response:
    def __init__(self, text: str | None) -> None:
        self.text = text


def test_reports_a_missing_key_without_calling_the_llm() -> None:
    import os

    original = os.environ.pop("GOOGLE_API_KEY", None)

    def call() -> None:
        raise AssertionError("키가 없으면 LLM을 호출하지 않는다.")

    try:
        outcome, elapsed_ms = measure_llm_outcome(call)
    finally:
        if original is not None:
            os.environ["GOOGLE_API_KEY"] = original

    assert outcome == "no_key"
    assert elapsed_ms >= 0.0


def test_reports_a_rejected_schema_as_a_quality_signal() -> None:
    import os

    original = os.environ.get("GOOGLE_API_KEY")
    os.environ["GOOGLE_API_KEY"] = "test-key"

    def call() -> _Response:
        # 지점 화이트리스트 밖의 값은 스키마 검증에서 전체 결과를 버린다.
        return _Response('{"region":"부산","checkIn":"","checkOut":"","adults":0,"children":0,"rooms":0,"breakfastIncluded":false}')

    try:
        outcome, elapsed_ms = measure_llm_outcome(call)
    finally:
        if original is None:
            os.environ.pop("GOOGLE_API_KEY", None)
        else:
            os.environ["GOOGLE_API_KEY"] = original

    assert outcome == "schema_rejected"
    assert elapsed_ms >= 0.0


def test_reports_an_unparsable_response() -> None:
    import os

    original = os.environ.get("GOOGLE_API_KEY")
    os.environ["GOOGLE_API_KEY"] = "test-key"

    def call() -> _Response:
        return _Response("예약 조건을 들을 수 없습니다.")

    try:
        outcome, _elapsed_ms = measure_llm_outcome(call)
    finally:
        if original is None:
            os.environ.pop("GOOGLE_API_KEY", None)
        else:
            os.environ["GOOGLE_API_KEY"] = original

    assert outcome == "unparsable"


def test_reports_an_api_error_without_stopping_the_concierge() -> None:
    import os

    original = os.environ.get("GOOGLE_API_KEY")
    os.environ["GOOGLE_API_KEY"] = "test-key"

    def call() -> _Response:
        raise RuntimeError("외부 LLM 장애")

    try:
        outcome, _elapsed_ms = measure_llm_outcome(call)
    finally:
        if original is None:
            os.environ.pop("GOOGLE_API_KEY", None)
        else:
            os.environ["GOOGLE_API_KEY"] = original

    assert outcome == "api_error"


def test_reports_an_empty_response() -> None:
    import os

    original = os.environ.get("GOOGLE_API_KEY")
    os.environ["GOOGLE_API_KEY"] = "test-key"

    def call() -> _Response:
        return _Response("")

    try:
        outcome, _elapsed_ms = measure_llm_outcome(call)
    finally:
        if original is None:
            os.environ.pop("GOOGLE_API_KEY", None)
        else:
            os.environ["GOOGLE_API_KEY"] = original

    assert outcome == "unparsable"


def test_reports_a_non_object_response() -> None:
    import os

    original = os.environ.get("GOOGLE_API_KEY")
    os.environ["GOOGLE_API_KEY"] = "test-key"

    def call() -> _Response:
        return _Response("null")

    try:
        outcome, _elapsed_ms = measure_llm_outcome(call)
    finally:
        if original is None:
            os.environ.pop("GOOGLE_API_KEY", None)
        else:
            os.environ["GOOGLE_API_KEY"] = original

    assert outcome == "empty_response"


def test_reports_a_successful_extraction() -> None:
    import os

    original = os.environ.get("GOOGLE_API_KEY")
    os.environ["GOOGLE_API_KEY"] = "test-key"

    def call() -> _Response:
        return _Response('{"region":"속초","checkIn":"2026-09-25","checkOut":"2026-09-27","adults":2,"children":0,"rooms":0,"breakfastIncluded":true}')

    try:
        outcome, _elapsed_ms = measure_llm_outcome(call)
    finally:
        if original is None:
            os.environ.pop("GOOGLE_API_KEY", None)
        else:
            os.environ["GOOGLE_API_KEY"] = original

    assert outcome == "success"


def test_the_outcome_is_logged_at_info_level() -> None:
    import logging
    import os

    from app import llm

    original_key = os.environ.pop("GOOGLE_API_KEY", None)
    original_level = llm.logger.level
    llm.logger.setLevel(logging.INFO)
    records: list[logging.LogRecord] = []

    class _Capture(logging.Handler):
        def emit(self, record: logging.LogRecord) -> None:
            records.append(record)

    handler = _Capture()
    llm.logger.addHandler(handler)
    try:
        # 키가 없으면 Gemini를 호출하지 않고 no_key로 끝난다. 운영 로그 수집은 이 경로에서도 빠지지 않아야 한다.
        llm.extract_with_llm("2026-09-25부터 2박, 속초 성인 두 명")
    finally:
        llm.logger.removeHandler(handler)
        llm.logger.setLevel(original_level)
        if original_key is not None:
            os.environ["GOOGLE_API_KEY"] = original_key

    assert len(records) == 1
    assert records[0].levelno == logging.INFO
    assert "llm.outcome" in records[0].message
    assert "no_key" in records[0].message
    # message 원문은 로그에 남지 않는다.
    assert records[0].message.count("속초") == 0


def test_the_outcome_counter_is_exposed_for_trend_tracking() -> None:
    import os

    from app.llm import counters, extract_with_llm

    original = os.environ.pop("GOOGLE_API_KEY", None)
    before = counters["no_key"]["count"]
    try:
        # 키가 없으면 LLM을 호출하지 않고 no_key로 끝난다. 이 경로도 카운터에 집계돼야 한다.
        extract_with_llm("2026-09-25부터 2박, 속초 성인 두 명")
    finally:
        if original is not None:
            os.environ["GOOGLE_API_KEY"] = original

    assert counters["no_key"]["count"] == before + 1
