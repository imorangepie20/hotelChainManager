# 고객 웹 Playwright CI 필수 검사 (2026-09-27)

## 결정

- GitHub Actions 검사 이름을 `Customer Web Playwright / customer-web-playwright`로 고정하고 `main` push,
  모든 pull request, 수동 실행에서 같은 34건을 수행한다. required check 누락을 막기 위해 path filter는 두지 않는다.
- 대상은 고객 웹의 네 `*.spec.ts`에 있는 Playwright 34건이다. 같은 디렉터리의 Node 계약 `*.test.ts`는
  Playwright discovery에서 제외해 별도 테스트가 34건 집계에 섞이지 않게 했다.
- 자동 재시도는 로컬과 CI 모두 0회다. API를 모형화한 결정적 회귀이므로 첫 실패를 필수 검사 실패로 취급한다.

## 격리와 비밀값

- 이 suite는 모든 API·AI·Toss SDK 응답을 `page.route`로 대체한다. Spring API와 PostgreSQL을 사용하지 않으므로
  CI에는 DB service, DB 자격증명, Toss 키, GitHub secret을 제공하지 않는다. 불필요한 테스트 DB를 띄우지 않는 것이
  공유·운영 DB로부터의 가장 명확한 격리다.
- production build preview만 `127.0.0.1:4173`의 strict port에 띄운다. `CUSTOMER_WEB_E2E_PORT`로 포트를
  바꿀 수 있고 기존 서버를 재사용하지 않으므로 충돌이나 오래된 빌드는 즉시 실패한다.
- API와 concierge proxy 대상은 연결되지 않은 `127.0.0.1:65535`로 고정한다. mock 누락 요청은 실제 서비스로
  나가지 않고 실패한다. 이 기본값은 Playwright web server 자체에 있어 로컬과 CI에 똑같이 적용된다.
  workflow는 환경 전체를 출력하지 않고 checkout 자격증명도 Git 설정에 남기지 않는다.

## 실행과 산출물

- 로컬과 CI의 공통 명령은 `apps/web`에서 `pnpm run test:e2e`다. 이 명령은 TypeScript·Vite production build 후
  Playwright가 preview server를 자동으로 시작하고 종료한다.
- CI는 Node 22, pnpm 9.15.9, `pnpm install --frozen-lockfile`, Playwright 1.60.0 Chromium을 사용한다.
  로컬 기준은 Node 24.19.0·pnpm 11.25.0이었지만 같은 lockfile과 Chromium 1.60.0으로 결과가 같았다.
- CI와 동일한 환경변수·2 workers 실행에서 production build 포함 34/34가 34.76초, Playwright 구간은 26.2초였다.
  사전 기준 실행은 4 workers에서 24.3초였다. warm E2E 목표는 1분 이내, cold dependency·Chromium 설치를 포함한
  전체 job 목표는 3~6분이며 hard timeout은 10분이다.
- 매 실행은 list와 HTML report를 만들고 CI는 JUnit도 만든다. 실패 시 screenshot과 `trace.zip`을 보존한다.
  `playwright-report/`, `test-results/`, `playwright-run.log`, `playwright-server.log`는 성공·실패와 관계없이
  하나의 artifact로 올리고 14일 보존한다.

## 검증

- `pnpm run test:e2e`: production build 및 Chromium 34/34 통과, 총 34.76초.
- `pnpm exec playwright test --list`: Node `*.test.ts` 부작용 없이 정확히 4파일·34건.
- actionlint 1.7.12: workflow 오류 0건.
- 격리 기본값 점검 중 의도적으로 드러난 origin 불일치 실패에서 screenshot·trace가 생성됐고, config가 선택한
  base URL을 테스트 환경의 단일 origin으로 고정한 뒤 다시 34/34를 확인했다.
- 실행 뒤 4173 포트가 해제되고 JUnit·HTML report·실행/서버 log가 생성됨을 확인했다.

## 원격 완료 조건

- 저장소 변경만으로 branch protection은 자동으로 바뀌지 않는다. workflow를 원격에 반영해 최초 성공 run으로
  `customer-web-playwright` context를 만든 뒤 `main` ruleset의 required status check로 지정하고 실제 PR에서
  병합 차단 여부를 확인해야 완전히 필수화된다.
