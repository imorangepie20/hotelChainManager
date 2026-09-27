from __future__ import annotations

import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Literal

import httpx

logger = logging.getLogger("app.telemetry")

LlmOutcome = Literal[
    "success",
    "schema_rejected",
    "unparsable",
    "empty_response",
    "api_error",
    "no_key",
]


def publish_llm_outcome(
    *,
    model: str,
    outcome: LlmOutcome,
    elapsed_ms: float,
    event_id: uuid.UUID | None = None,
) -> None:
    """승인된 운영 필드만 보낸다. 수집 장애는 고객 채팅에 전파하지 않는다."""
    payload: dict[str, object] = {
        "eventId": str(event_id or uuid.uuid4()),
        "eventType": "LLM_CALL",
        "model": model,
        "outcome": outcome,
        "elapsedMs": round(elapsed_ms, 2),
        "occurredAt": _utc_now(),
    }
    _publish(payload)


def publish_policy_violation(*, event_id: uuid.UUID | None = None) -> None:
    """거부된 입력이나 사유 없이 정책 위반 발생 사실만 보낸다."""
    _publish({
        "eventId": str(event_id or uuid.uuid4()),
        "eventType": "POLICY_VIOLATION",
        "occurredAt": _utc_now(),
    })


def _publish(payload: dict[str, object]) -> None:
    try:
        _post_event(payload)
    except Exception as error:
        # payload에는 운영 수치만 있지만 예외 메시지에도 응답 본문이 섞일 수 있어 타입만 남긴다.
        logger.warning("telemetry.publish_failed error_type=%s", type(error).__name__)


def _post_event(payload: dict[str, object]) -> None:
    token = os.environ.get("AI_TELEMETRY_INGEST_TOKEN", "").strip()
    if not token:
        raise RuntimeError("telemetry token is not configured")
    base_url = os.environ.get("CONCIERGE_API_BASE_URL", "http://api:4080").rstrip("/")
    response = httpx.post(
        f"{base_url}/api/internal/concierge/telemetry",
        headers={"Authorization": f"Bearer {token}"},
        json=payload,
        timeout=httpx.Timeout(0.25),
    )
    response.raise_for_status()


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
