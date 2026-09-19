# AI 도우미 정책 위반 안내와 LLM 품질 측정

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- `app/main.py`는 정책 위반(`ValueError`)을 400으로 분리했지만 고객 웹 `concierge-panel.tsx`는 400과 502를 같은 "도우미에 연결하지 못했습니다"로 처리한다. 사용자가 "결제해 줘"처럼 도우미가 다루지 않는 요청을 보내면 실제로는 도우미가 정상 응답한 건을 연결 장애로 오해하게 된다.
- Gemini 연결은 일회성 라이브 호출로만 확인했다. LLM 지연·빈 응답·스키마 위반(할루시네이션)이 지속 관측되지 않으므로, 외부 장애가 정규식 폴백으로 묻히는 구간을 운영 중에 알 수 없다.

## 완료 기준

1. 고객 웹이 `/chat` 400을 502와 구분하는 안내를 표시하고, 390px에서 가로로 넘치지 않는다.
2. concierge가 LLM 호출·파싱·검증 단계의 실패와 소요 시간을 구조화된 로그로 남긴다.
3. `pytest`가 LLM 품질 측정 분기를 검증한다.
4. Playwright `concierge-panel.spec.ts`가 400 안내와 502 안내를 모두 영구 확인한다.
5. `tsc --noEmit`과 Playwright suite가 종료 코드 0이다.

## 설계

### 1. 고객 웹: 400과 502 구분 (`apps/web/src/components/concierge-panel.tsx`)

- 기존: 응답이 `ok`가 아니면 `throw new Error()` → catch에서 "도우미에 연결하지 못했습니다. 직접 객실 검색을 이용해 주세요."
- 변경: 응답 상태별로 안내를 나눈다.

  | 상태 | 의미 | 안내 |
  |---|---|---|
  | 400 | 정책 위반. 도우미가 조건을 거부 | "도우미가 처리할 수 없는 조건입니다. 객실 검색에서 직접 선택해 주세요." |
  | 그 외 | 연결 실패 | "도우미에 연결하지 못했습니다. 직접 객실 검색을 이용해 주세요." (기존) |

- 400 본문의 `detail`을 그대로 노출하지 않는다. `detail`은 서버 정책 메시지이지만 사용자 행동 안내가 아니며, 향후 메시지를 바꿀 때 고객 웹과 결합되는 것을 막는다. 상태 코드만으로 두 안내 중 하나를 선택한다.
- 에러 문구에 `role="alert"`을 유지하고, 390px에서 넘치지 않는지 확인한다.

### 2. concierge: LLM 품질 측정 (`services/concierge/app/llm.py`)

- `extract_with_llm`이 LLM 단계별 결과를 로그로 남긴다. 파일·stdout이 아닌 구조화된 형식을 쓴다.

  | 측정 항목 | 의미 |
  |---|---|
  | `outcome` | `success`·`schema_rejected`·`unparsable`·`api_error`·`not_configured`·`no_key`·`import_error` |
  | `elapsed_ms` | Gemini 호출 + 파싱 + 검증의 합 |

- 측정은 LLM 비활성화·폴백 동작과 무관하게 동작해야 한다. `is_configured()`가 거짓이면 `not_configured`를, Gemini API 장애면 `api_error`를, 응답 텍스트가 JSON이 아니면 `unparsable`을, 스키마가 어긋나면 `schema_rejected`를 기록한다.
- LLM 응답 콘텐츠·토큰 수·요청 ID는 비밀값이 아니지만 운영 로그에서만 의미가 있으므로 측정 갯수를 최소한으로 유지하고 메시지 원문은 로그에 남기지 않는다.
- 외부 장애를 도우미 중단으로 번역하지 않는 기존 동작은 그대로 둔다. 측정은 실패 분기를 가리지 않는다.

### 3. 테스트

- `tests/test_llm.py`: `measure_llm_outcome`이 키가 없을 때 `not_configured`를 반환하는지, 잘못된 스키마가 들어오면 `schema_rejected`를 반환하는지 검증한다. 실제 Gemini 호출은 하지 않는다.
- `apps/web/test/concierge-panel.spec.ts`: 기존 502 시나리오를 두 개로 나눈다. 400은 "도우미가 처리할 수 없는 조건입니다."를, 502는 기존 "도우미에 연결하지 못했습니다."를 각각 확인한다.

## 검증 계획

- Python: `services/concierge`에서 `pytest tests`를 실행해 기존 17건 + 측정 분기 케이스가 종료 코드 0인지 확인.
- TypeScript: `apps/web`에서 `npx tsc --noEmit -p tsconfig.app.json`을 실행해 종료 코드 0인지 확인.
- Playwright: 사용자가 시작한 4000을 그대로 쓰고 `apps/web/test/concierge-panel.spec.ts`만 실행해 종료 코드 0인지 확인.
- 라이브: concierge 9000의 `/health` 200과 `/chat` 정상 응답을 확인. 고객 데이터는 변경하지 않는다.

## 미검증 예정 항목

- LLM 지연·비용의 부하 상황. 일회성 측정으로는 할당량·동시 호출 상황을 재지 못한다.
- 로그의 영구 보관·집계. 측정을 남기는 것까지만 하고 대시보드는 다루지 않는다.
- 400 안내의 영문 전환. 고객 웹의 한국어·영문 전환은 예약 흐름에만 있고 대화 패널은 단일 언어다.
- 정책 위반 400의 관리자 알림. 별도 작업이다.
