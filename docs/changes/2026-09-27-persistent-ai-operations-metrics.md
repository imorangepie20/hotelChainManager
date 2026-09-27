# AI 운영 지표 영구 저장·시계열 (2026-09-27)

## 상태

- 구현·집중 테스트·로컬/배포 교차 서비스 검증 완료.
- 기준 계획: `docs/overview/current-development-context.md`의 AI 운영 후속 작업과
  `docs/plans/admin-menu-roadmap.md` 8번.

## 변경 이유

기존 `/metrics/llm`은 프로세스 메모리 카운터라 concierge 재시작 때 초기화됐고 관리자 화면이
인증 없이 concierge를 직접 읽었다. 호출 품질을 기간별로 비교하고 정책 위반을 함께 보려면 서버
권한 검증을 거치는 영구 이벤트와 명확한 서울 시간 경계가 필요했다.

## 구현

- V70 `ai_telemetry_event`에 `LLM_CALL`·`POLICY_VIOLATION`을 append-only로 저장한다.
  UUID와 SHA-256 payload hash가 같은 재전송은 성공으로 처리하고, 같은 UUID의 다른 payload는
  409로 거부한다. DB CHECK가 event shape, 6개 outcome, 지연 상한, hash 형식을 고정한다.
- concierge는 event ID·발생 UTC·model·outcome·elapsed만 내부 Spring 수집 API로 보낸다.
  메시지·criteria·응답·고객 식별자·API key는 전송하거나 저장하지 않는다. 250ms timeout의
  fail-open이므로 지표 장애가 채팅 응답을 바꾸지 않는다.
- Spring 집계 API는 `HQ_ADMIN` 세션을 재검증하고 24개 시간 버킷 또는 7·30개 서울 일 버킷을
  `[fromInclusive,toExclusive)`로 반환한다. 빈 버킷과 6개 outcome도 0으로 채운다.
- 원본은 90일 보존한다. 정리 작업은 `FOR UPDATE SKIP LOCKED`, 10,000행 batch, 실행당 최대
  10 batch로 제한한다.
- 관리자 화면은 24시간·7일·30일 전환, 모델 목록, 전체 호출, 가중 평균 지연, 정책 위반,
  outcome 표, 접근 가능한 시계열 차트와 동일 데이터 표를 제공한다.
- Zorin 고객 nginx는 `/concierge/chat`만 노출하고 IP당 분당 10회·동시 2개 제한을 적용한다.
  기존 무인증 `/metrics/llm`은 공개 프록시에서 차단한다.

## 검증

- `services/concierge`: `python -m pytest tests -q` — 39 passed.
- `services/api`: `mvnw -Dtest=AiOperationsMetricsIntegrationTest,AiTelemetryIngestIntegrationTest test`
  — 10 passed. V70 신규 적용, HQ/지점/무세션 권한, 24H·7D·30D 서울 경계, 가중 평균,
  중복·충돌, 90일 cutoff를 확인했다.
- `SDTPL_ADM`: AI 운영 Playwright 8 passed, `next build` 성공.
- `apps/web`: `pnpm run build` 성공.
- 로컬 실제 compose에서 `/chat` 200 뒤 영구 이벤트가 2→3으로 증가했다. concierge 재시작과
  API 재시작 뒤에도 3건이 유지됐다. 고정 UUID를 내부 수집 API에 두 번 보내도 3→4로 한 건만
  증가했다.
- `53dd505`를 원격 `main`에 푸시하고 Zorin compose를 재빌드·배포했다. 배포 검증에서 API·웹·
  관리자·concierge 헬스, Cloudflare 터널, 실제 chat→telemetry→PostgreSQL 왕복이 모두 통과했다.
- 배포 DB 이벤트는 API·concierge 재시작 전후 `1→1`로 유지됐다. 공개 관리자 AI 운영 화면은
  `200`, 무인증 집계 API는 `401`, 비공개 concierge 지표 경로는 `404`를 반환했다.

## 제한과 후속 작업

- 수집은 고객 응답 보호를 위해 fail-open이다. 250ms 안에 내부 API가 응답하지 않으면 해당
  이벤트는 유실될 수 있고 구조화 경고만 남는다. durable outbox는 별도 작업이다.
- 배포 관리자 경로와 접근 제어는 확인했다. 실제 HQ 세션으로 기간을 바꾸며 차트 값을 육안 확인하는
  브라우저 검증은 자동 Playwright의 동일 계약으로 대체했으며, 필요하면 운영 계정 점검 절차에 포함한다.
