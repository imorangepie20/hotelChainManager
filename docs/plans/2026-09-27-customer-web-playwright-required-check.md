# 고객 웹 Playwright CI 필수 검사 계획

> 기준: 요청의 `current-development-context.md#step-7` 앵커는 현재 문서에 없으므로 `다음 작업` 4번의 고객 웹 Playwright 34건을 대상으로 한다.

## 결정

- GitHub Actions의 안정적인 검사 이름을 `Customer Web Playwright / customer-web-playwright`로 정하고 `main` push와 모든 pull request에서 실행한다.
- 대상은 `apps/web/test/*.spec.ts` 네 파일의 Playwright 34건이다. 같은 디렉터리의 Node 단위 테스트 `*.test.ts`는 이 검사에 섞지 않는다.
- 이 34건은 API·결제·AI 응답을 `page.route`로 대체하는 브라우저 계약 테스트다. DB를 사용하는 척 별도 PostgreSQL을 띄우지 않고, DB 환경변수와 비밀값을 아예 주지 않아 공유·운영 DB 접근을 차단한다.
- 고객 웹은 전용 loopback 포트에서 production build preview로 실행한다. API·컨시어지 proxy 대상도 연결되지 않은 loopback 포트로 지정해 mock 누락이 실제 서비스로 나가지 않고 실패하게 한다.
- checkout 자격증명은 작업 트리에 유지하지 않으며 workflow에는 `contents: read` 외 권한과 secret을 제공하지 않는다.

## CI 계약

- Node 22, pnpm 9.15.9, lockfile 고정 설치, Chromium과 Linux 의존성을 사용한다.
- Playwright는 CI와 로컬 모두 `pnpm run test:e2e`로 실행한다. 기본 포트는 `4173`이며 `CUSTOMER_WEB_E2E_PORT`로 덮어쓸 수 있다.
- 자동 재시도는 0회다. 현재 suite가 결정적인 mock 기반이므로 재시도로 실패를 숨기지 않는다.
- job 제한은 10분, web server 시작 제한은 3분이다. 기준 실행은 Playwright 24.3초, 격리 Vite 기동 포함
  26.71초였으며 목표 시간은 warm E2E 1분 이내, cold browser 설치 포함 전체 job 3~6분이다.
- 실패 여부와 무관하게 HTML report, JUnit 결과, screenshot, trace, 고객 웹 server log를 14일 보존한다.

## 구현 순서와 완료 기준

1. Playwright config에 `.spec.ts` 대상, production preview web server, CI reporter와 실패 산출물 정책을 추가한다.
2. 고객 웹 package script와 GitHub Actions workflow를 추가한다.
3. 로컬에서 CI와 같은 환경변수와 명령으로 정확히 34건을 실행하고 실행 시간을 기록한다.
4. workflow YAML, 잠금 설치, 비밀값 부재, artifact 경로와 필수 검사 이름을 검토한다.
5. 변경 기록과 현재 개발 문맥에 로컬/CI 차이, 재시도·시간·완료 기준과 미검증 사항을 남긴다.

완료 조건은 로컬 34/34 통과, production build preview 사용, 전용 포트 충돌 시 즉시 실패, 실패 screenshot·trace·log 경로 존재, 저장소에 실제 비밀값이 없는 상태다. GitHub branch protection의 required status 등록은 workflow가 원격에서 최초 성공해 검사 context가 생성된 뒤 확인한다.
