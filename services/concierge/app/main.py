from __future__ import annotations

from typing import Any

import logging

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .graph import workflow
from .llm import MODEL_ID

# uvicorn은 access 로그만 자체 구성한다. root 로거는 기본 WARNING에 핸들러가 없고
# app 로거는 NOTSET이라 root의 WARNING에 억제돼 llm.outcome 측정이 운영 로그에 나타나지 않는다.
# root를 INFO로 구성하고 app 로거에 명시적으로 INFO를 줘 측정이 기본 실행에서도 노출되게 한다.
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logging.getLogger("app").setLevel(logging.INFO)

app = FastAPI(title="Hotel AI Concierge", version="0.1.0")
app.add_middleware(CORSMiddleware, allow_origins=["http://127.0.0.1:4000", "http://localhost:4000"], allow_methods=["POST"], allow_headers=["Content-Type"])


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=500)
    criteria: dict[str, Any] = Field(default_factory=dict)


class ChatResponse(BaseModel):
    reply: str
    nextAction: str
    criteria: dict[str, Any]
    offers: list[dict[str, Any]]
    policy: str = ""


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/metrics/llm")
def metrics_llm() -> dict[str, Any]:
    # 프로세스 단위 LLM 측정을 노출한다. 지연·빈 응답·스키마 위반의 추이를 볼 때 사용한다.
    # concierge에는 세션 검증이 없으므로 compose는 127.0.0.1에만 포트를 바인딩한다.
    from .llm import counters

    return {
        "model": MODEL_ID,
        "outcomes": {
            outcome: {
                "count": int(entry["count"]),
                "totalElapsedMs": round(entry["elapsed_ms"], 2),
                "avgElapsedMs": round(entry["elapsed_ms"] / entry["count"], 2) if entry["count"] else 0.0,
            }
            for outcome, entry in counters.items()
        },
    }


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    try:
        result = workflow.invoke({"message": request.message, "criteria": request.criteria})
    except ValueError as error:
        # 정책 위반(금지 필드·API 외 후보)은 도우미가 회피하지 않고 400으로 알린다.
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=502, detail="예약 정보를 조회하지 못했습니다.") from error
    return ChatResponse(
        reply=result["reply"],
        nextAction=result["next_action"],
        criteria=result["criteria"],
        offers=result.get("offers", []),
        policy=result.get("policy", ""),
    )
