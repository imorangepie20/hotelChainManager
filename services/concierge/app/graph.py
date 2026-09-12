from __future__ import annotations

import os
import re
from datetime import date, timedelta
from typing import Any, Literal, TypedDict

import httpx
from langgraph.graph import END, START, StateGraph

REGIONS = ("속초", "설악산", "제주도")
NUMBER_WORDS = {"한": 1, "하나": 1, "두": 2, "세": 3, "네": 4, "다섯": 5}


class ConciergeState(TypedDict, total=False):
    message: str
    criteria: dict[str, Any]
    missing: list[str]
    offers: list[dict[str, Any]]
    reply: str
    next_action: Literal["ASK", "RECOMMEND"]


def _number(text: str, label: str) -> int | None:
    matched = re.search(rf"{label}\s*(\d+)\s*명", text)
    if matched:
        return int(matched.group(1))
    for word, value in NUMBER_WORDS.items():
        if re.search(rf"{label}\s*{word}\s*명", text):
            return value
    return None


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


def _nights(text: str) -> int | None:
    matched = re.search(r"(\d+)\s*박", text)
    return int(matched.group(1)) if matched else None


def extract_conditions(state: ConciergeState) -> dict[str, Any]:
    text = state["message"]
    criteria = dict(state.get("criteria", {}))
    for region in REGIONS:
        if region in text:
            criteria["region"] = region
    adults = _number(text, "성인")
    children = _number(text, "(?:아동|아이|어린이)")
    rooms = _number(text, "객실")
    if adults is not None:
        criteria["adults"] = adults
    if children is not None:
        criteria["children"] = children
    if rooms is not None:
        criteria["rooms"] = rooms
    if "조식" in text:
        criteria["breakfastIncluded"] = "포함" in text or "있" in text
    check_in, check_out = _parse_dates(text)
    if check_in:
        criteria["checkIn"] = check_in
    if check_out:
        criteria["checkOut"] = check_out
    return {"criteria": criteria}


def check_missing(state: ConciergeState) -> dict[str, Any]:
    criteria = state["criteria"]
    labels = {"region": "지점", "checkIn": "체크인 날짜", "checkOut": "체크아웃 날짜", "adults": "성인 인원"}
    missing = [label for key, label in labels.items() if not criteria.get(key)]
    return {"missing": missing}


def route_after_validation(state: ConciergeState) -> Literal["ask_missing", "search_inventory"]:
    return "ask_missing" if state["missing"] else "search_inventory"


def ask_missing(state: ConciergeState) -> dict[str, Any]:
    joined = ", ".join(state["missing"])
    return {"reply": f"정확한 추천을 위해 {joined}을 알려주세요.", "next_action": "ASK"}


def search_inventory(state: ConciergeState) -> dict[str, Any]:
    criteria = state["criteria"]
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
    return {"offers": offers[:3]}


def recommend(state: ConciergeState) -> dict[str, Any]:
    offers = state["offers"]
    if not offers:
        return {"reply": "조건에 맞는 판매 가능 객실을 찾지 못했습니다. 날짜 또는 인원을 바꿔 주세요.", "next_action": "RECOMMEND"}
    names = ", ".join(offer["roomTypeName"] for offer in offers)
    return {"reply": f"실시간 조회 결과 {names}을 제안합니다. 표시된 금액과 잔여 객실은 예약 화면에서 다시 확인할 수 있습니다.", "next_action": "RECOMMEND"}


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
