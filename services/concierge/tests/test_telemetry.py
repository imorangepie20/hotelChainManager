from __future__ import annotations

import uuid

import pytest


def test_llm_event_contains_only_the_approved_operational_fields(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import telemetry

    captured: list[dict[str, object]] = []
    event_id = uuid.UUID("92000000-0000-0000-0000-000000000001")
    monkeypatch.setattr(telemetry, "_post_event", captured.append)

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
    }]
    serialized = repr(captured)
    for forbidden in ("message", "criteria", "response", "apiKey", "customer"):
        assert forbidden not in serialized


def test_policy_violation_event_does_not_include_the_rejected_input(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import telemetry

    captured: list[dict[str, object]] = []
    event_id = uuid.UUID("92000000-0000-0000-0000-000000000002")
    monkeypatch.setattr(telemetry, "_post_event", captured.append)

    telemetry.publish_policy_violation(event_id=event_id)

    assert captured == [{
        "eventId": str(event_id),
        "eventType": "POLICY_VIOLATION",
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
