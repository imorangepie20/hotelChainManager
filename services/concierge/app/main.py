from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .graph import workflow

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


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    try:
        result = workflow.invoke({"message": request.message, "criteria": request.criteria})
    except Exception as error:
        raise HTTPException(status_code=502, detail="예약 정보를 조회하지 못했습니다.") from error
    return ChatResponse(reply=result["reply"], nextAction=result["next_action"], criteria=result["criteria"], offers=result.get("offers", []))
