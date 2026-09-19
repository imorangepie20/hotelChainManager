from app.main import app


def test_the_llm_metrics_endpoint_reports_outcome_counters() -> None:
    from starlette.testclient import TestClient

    # concierge에는 세션 검증이 없으므로 compose가 127.0.0.1에만 포트를 바인딩한다.
    response = TestClient(app).get("/metrics/llm")

    assert response.status_code == 200
    payload = response.json()
    assert payload["model"] == "gemini-3.6-flash"
    for outcome in ("success", "schema_rejected", "unparsable", "empty_response", "api_error", "no_key"):
        entry = payload["outcomes"][outcome]
        assert entry["count"] >= 0
        assert entry["totalElapsedMs"] >= 0.0
        assert entry["avgElapsedMs"] >= 0.0
