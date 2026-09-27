from __future__ import annotations

import uuid

import pytest


def test_llm_event_contains_only_the_approved_operational_fields(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import telemetry

    captured: list[dict[str, object]] = []
    event_id = uuid.UUID("92000000-0000-0000-0000-000000000001")
    monkeypatch.setattr(telemetry, "_post_event", captured.append)
    monkeypatch.setattr(telemetry, "_utc_now", lambda: "2026-09-27T00:00:00Z")

    telemetry.publish_llm_outcome(
        model="gemini-3.6-flash",
        outcome="success",
        elapsed_ms=123.45,
        event_id=event_id,
    )

    assert captured == [{
        "eventId": str(event_id),
        "eventType": "LLM_CALL",
        "model": "gemini-3.6-flash",
        "outcome": "success",
        "elapsedMs": 123.45,
        "occurredAt": "2026-09-27T00:00:00Z",
    }]
    serialized = repr(captured)
    for forbidden in ("message", "criteria", "response", "apiKey", "customer"):
        assert forbidden not in serialized


def test_policy_violation_event_does_not_include_the_rejected_input(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import telemetry

    captured: list[dict[str, object]] = []
    event_id = uuid.UUID("92000000-0000-0000-0000-000000000002")
    monkeypatch.setattr(telemetry, "_post_event", captured.append)
    monkeypatch.setattr(telemetry, "_utc_now", lambda: "2026-09-27T00:00:00Z")

    telemetry.publish_policy_violation(event_id=event_id)

    assert captured == [{
        "eventId": str(event_id),
        "eventType": "POLICY_VIOLATION",
        "occurredAt": "2026-09-27T00:00:00Z",
    }]


def test_telemetry_failure_is_fail_open(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import telemetry

    def fail(_payload: dict[str, object]) -> None:
        raise TimeoutError("telemetry unavailable")

    monkeypatch.setattr(telemetry, "_post_event", fail)

    telemetry.publish_llm_outcome(
        model="gemini-3.6-flash",
        outcome="api_error",
        elapsed_ms=5.0,
    )
    telemetry.publish_policy_violation()


def test_llm_extraction_publishes_the_measured_outcome(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import llm

    published: list[tuple[str, str, float]] = []
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    monkeypatch.setattr(
        llm,
        "publish_llm_outcome",
        lambda *, model, outcome, elapsed_ms: published.append((model, outcome, elapsed_ms)),
    )

    assert llm.extract_with_llm("예약 조건") is None
    assert len(published) == 1
    assert published[0][0:2] == ("gemini-3.6-flash", "no_key")


@pytest.mark.parametrize(
    "outcome",
    ["success", "schema_rejected", "unparsable", "empty_response", "api_error", "no_key"],
)
def test_every_llm_outcome_is_published(monkeypatch: pytest.MonkeyPatch, outcome: str) -> None:
    from app import llm

    published: list[tuple[str, str, float]] = []
    criteria = {} if outcome == "success" else None
    monkeypatch.setattr(llm, "_run_llm", lambda _call: (outcome, 12.5, criteria))
    monkeypatch.setattr(
        llm,
        "publish_llm_outcome",
        lambda *, model, outcome, elapsed_ms: published.append((model, outcome, elapsed_ms)),
    )

    llm.extract_with_llm("원문은 publisher에 전달하지 않는다")

    assert published == [("gemini-3.6-flash", outcome, 12.5)]


def test_only_policy_errors_are_counted_as_policy_violations(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import main
    from app.policy import PolicyViolationError
    from starlette.testclient import TestClient

    published: list[bool] = []
    monkeypatch.setattr(main, "publish_policy_violation", lambda: published.append(True))
    monkeypatch.setattr(
        main.workflow,
        "invoke",
        lambda _state: (_ for _ in ()).throw(PolicyViolationError("거부됨")),
    )

    response = TestClient(main.app).post("/chat", json={"message": "예약", "criteria": {}})

    assert response.status_code == 400
    assert published == [True]


def test_non_policy_value_error_is_not_counted_as_policy_violation(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import main
    from starlette.testclient import TestClient

    published: list[bool] = []
    monkeypatch.setattr(main, "publish_policy_violation", lambda: published.append(True))
    monkeypatch.setattr(
        main.workflow,
        "invoke",
        lambda _state: (_ for _ in ()).throw(ValueError("programming error")),
    )

    response = TestClient(main.app).post("/chat", json={"message": "예약", "criteria": {}})

    assert response.status_code == 502
    assert published == []
