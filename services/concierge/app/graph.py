from __future__ import annotations

import os
from typing import Any, Literal, TypedDict

import httpx
from langgraph.graph import END, START, StateGraph

from .llm import extract_with_patterns, guard, merge
from .policy import (
    POLICY_TEXT,
    assert_no_forbidden_fields,
    assert_offers_from_api,
    missing_fields,
)


class ConciergeState(TypedDict, total=False):
    message: str
    criteria: dict[str, Any]
    missing: list[str]
    offers: list[dict[str, Any]]
    reply: str
    next_action: Literal["ASK", "RECOMMEND"]
    policy: str


def extract_conditions(state: ConciergeState) -> dict[str, Any]:
    # LLM(우선) → 정규식(폴백) 순서로 조건을 해석한다. 어느 쪽이든 정책 가드를 통과한다.
    criteria = merge(state["message"], dict(state.get("criteria", {})))
    return {"criteria": guard(criteria)}


def check_missing(state: ConciergeState) -> dict[str, Any]:
    return {"missing": missing_fields(state["criteria"])}


def route_after_validation(state: ConciergeState) -> Literal["ask_missing", "search_inventory"]:
    return "ask_missing" if state["missing"] else "search_inventory"


def ask_missing(state: ConciergeState) -> dict[str, Any]:
    joined = ", ".join(state["missing"])
    return {"reply": f"정확한 추천을 위해 {joined}을 알려주세요.", "next_action": "ASK"}


def search_inventory(state: ConciergeState) -> dict[str, Any]:
    criteria = state["criteria"]
    # 정책상 도우미가 다루지 않는 필드가 섞여 있으면 조회하지 않는다.
    assert_no_forbidden_fields(criteria)
    with httpx.Client(base_url=os.environ.get("CONCIERGE_API_BASE_URL", "http://api:4080"), timeout=5.0) as client:
        hotels = client.get("/api/hotels").raise_for_status().json()
        hotel = next((item for item in hotels if item["region"] == criteria["region"]), None)
        if hotel is None:
            return {"offers": []}
        params = {
            "hotelId": hotel["id"], "checkIn": criteria["checkIn"], "checkOut": criteria["checkOut"],
            "adults": criteria["adults"], "children": criteria.get("children", 0), "rooms": criteria.get("rooms", 1),
        }
        offers = client.get("/api/availability", params=params).raise_for_status().json()["offers"]
    if criteria.get("breakfastIncluded"):
        offers = [offer for offer in offers if offer["breakfastIncluded"]]
    # 추천 후보는 API 응답의 금액·잔여 필드를 그대로 들고 있어야 한다.
    assert_offers_from_api(offers)
    return {"offers": offers[:3]}


def recommend(state: ConciergeState) -> dict[str, Any]:
    offers = state["offers"]
    if not offers:
        return {"reply": "조건에 맞는 판매 가능 객실을 찾지 못했습니다. 날짜 또는 인원을 바꿔 주세요.", "next_action": "RECOMMEND"}
    names = ", ".join(offer["roomTypeName"] for offer in offers)
    return {
        "reply": f"실시간 조회 결과 {names}을 제안합니다. 표시된 금액과 잔여 객실은 예약 화면에서 다시 확인할 수 있습니다.",
        "next_action": "RECOMMEND",
        "policy": POLICY_TEXT,
    }


def create_graph():
    graph = StateGraph(ConciergeState)
    graph.add_node("extract_conditions", extract_conditions)
    graph.add_node("check_missing", check_missing)
    graph.add_node("ask_missing", ask_missing)
    graph.add_node("search_inventory", search_inventory)
    graph.add_node("recommend", recommend)
    graph.add_edge(START, "extract_conditions")
    graph.add_edge("extract_conditions", "check_missing")
    graph.add_conditional_edges("check_missing", route_after_validation)
    graph.add_edge("ask_missing", END)
    graph.add_edge("search_inventory", "recommend")
    graph.add_edge("recommend", END)
    return graph.compile()


workflow = create_graph()
