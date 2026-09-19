# AI 도우미 정책 위반 400 안내와 LLM 품질 측정

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- `app/main.py`가 정책 위반(`ValueError`)을 400으로 분리했지만 고객 웹 `concierge-panel.tsx`는 400과 502를 같은 "도우미에 연결하지 못했습니다"로 처리했다. 사용자가 "결제해 줘"처럼 도우미가 다루지 않는 요청을 보내면, 도우미가 정상적으로 정책을 거절한 건을 연결 장애로 오해했다.
- Gemini 연결은 일회성 라이브 호출로만 확인했다. LLM 지연·빈 응답·스키마 위반이 지속 관측되지 않으므로, 외부 장애가 정규식 폴백으로 묻히는 구간을 운영 중에 알 수 없었다.

## 구현 범위

### 고객 웹 (apps/web)

- `src/components/concierge-panel.tsx`의 `submit`이 `/chat` 응답 상태에 따라 안내를 나눈다.
  - **400**(정책 위반): `도우미가 처리할 수 없는 조건입니다. 객실 검색에서 직접 선택해 주세요.`
  - **그 외**(연결 장애): 기존 `도우미에 연결하지 못했습니다. 직접 객실 검색을 이용해 주세요.`를 유지한다.
  - 서버 `detail` 원문을 그대로 노출하지 않는다. `detail`은 서버 정책 메시지이지 고객 행동 안내가 아니며, 원문을 노출하면 향후 서버 메시지와 고객 웹이 결합된다. 상태 코드만으로 두 안내 중 하나를 선택한다.
  - 400 안내도 502와 같이 `requestVersion !== criteriaVersion.current` 검사를 거쳐 늦게 도착한 응답이 새 조건에 덮어씌워지지 않게 한다.
- `src/styles.css`의 `.concierge-error`에 `line-height:1.6;overflow-wrap:break-word`를 추가했다. 안내문이 길어져 390px에서 단어 중간이 잘리거나 가로로 넘치지 않는다.

### concierge (services/concierge)

- `app/llm.py`에 LLM 한 번 호출의 결과와 소요 시간을 측정하는 분기를 추가했다. 정규식 폴백 동작은 그대로 둔다.
  | `outcome` | 의미 |
  |---|---|
  | `success` | 스키마까지 통과해 조건을 추출 |
  | `schema_rejected` | 스키마·지점 화이트리스트·날짜 형식이 어긋나 전체 결과를 버림 |
  | `unparsable` | 응답이 JSON이 아니거나 파싱에 실패 |
  | `empty_response` | 파싱은 됐으나 `null` 등 비어 있음 |
  | `api_error` | Gemini 호출이 예외로 끝남 |
  | `no_key` | `GOOGLE_API_KEY`가 없어 LLM을 시도하지 않음 |

- `extract_with_llm`은 호출마다 `logger.info("llm.outcome model=%s outcome=%s elapsed_ms=%s", ...)`로 남긴다. 처음엔 실패할 때만 `logger.debug`로 남겼으나 성공률·지연의 지속 수집이 안 됐고, 기본 로그 레벨에서는 실패마저 숨었다. 그래서 전체 outcome을 INFO로 올렸다. message 원문은 쓰지 않는다.
- `extract_with_llm`과 `measure_llm_outcome`은 같은 `_run_llm(call)`을 쓴다. Gemini 호출을 두 번 수행하는 부작용을 막기 위해 측정과 추출을 한 번의 호출로 묶었다. `_run_llm`은 `perf_counter` 기반 `elapsed_ms`를 함께 반환한다.
- `measure_llm_outcome(call)`은 호출 콜러블을 받아 측정만 잰다. message 원문은 로그에 남기지 않는다. `no_key`일 때는 LLM을 호출하지 않는다.
- `app/main.py`가 `logging.basicConfig(level=logging.INFO, ...)` 뒤 `logging.getLogger("app").setLevel(logging.INFO)`로 로거를 구성한다. uvicorn은 access 로그만 자체 구성하고 root 로거는 기본 WARNING에 핸들러가 없다. 앱 로거를 따로 두지 않으면 INFO가 lastResort에서 버려져 운영 로그에 측정이 나타나지 않는다.
- `empty_response`는 실제 `google-genai`의 빈 응답 동작을 정확히 반영한다. 라이브러리는 빈 결과에 `None`을 주고 `_strip_code_fence(None)`은 `AttributeError`를 던지므로, 이 단계는 `unparsable`이 되고 `empty_response`는 명시적인 `null` 본문에만 해당한다. `import_error`는 측정 분기에서 제외했다. Gemini 호출 전 import가 실패하면 측정 콜러블이 만들어지지 않으므로 `api_error`로 잡히지 않지만, 이 경로는 컨테이너 빌드 검증으로 다룬다.

### 테스트

- `services/concierge/tests/test_llm.py`에 7종을 추가했다. `no_key`·`schema_rejected`·`unparsable`·`api_error`·`empty_response`·`success`·INFO 로그 단정. `_Response` 스텁만 쓰고 실제 Gemini는 호출하지 않는다. 매 케이스가 `GOOGLE_API_KEY`를 원래 값으로 복구한다.
- `apps/web/test/concierge-panel.spec.ts`에 400 안내 시나리오를 추가했다. 390×844에서 서버 `detail` 원문이 아니라 고객 행동 안내가 노출되고 가로로 넘치지 않는지 확인한다. 기존 502 시나리오는 그대로 둔다.

## 자동 검증

- Python: `.venv`에서 `pytest tests` **25건** 종료 코드 0(기존 그래프 3 + 정책 6 + LLM 8 → LLM 16).
- 고객 웹 TypeScript: `npx tsc --noEmit -p tsconfig.app.json` 종료 코드 0.
- Playwright: `test/concierge-panel.spec.ts` **4건** 종료 코드 0(6.7s). 추천 적용·390px ASK·502 안내·400 안내.
- 라이브: concierge 9000 이미지를 새 코드로 재빌드한 뒤 `POST /chat`에 "2026-09-25부터 2박, 속초 성인 두 명 조식 포함"을 보내 **200**. `criteria`가 `region: 속초, checkIn 2026-09-25, checkOut 2026-09-27, adults 2, breakfastIncluded true`로 추출됐다.
- 라이브 측정: 같은 호출에서 `docker logs`에 `INFO app.llm llm.outcome model=gemini-3.6-flash outcome=success elapsed_ms=3392.39`가 찍혔다. 재호출에서도 `outcome=success elapsed_ms=2826.29`가 찍혔다. uvicorn access 로그와 같은 stdout에 나타나므로 별도 로그 레벨 설정 없이 운영 로그에서 지속 수집된다.
- 라이브 정책: 조건에 `payment`를 넣고 `/chat`를 호출하면 **400**. 이때 비로소 도우미가 정책을 거절한다. `payment` 없이 "결제해 주고 예약 확정해 줘"만 보내면 LLM이 결제 조건으로 해석하지 않아 200으로 빠진다. 이 동작은 의도적이다. `assert_no_forbidden_fields`는 `criteria`에 들어온 금지 필드를 걸러내고, 자연어의 암시까지 사전 차단하지는 않는다. 고객 웹의 400 안내는 서버가 거절했을 때 정확한 안내로 연결된다.

## 미검증 항목

- LLM 지연·비용·할루시네이션의 집계·대시보드. 측정을 INFO 로그로 남기는 것까지만 했고 외부 로그 수집·대시보드는 다루지 않았다.
- 측정의 부하 상황. 일회성 라이브 호출 2건이므로 할당량·동시 호출 상황을 재지 못했다.
- 정책 위반 400의 영문 전환. 대화 패널은 한국어 단일 언어다.
- 자연어 암시까지 사전 차단하는 stronger 정책 가드. "결제해 줘"가 200으로 빠지는 현재 동작을 유지한다.
- Playwright 실패 5건(toss SDK 로딩·세션 타이밍)의 원인 제거. 이 작업과 무관하다.
- 영문 입력 지원. 한국어 입력만 다뤘다.
- 대화 이력의 영구 보관. `ConciergeState`는 요청 단위다.
