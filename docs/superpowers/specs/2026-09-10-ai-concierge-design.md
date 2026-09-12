# AI 예약 도우미 설계

## 목적
고객이 자연어로 지점·숙박일·인원·조식 조건을 말하면, `services/concierge`의 LangGraph가 조건을 관리하고 Spring Boot 예약 API가 반환한 실제 판매 가능 객실만 제안한다. AI 서비스는 예약 생성·결제·가격 계산을 하지 않는다.

## 경계
- 고객 웹은 AI 도우미 대화 패널을 제공하고, 추천을 선택하면 기존 예약 바의 지점·날짜·인원을 갱신한 뒤 객실 검색으로 연결한다.
- FastAPI는 `POST /chat`에서 대화 상태와 다음 행동을 반환한다. 개발 포트는 `9000`이다.
- FastAPI는 Spring Boot `GET /api/hotels`, `GET /api/availability`만 호출한다. 계산된 `total`, `remaining`, `roomTypeName`, `ratePlanName`을 그대로 전달한다.
- Spring Boot만 재고·정원·요금·예약 가능 여부를 판정하고, 고객 웹만 기존 예약 API로 임시 확보·결제를 요청한다.

## LangGraph 상태
`extract_conditions`가 한국어 입력에서 기본 조건을 갱신한다. `check_missing`은 지점·체크인·체크아웃·성인 수를 확인한다. 누락값이 있으면 `ask_missing`으로 끝난다. 조건이 완성되면 `search_inventory`가 API를 조회하고, `compare_offers`가 조식 조건을 반영해 최대 3개 후보를 정리한다. 결과는 `recommend`에서 질문과 후보·예약 화면 적용용 조건으로 반환한다.

초기 버전은 외부 LLM 키 없이도 실행되도록 정해진 한국어 조건 패턴을 추출한다. 이후 LLM·정책 임베딩을 연결해도 검색·추천 노드는 API 결과만 사용해야 한다.

## 완료 기준
1. FastAPI와 LangGraph가 Docker Compose에서 9000 포트로 실행된다.
2. “다음 주 토요일에 속초, 성인 두 명, 조식 포함” 같은 입력에서 부족한 체크아웃을 묻고, 답변 뒤 API 후보를 반환한다.
3. 후보의 가격·잔여 객실은 API 응답과 같으며 임의 수치가 없다.
4. 고객 웹에서 추천 적용 후 기존 객실 검색과 예약 흐름이 이어진다.
