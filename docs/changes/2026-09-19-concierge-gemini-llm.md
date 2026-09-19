# AI 도우미 Gemini LLM 연결

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- 도우미는 정해진 한국어 정규식으로만 조건을 추출했다. "다음 주 토요일부터 3박 할건데 제주도에 어른 넷이랑 아이 둘"처럼 정규식에 없는 표현을 놓쳤고, 사용자는 같은 조건을 반복해서 입력해야 했다.
- `GOOGLE_API_KEY`를 루트 `.env`에 추가해 Gemini를 연결할 수 있게 됐다. LLM은 조건 해석만 담당하고 가격·빈 객실·예약 확정은 여전히 Spring Boot에 있다.

## 구현 범위

### LLM 클라이언트 (`services/concierge/app/llm.py` 신규)

- `google-genai==2.24.0`를 `requirements.txt`에 추가했다. `langgraph`·`fastapi`와 같이 정확한 버전을 고정한다.
- 모델은 `gemini-3.6-flash`. `gemini-2.5-flash`는 API가 404 `no longer available to new users`로 거부한다.
- `is_configured()`는 `GOOGLE_API_KEY` 유무로 LLM 사용 가능 여부를 알린다. 키가 없으면 정규식 폴백만으로 도우미가 동작한다.
- `extract_with_llm(message)`이 Gemini에 JSON 스키마·시스템 지시문을 전달해 조건을 추출한다.
  - 스키마는 단일 타입만 쓴다. `{"type": ["string", "null"]}` 같은 union 타입을 쓰면 라이브러리가 검증에 실패해 빈 응답이 내려온다. 빈 값을 "추출하지 못함"으로 해석하도록 `_coerce`가 빈 문자열·0·`False`를 `None`으로 바꾼다. `bool`은 `int`의 하위 타입이므로 정수 분기보다 먼저 판정한다.
  - `additionalProperties: false`를 빼야 Gemini가 지시된 키를 그대로 반환했다. 이 제약을 넣으면 모델이 키를 `destination`·`check_in`처럼 임의로 바꿔서 내려왔다.
  - 호출·파싱·검증 어느 단계에서든 실패하면 `None`을 반환해 폴백으로 넘긴다. 외부 장애를 도우미 중단으로 번역하지 않는다.
- `extract_with_patterns(message, criteria)`는 기존 `extract_conditions`의 정규식 로직을 그대로 옮겼다. 명시 날짜·`N박`·"다음 주 토요일"·숫자 단위를 해석한다.
- `merge(message, criteria)`가 LLM을 먼저 시도하고 `None`이면 정규식으로 내려간다. LLM 결과의 `None`은 "추출하지 못함"이므로 기존 조건을 유지하고 추출한 값만 덮어쓴다.
- `guard(criteria)`는 LLM 출력이라도 `assert_no_forbidden_fields`로 `payment`·`confirm`·`reservationId`·`roomId`를 거른다.

### 그래프

- `app/graph.py`의 `extract_conditions`를 `merge(...)` → `guard(...)` 두 줄로 바꿨다. 노드 순서·라우팅·정책 가드는 그대로다.
- 정규식 도우미(`_number`·`_parse_dates`·`_nights`·`REGIONS`·`NUMBER_WORDS`)는 `llm.py`로 옮기고 `graph.py`에서 제거했다. 기존 `test_graph.py`는 `extract_conditions`·`check_missing`을 호출하므로 동작을 유지한다.

### 컨테이너

- `compose.yaml`의 `concierge` 서비스에 `GOOGLE_API_KEY: ${GOOGLE_API_KEY:-}`를 추가했다. 루트 `.env`의 값을 컨테이너로 넘기고, 키가 없으면 빈 문자열로 둬 폴백 동작을 유지한다.

## 자동 검증

- Python: `pytest tests` 17건 종료 코드 0(기존 그래프 3 + 정책 6 + LLM 8). 로컬 `.venv`와 컨테이너 모두 `google-genai==2.24.0`을 설치했다.
- Docker: `docker compose build concierge` 종료 코드 0. `docker compose up -d --no-deps concierge`로 컨테이너를 다시 만들고 `/health` 200 `UP`를 확인했다. 컨테이너의 `GOOGLE_API_KEY`가 루트 `.env`의 값과 일치한다.
- 라이브(정규식으로는 불가능한 자연어): `POST /chat`에 "2026-09-25부터 2박, 속초 성인 두 명 조식 포함해 주세요"를 보내 **200**. `criteria`가 `region: 속초, checkIn: 2026-09-25, checkOut: 2026-09-27, adults: 2, breakfastIncluded: true`로 추출됐고, `offers` 1건이 내려왔다.
- 라이브(금지 필드): 조건에 `payment`를 넣고 `/chat`를 호출하면 **400** `{"detail":"AI는 payment 조건을 처리할 수 없습니다."}`. LLM 경로라도 정책 가드가 동작한다.
- 라이브(누락 질문): "다음 주 토요일부터 3박 할건데 제주도에 어른 넷이랑 아이 둘이랑 갈거야 조식도 먹고 싶어"를 보내 **200** `nextAction: ASK`. `region: 제주도, adults: 4, children: 2, breakfastIncluded: true`만 추출되고 날짜가 비어 "체크인 날짜, 체크아웃 날짜를 알려주세요."를 물었다. 정규식은 "어른 넷"·"아이 둘"을 해석하지 못했던 표현이다.
- 교차 검증: 같은 조건으로 Spring `GET /api/availability`(hotelId `11000000-0000-0000-0000-000000000001`, checkIn 2026-09-25, checkOut 2026-09-27, adults 2)를 호출해 비교했다. 도우미의 `offers[0]`와 Spring 응답의 조식 포함 offer가 `roomTypeName`·`ratePlanName`·`total`·`remaining`에서 **`MATCH true`**. 금액·잔여 수를 LLM이 만들 가능성이 구조적으로 없다.
- 정책 위반 400을 확인하느라 만든 요청 이외에는 고객 데이터를 변경하지 않았다.

## 미검증 항목

- LLM 지연·비용·할루시네이션의 지속 측정. 일회성 라이브 호출로만 확인했다. 응답은 수 초 안에 돌아왔지만 부하·할당량 상황은 재지 않았다.
- LLM 출력 스키마 위반의 장기적 안정성. 현재는 키·타입·날짜 형식·지점 화이트리스트를 검증하고 어긋나면 전체 결과를 버려 폴백하지만, 이것이 모든 할루시네이션을 막지는 못한다.
- 정책 위반 400의 고객 웹 안내. 고객 웹은 400과 502를 같은 "도우미에 연결하지 못했습니다"로 처리한다.
- Playwright 실패 5건(toss SDK 로딩·세션 타이밍)의 원인 제거. 이 작업과 무관하다.
- 영문 입력 지원. 한국어 입력만 다뤘다.
- 대화 이력의 영구 보관. `ConciergeState`는 요청 단위다.
