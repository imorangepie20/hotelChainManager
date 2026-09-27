# AI 도우미 영구 지표·시계열 계획 (2026-09-27)

> 기준: `docs/overview/current-development-context.md`의 `다음 작업` 3번과
> `docs/plans/admin-menu-roadmap.md` 8번 AI 운영. 요청의 `#step-5`는 현재 문서의 AI 항목과
> 일치하는 앵커가 아니므로 명시된 기능 범위를 우선한다.

## 사용자 여정과 완료 기준

1. 본사 관리자는 AI 운영 화면에서 최근 24시간·7일·30일의 LLM outcome별 호출 수, 평균 지연,
   정책 위반 건수를 시간대별로 확인한다.
2. 지점 직원과 인증하지 않은 사용자는 집계 API를 읽을 수 없다.
3. concierge가 재시작돼도 이전 지표가 남고 같은 이벤트가 재전송돼도 한 번만 집계된다.
4. 지표는 원문 메시지·추출 조건·고객 식별자를 저장하지 않으며 90일이 지난 이벤트는 삭제한다.

## 데이터·시간 계약

- V70 `ai_telemetry_event`는 `event_id UUID`를 기본키로 사용한다. concierge는 이벤트마다 UUID를 한 번
  생성하고 재전송 때 같은 값을 사용한다.
- 같은 ID와 같은 payload hash는 성공으로 재생한다. 같은 ID와 다른 hash는 `409 Conflict`로 거부해
  잘못된 키 재사용을 숨기지 않는다.
- 이벤트 종류는 `LLM_CALL`과 `POLICY_VIOLATION`이다. LLM 호출만 model·outcome·elapsed_ms를 가지며
  outcome은 기존 6종을 그대로 쓴다. 정책 위반은 거부 입력이나 사유를 저장하지 않는다.
- concierge는 DB 자격 증명을 갖지 않는다. 짧은 타임아웃의 내부 Spring 수집 API로 승인된 필드만 보내고,
  수집 실패는 채팅 응답에 영향을 주지 않는다. 공유 토큰은 환경변수로만 주입한다.
- 발생 시각은 concierge가 이벤트 ID와 함께 UTC로 고정하고 서버가 미래 1분·과거 90일 범위를 검증한다.
  수집 시각은 별도 UTC `TIMESTAMPTZ`로 기록한다. 관리자 버킷 경계는 `Asia/Seoul` 기준이며 범위는
  `[fromInclusive, toExclusive)`이다.
- `24H`는 현재 서울 시각의 다음 정시까지 24개 시간 버킷, `7D`·`30D`는 다음 서울 자정까지 각각
  7개·30개 일 버킷이다. 빈 버킷과 outcome도 0으로 반환한다.
- 보존 기간은 90일이다. Spring 정리 작업은 UTC 기준 `now - 90일`보다 오래된 행만 삭제하고 경계 시각은
  보존한다.

## 구현 경계

- concierge는 LLM 결과와 전용 `PolicyViolationError`만 최소 이벤트로 보낸다. 일반 `ValueError`는 정책
  위반으로 집계하지 않는다. prompt·criteria·응답 원문은 body와 로그에 포함하지 않는다.
- Spring Boot `POST /api/internal/concierge/telemetry`는 공유 토큰과 payload를 검증하고 멱등 저장한다.
- Spring Boot `GET /api/staff/ai-operations/metrics?period=...`는 `HQ_ADMIN` 세션을 서버에서 재검증하고
  PostgreSQL을 조회 전용으로 집계한다. 기존 인증 없는 concierge `/metrics/llm`은 호환용으로만 남기며
  관리자 화면은 더 이상 사용하지 않는다.
- 응답은 여러 모델이 섞일 수 있음을 드러내도록 `models[]`를 제공한다. 호출 수와 평균은 전체 모델을
  합산하며 모델 이름을 단일 현재값으로 오인해 표시하지 않는다.
- 관리자 화면은 기간 버튼, 총 호출·가중 평균 지연·정책 위반 카드, outcome 표, 접근 가능한 시계열 차트와
  같은 데이터의 표를 제공한다.

## TDD 매핑

| 계약 | RED 대상 | GREEN 증거 |
|---|---|---|
| 수집 인증·중복·충돌 방지 | 내부 수집 API payload 재전송 | Spring 통합 테스트 |
| 재시작 보존·서울 시간 경계 | DB seed와 고정 Clock | Spring 집계 통합 테스트 |
| 90일 보존 | 경계 이전·정확한 경계 행 | 정리 서비스 통합 테스트 |
| HQ 전용 | 무세션·지점·본사 세션 호출 | API 권한 테스트 |
| outcome·정책 위반 기록 | fake HTTP publisher와 `/chat` 오류 | Python 단위 테스트 |
| 기간·시계열 UI | API mock과 390px 키보드 조작 | Playwright 테스트 |

## 중단 조건

- 지표 기록 실패가 고객 채팅을 실패시키거나 느린 수집 재시도를 기다리게 되면 구현을 중단한다.
- 원문 메시지·criteria·비밀값을 저장하거나 응답에 노출하는 설계는 채택하지 않는다.
- 가격·재고·예약 확정 권한은 기존처럼 Spring 예약 API에만 둔다.
