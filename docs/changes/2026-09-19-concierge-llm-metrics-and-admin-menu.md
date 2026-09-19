# AI 도우미 LLM 측정 노출과 본사 AI 운영 메뉴

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- 이전 작업이 `llm.outcome` 측정을 `logger.info`로 남기게 했지만, 로그를 직접 grep하지 않으면 호출 수·실패율·평균 지연을 볼 수단이 없었다. `admin-menu-roadmap.md` 8번 `AI 운영`의 간격이었다.
- concierge에는 세션 검증이 없어 측정을 노출하면 누구나 읽을 수 있다. 본사만 볼 수단이 필요했다.

## 구현 범위

### concierge (services/concierge)

- `app/llm.py`가 outcome별 in-process 카운터(`count`·`elapsed_ms`)를 가진다. `extract_with_llm`이 `_record_outcome`으로 매 호출마다 더한다. 정규식 폴백 동작은 그대로다.
- `app/main.py`의 `GET /metrics/llm`이 카운터를 `count`·`totalElapsedMs`·`avgElapsedMs`로 노출한다. 프로세스 단위라 재시작하면 0부터 시작한다.
- concierge에 세션 검증이 없으므로 노출 범위는 compose의 `127.0.0.1:9000` 포트 바인딩으로 제한한다. 엔드포인트 자체에는 인증을 두지 않는다.

### 관리자 (SDTPL_ADM)

- `next.config.ts`의 rewrite가 `/concierge/:path*`를 `http://127.0.0.1:9000/:path*`로 보낸다. 기존 `/api/:path*` rewrite와 같은 방식이다.
- `src/lib/staff-api.ts`의 `getConciergeLlmMetrics`·`ConciergeLlmMetrics`·`ConciergeLlmOutcome`.
- `src/components/hotel-admin/ai-operations.tsx`가 모델·전체 호출·성공 외 호출 카드와 결과별 집계 표를 읽기 전용으로 보여준다. `HQ_ADMIN`만 새로고침·측정이 가능하고 다른 역할은 `AI 도우미 운영은 본사 관리자만 확인할 수 있습니다.`를 본다. 도우미가 응답하지 않으면 `AI 도우미 측정을 불러오지 못했습니다. 도우미가 실행 중인지 확인해 주세요.`를 보여준다.
- `src/lib/nav.ts`의 `aiGroup`이 `HQ_ADMIN`에만 노출된다. `getNavGroupsForRole`가 본사 4개 그룹을 반환한다.
- `src/app/(dashboard)/dashboard/ai-operations/page.tsx`.

### 테스트

- `services/concierge/tests/test_metrics.py`: `/metrics/llm`이 6개 outcome의 카운터를 200으로 내리는지 확인.
- `services/concierge/tests/test_llm.py`: `extract_with_llm` 호출이 카운터에 집계되는지 확인.
- `SDTPL_ADM/e2e/ai-operations.spec.ts` 4종: 메뉴 노출 권한, 결과별 호출 수·평균 지연 표시, 지점 직원 안내, 도우미 장애 안내.

## 자동 검증

- Python: `.venv`에서 `pytest tests` **27건** 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 1건 `react-hooks/set-state-in-effect`, 기존 `media-storage-status.tsx`와 같은 패턴).
- Playwright: `e2e/ai-operations.spec.ts` **4건** 종료 코드 0(9.3s).
- 라이브: concierge 이미지 재빌드 뒤 `POST /chat` 200으로 카운터 `success count=1 avgElapsedMs=3392.3`가 쌓이고, `GET /metrics/llm` 200으로 같은 값이 내려왔다. 관리자 4001의 `/concierge/metrics/llm` 200.

## 미검증 항목

- 측정의 영구 보관·시계열 집계. 프로세스 재시작으로 0으로 돌아간다. 외부 로그 수집·저장소는 다루지 않았다.
- 정책 위반 400 건수의 노출. `/chat` 400은 로그에 남지만 `/metrics/llm`에는 없다.
- 실험실·부하 상황의 지연·할루시네이션 추이. 일회성 라이브 호출 1건으로는 패턴을 볼 수 없다.
- `/metrics/llm`의 운영 배포 노출 범위. compose의 루프백 바인딩에 의존하므로 포트를 공개하는 배포에서는 별도 보호가 필요하다.
- 영문 전환. 메뉴·측정 안내는 한국어 단일 언어다.
- 390px 모바일 뷰포트에서 AI 운영 메뉴·표의 동작. Playwright 시나리오는 1280px 기본 뷰포트를 썼다.
