# AI 도우미 정책 임베딩과 고객 대화 E2E suite

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- AI 도우미는 "조건을 해석하고 Spring API 결과만 안내한다"는 경계를 코드 주석과 설계 문서에만 두고 있었다. extract 노드가 LLM으로 조건을 해석하더라도 그 결과가 곧바로 조회·추천으로 이어지기 때문에, 정책을 코드로 검증 가능한 형태로 그래프 안에 두지 않으면 위반을 런타임에 잡을 수 없었다.
- 고객 대화 입력·누락 질문·적용 버튼을 확인하는 수단이 일회성 브라우저 조작뿐이었다. 대화 패널 변경이 예약 검색 흐름에 영향을 주는 부분을 영구 suite로 남겨두지 않으면, 이후 LLM 연결이나 정책 변경 시 회귀를 알 수 없었다.

## 구현 범위

### 정책 (services/concierge)

- `app/policy.py`를 새로 작성했다.
  - `POLICY_TEXT`: "AI는 Spring Boot 예약 API가 반환한 객실·가격·잔여 수만 안내한다. 임의 가격·재고·예약 가능 여부를 만들지 않고 예약 생성·결제·취소를 실행하지 않는다." 추천 응답의 `policy` 필드로 내보낸다.
  - `REQUIRED_FIELDS`·`missing_fields(criteria)`: `region`·`checkIn`·`checkOut`·`adults` 중 빈 문자열·0·None을 누락으로 계산해 라벨 목록을 반환한다. 기존 `check_missing`의 중복 구현을 이 함수로 대체했다.
  - `assert_no_forbidden_fields(criteria)`: `payment`·`paymentMethod`·`confirm`·`reservationId`·`roomId`가 조건에 들어가면 `ValueError`를 던진다. 도우미가 다루지 않는 필드가 LLM 해석 결과에 섞여도 조회 단계까지 가지 않는다.
  - `assert_offers_from_api(offers)`: 추천 후보가 `roomTypeName`·`ratePlanName`·`total`·`remaining`을 그대로 들고 있는지 확인한다. 어느 하나라도 비어 있으면 추천을 만들지 않는다.
- `app/graph.py`는 `check_missing`·`search_inventory`·`recommend` 세 노드를 이 모듈 기반으로 다시 썼다. `ConciergeState`에 `policy: str`을 추가했고, `recommend`가 `POLICY_TEXT`를 상태에 채운다. 그래프 노드 순서와 라우팅 조건은 변경하지 않았다.
- `app/main.py`의 `ChatResponse`에 `policy: str`을 추가했고, `ValueError`는 400(정책 위반)으로, 나머지 예외는 기존 502로 매핑했다. 정책 위반을 502로 감싸면 도우미가 "조회 실패"로 회피하는 것과 같으므로 코드를 나눴다.

### 고객 웹 (apps/web)

- `test/concierge-panel.spec.ts`를 새로 작성했다. `playwright.config.ts`가 서버를 자동 기동하지 않으므로 기존 suite와 같이 사용자가 시작한 4000을 그대로 쓰고, API와 `/chat`을 `page.route`로 제어한다.
  - 추천 적용 → AI 후보를 예약 카드에 복사하지 않고 `GET /api/availability`가 새로 발생하는지 확인. 발생한 응답이 200인지 검사하고 결과 화면의 객실 선택 버튼이 노출되는지 본다.
  - 누락 질문(ASK) 응답에서 안내 문구가 표시되는지, 390×844에서 가로로 넘치지 않는지 확인.
  - `/chat` 502 실패에서 `role="alert"`로 직접 검색 안내가 나오고 종료하는지 확인.
- 기존 `latest-availability-request.test.ts`와 `customer-booking-journey.spec.ts`는 변경하지 않았다.

### 테스트 (services/concierge)

- `tests/test_policy.py`를 새로 작성했다. 누락 필드 계산 3종, 정책 문자열 내용, 금지 필드 4종 거부, 후보 필수 필드 누락 거부, 정상 후보 허용을 검증한다. 기존 `test_graph.py` 3건은 그대로 둔다.

## 자동 검증

- Python: `pytest tests` 9건 종료 코드 0(기존 3건 + 정책 6건). `services/concierge` 디렉터리에서 실행한다.
- 관리자·고객 웹 TypeScript: `npx tsc --noEmit -p tsconfig.app.json` 종료 코드 0.
- Playwright: `test/concierge-panel.spec.ts` 3건 종료 코드 0(5.1s). 추천 적용·누락 질문 390px·502 오류 안내.
- Playwright 전체 회귀: 28 passed / 5 failed. 실패 5건은 `toss-test-payment.spec.ts` 2건·`customer-reservation-management.spec.ts` 2건·`customer-booking-journey.spec.ts` 1건이다. `apps/web/test/concierge-panel.spec.ts`를 작업 트리에서 제외한 상태에서 같은 5건이 동일하게 실패해 이번 변경과 무관함을 확인했다. 원인은 외부 SDK 스크립트 로딩(`https://js.tosspayments.com/v2/standard`)과 세션 상태 타이밍이며, 본 세션의 정책·대화 패널 변경 경로와 겹치지 않는다.
- Docker: `docker compose build concierge` 종료 코드 0. `docker compose up -d --no-deps concierge`로 컨테이너를 다시 만들고 `/health` 200 `UP`를 확인했다.
- 라이브: `POST /chat`에 "2026-09-25부터 2박, 속초 성인 두 명 조식 포함"을 보내 200. 응답의 `policy`가 `POLICY_TEXT` 전문이고, `offers` 1건(디럭스 오션 · 조식 포함 · total 380000 · remaining 7)이 Spring `GET /api/availability`의 같은 조건 응답과 정확히 일치한다. Spring API의 실제 가격·잔여 수와 AI 후보가 다를 수 없는 구조다.
- 라이브 정책 가드: 조건에 `payment`를 넣고 `/chat`를 호출하면 **400** `{"detail":"AI는 payment 조건을 처리할 수 없습니다."}`. 정책 위반이 502로 회피되지 않는다.
- 정책 가드를 라이브로 확인하느라 만든 요청 이외에는 고객 데이터를 변경하지 않았다.

## 미검증 항목

- 실제 외부 LLM 연결. `OPENAI_API_KEY`·`ANTHROPIC_API_KEY`·그에 준하는 비밀값은 환경에 없고 저장소에 넣지 않는다. 정책 가드는 LLM 출력이 들어올 자리인 `criteria`·`offers`에 그대로 적용되지만, 실제 LLM 응답 지연·비용·할루시네이션은 확인하지 않았다.
- 정책 위반 400의 관리자 알림. 고객 웹은 400과 502를 같은 "도우미에 연결하지 못했습니다" 안내로 처리한다. 400 본문을 구분해 표시하려면 고객 웹의 에러 처리를 추가해야 한다.
- Playwright 실패 5건의 원인 제거. 외부 SDK 로딩 정책과 세션 타이밍을 다루는 별도 작업이 필요하다.
- `compose.yaml`의 `CONCIERGE_` 환경 변수 전달. 정책 모듈은 환경 변수를 읽지 않으므로 추가한 항목이 없다.
- 대화 이력의 영구 보관. `ConciergeState`는 요청 단위이고 DB에 저장하지 않는다.
