from __future__ import annotations

import json
import logging
import os
import re
from datetime import date, timedelta
from time import perf_counter
from typing import Any, Callable, Literal

from .policy import assert_no_forbidden_fields

logger = logging.getLogger("app.llm")

MODEL_ID = "gemini-3.6-flash"
REGIONS = ("속초", "설악산", "제주도")
NUMBER_WORDS = {"한": 1, "하나": 1, "두": 2, "세": 3, "네": 4, "다섯": 5}

# outcome별 호출 수와 누적 소요 시간. 프로세스 단위이므로 재시작하면 0부터 시작한다.
counters: dict[str, dict[str, float]] = {
    outcome: {"count": 0.0, "elapsed_ms": 0.0} for outcome in
    ("success", "schema_rejected", "unparsable", "empty_response", "api_error", "no_key")
}

# Gemini가 채우는 조건의 스키마. LLM 출력을 신뢰하지 않고 이 형태만 받는다.
CRITERIA_KEYS = (
    "region",
    "checkIn",
    "checkOut",
    "adults",
    "children",
    "rooms",
    "breakfastIncluded",
)

_INSTRUCTION = """호텔 예약 조건을 추출한다. 반드시 아래 JSON 형식으로만 답한다. 다른 설명·마크다운은 붙이지 않는다.
- region: 속초, 설악산, 제주도 중 하나. 없으면 빈 문자열
- checkIn, checkOut: YYYY-MM-DD. 없으면 빈 문자열
- adults, children, rooms: 정수. 없으면 0
- breakfastIncluded: 조식 포함 여부. 불명하면 false
추출할 수 없는 값은 빈 값으로 둔다. 값을 새로 만들지 않는다."""

_JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "region": {"type": "string"},
        "checkIn": {"type": "string"},
        "checkOut": {"type": "string"},
        "adults": {"type": "integer"},
        "children": {"type": "integer"},
        "rooms": {"type": "integer"},
        "breakfastIncluded": {"type": "boolean"},
    },
    "required": CRITERIA_KEYS,
}


def is_configured() -> bool:
    """LLM 호출에 필요한 키가 환경에 있는지 확인한다."""
    return bool(os.environ.get("GOOGLE_API_KEY", "").strip())


# LLM 한 번 호출에서 어느 단계가 어떻게 끝났는지 측정한다.
# 정규식 폴백이 동작하더라도 이 측정은 실패한 LLM 경로를 가리키지 않는다.
LlmOutcome = Literal[
    "success",
    "schema_rejected",
    "unparsable",
    "empty_response",
    "api_error",
    "no_key",
]


def _coerce(value: Any) -> Any:
    """빈 문자열·0·False를 모두 '추출하지 못함'으로 본다."""
    if isinstance(value, bool):
        # bool은 int의 하위 타입이므로 정수 분기보다 먼저 판정한다.
        return value or None
    if isinstance(value, str):
        text = value.strip()
        return text or None
    if isinstance(value, int):
        return value or None
    return value


def _validate(payload: Any) -> dict[str, Any] | None:
    """LLM 출력을 스키마와 비교해 유효한 조건만 남긴다. 하나라도 어긋나면 전체를 버린다."""
    if not isinstance(payload, dict):
        return None
    if set(payload.keys()) != set(CRITERIA_KEYS):
        return None
    cleaned: dict[str, Any] = {}
    for key in CRITERIA_KEYS:
        value = _coerce(payload.get(key))
        if value is None:
            cleaned[key] = None
            continue
        if key == "region":
            if not isinstance(value, str) or value not in REGIONS:
                return None
        elif key in ("checkIn", "checkOut"):
            if not isinstance(value, str) or not _is_iso_date(value):
                return None
        elif key in ("adults", "children", "rooms"):
            # _coerce가 0을 None으로 바꿨으므로 여기서는 1 이상의 정수만 남는다.
            if not isinstance(value, int) or isinstance(value, bool) or value < 1:
                return None
        elif key == "breakfastIncluded":
            if not isinstance(value, bool):
                return None
        cleaned[key] = value
    return cleaned


def _is_iso_date(text: str) -> bool:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", text):
        return False
    try:
        date.fromisoformat(text)
    except ValueError:
        return False
    return True


def _strip_code_fence(text: str) -> str:
    match = re.search(r"\{.*\}", text, re.DOTALL)
    return match.group(0) if match else text


def extract_with_llm(message: str) -> dict[str, Any] | None:
    """Gemini로 조건을 추출한다. 실패·불량 출력이면 None을 반환해 폴백으로 넘긴다."""
    try:
        from google import genai
    except ImportError:
        return None

    def call() -> Any:
        client = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])
        return client.models.generate_content(
            model=MODEL_ID,
            contents=message,
            config={
                "system_instruction": _INSTRUCTION,
                "response_mime_type": "application/json",
                "response_schema": _JSON_SCHEMA,
                "temperature": 0.0,
            },
        )

    outcome, elapsed_ms, criteria = _run_llm(call)
    # 운영 로그에서 LLM 지연·빈 응답·스키마 위반이 정규식 폴백으로 묻히지 않게 남긴다.
    # message 원문은 쓰지 않는다. outcome별 카운터로 실패율·평균 지연을 집계한다.
    logger.info("llm.outcome model=%s outcome=%s elapsed_ms=%s", MODEL_ID, outcome, elapsed_ms)
    _record_outcome(outcome, elapsed_ms)
    if outcome != "success":
        return None
    return criteria


def _run_llm(call: Callable[[], Any]) -> tuple[LlmOutcome, float, dict[str, Any] | None]:
    """LLM 호출 한 번의 결과·소요 시간·추출 조건을 반환한다. 어느 단계에서 실패하든 폴백은 그대로 동작한다."""
    started = perf_counter()
    if not is_configured():
        return "no_key", _elapsed(started), None
    try:
        response = call()
    except Exception:
        return "api_error", _elapsed(started), None
    try:
        payload = json.loads(_strip_code_fence(_response_text(response)))
    except (AttributeError, ValueError, TypeError):
        return "unparsable", _elapsed(started), None
    if payload is None:
        return "empty_response", _elapsed(started), None
    criteria = _validate(payload)
    if criteria is None:
        return "schema_rejected", _elapsed(started), None
    return "success", _elapsed(started), criteria


def _record_outcome(outcome: LlmOutcome, elapsed_ms: float) -> None:
    """LLM 호출 결과를 in-process 카운터에 더한다. 실패율·평균 지연 집계용이다."""
    counters[outcome]["count"] += 1
    counters[outcome]["elapsed_ms"] += elapsed_ms


def measure_llm_outcome(call: Callable[[], Any]) -> tuple[LlmOutcome, float]:
    """LLM 호출 한 번의 결과와 소요 시간만 잰다. message 원문을 로그에 남기지 않는다."""
    outcome, elapsed_ms, _criteria = _run_llm(call)
    return outcome, elapsed_ms


def _elapsed(started: float) -> float:
    return round((perf_counter() - started) * 1000.0, 2)


def _response_text(response: Any) -> str:
    text = getattr(response, "text", None)
    if not isinstance(text, str) or not text.strip():
        return ""
    return text


def _number(text: str, label: str) -> int | None:
    matched = re.search(rf"{label}\s*(\d+)\s*명", text)
    if matched:
        return int(matched.group(1))
    for word, value in NUMBER_WORDS.items():
        if re.search(rf"{label}\s*{word}\s*명", text):
            return value
    return None


def _nights(text: str) -> int | None:
    matched = re.search(r"(\d+)\s*박", text)
    return int(matched.group(1)) if matched else None


def _parse_dates(text: str) -> tuple[str | None, str | None]:
    dates = re.findall(r"(20\d{2})[.\-/](\d{1,2})[.\-/](\d{1,2})", text)
    if len(dates) >= 2:
        parsed = [date(int(year), int(month), int(day)) for year, month, day in dates[:2]]
        return parsed[0].isoformat(), parsed[1].isoformat()
    if len(dates) == 1:
        check_in = date(int(dates[0][0]), int(dates[0][1]), int(dates[0][2]))
        nights = _nights(text)
        return check_in.isoformat(), (check_in + timedelta(days=nights)).isoformat() if nights else None
    if "다음 주 토요일" in text:
        today = date.today()
        next_monday = today + timedelta(days=(7 - today.weekday()) % 7 or 7)
        check_in = next_monday + timedelta(days=5)
        nights = _nights(text)
        return check_in.isoformat(), (check_in + timedelta(days=nights)).isoformat() if nights else None
    return None, None


def extract_with_patterns(message: str, criteria: dict[str, Any]) -> dict[str, Any]:
    """외부 키가 없거나 LLM이 실패했을 때 쓰는 정규식 폴백. 기존 동작을 그대로 유지한다."""
    extracted = dict(criteria)
    for region in REGIONS:
        if region in message:
            extracted["region"] = region
    adults = _number(message, "성인")
    children = _number(message, "(?:아동|아이|어린이)")
    rooms = _number(message, "객실")
    if adults is not None:
        extracted["adults"] = adults
    if children is not None:
        extracted["children"] = children
    if rooms is not None:
        extracted["rooms"] = rooms
    if "조식" in message:
        extracted["breakfastIncluded"] = "포함" in message or "있" in message
    check_in, check_out = _parse_dates(message)
    if check_in:
        extracted["checkIn"] = check_in
    if check_out:
        extracted["checkOut"] = check_out
    return extracted


def merge(message: str, criteria: dict[str, Any]) -> dict[str, Any]:
    """LLM을 우선으로 시도하고 실패하면 정규식으로 내려간다. 정책 가드로 마무리한다."""
    llm_result = extract_with_llm(message)
    if llm_result is not None:
        # LLM이 추출한 값만 덮어쓴다. None은 '추출하지 못함'이므로 기존 값을 유지한다.
        merged = dict(criteria)
        for key, value in llm_result.items():
            if value is not None:
                merged[key] = value
        return merged
    return extract_with_patterns(message, criteria)


def guard(criteria: dict[str, Any]) -> dict[str, Any]:
    """LLM이 넣을 수 없는 필드를 걸러낸다."""
    assert_no_forbidden_fields(criteria)
    return criteria
