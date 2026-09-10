# 플러그인·스킬 목록

## 실제 사용한 핵심 구성

| 구분 | 이름 | 사용 시점 | 이식 방법 |
|---|---|---|---|
| 플러그인 스킬 | `harness:harness` 1.2.0 | 하네스 감사·설계·문서화 | 새 환경에서 harness 플러그인 설치 여부 확인 |
| 플러그인 스킬 | `designlang:extract-design` 13.2.0 | 웹 디자인 언어·토큰·WCAG 추출 | `Manavarya09/design-extract` 설치 또는 플러그인 연결 |
| 로컬 스킬 | `using-superpowers` | 세션 시작 시 적용할 스킬 판단 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `brainstorming` | 기능·UI·동작 설계 전 의도와 선택 확인 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `writing-plans` | 다단계 구현 계획 작성 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `executing-plans` | 승인된 계획 순차 실행 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `test-driven-development` | 기능과 버그 수정의 실패 테스트 작성 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `systematic-debugging` | 예상 밖 동작과 테스트 실패 원인 추적 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `verification-before-completion` | 완료 보고 전 최신 검증 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `ui-styling` | shadcn/Tailwind UI 구현 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `ui-ux-pro-max` | 화면 구조·타이포그래피·반응형 검토 | 사용자 스킬 디렉터리에 설치 |
| 로컬 스킬 | `requesting-code-review` | 큰 기능 완료 시 독립 검토 | 필요 프로젝트에서 사용 |
| 로컬 스킬 | `karpathy-guidelines` | 과도한 변경 방지와 검증 기준 명료화 | 필요 프로젝트에서 사용 |
| 로컬 스킬 | `archify` 2.17 | 아키텍처·workflow·sequence·data flow·lifecycle 시각화와 검증 | `tt-a1i/archify`에서 사용자 전역 설치 |

## 프로젝트 도구와 플러그인의 구분

다음은 이 프로젝트에서 사용하지만 Codex 플러그인은 아닙니다.

| 도구 | 역할 | 설치 위치 |
|---|---|---|
| Playwright | 실제 브라우저 기능·반응형 검증 | 프로젝트 dev dependency |
| Docker PostgreSQL | 개발·테스트 DB와 복원 리허설 | 로컬 Docker 환경 |
| Cloudflare Quick Tunnel | 외부 기기 임시 접근 | 로컬 CLI, 고정 배포 수단 아님 |
| `designlang` CLI | 디자인 추출 실행 파일 | `.tools/designlang`, Git 제외 |

Cloudflare 추천 플러그인 목록이 보였다는 사실은 설치나 연결을 뜻하지 않습니다. B2B-STM에서는 Quick Tunnel URL을 CLI로 사용했으며 계정 플러그인 연결을 하네스에 포함하지 않습니다.

## 현재 환경에 설치되어 있으나 기본 하네스에서 제외한 항목

문서·PDF·프레젠테이션·스프레드시트·사이트 호스팅·컴퓨터 제어·딥리서치 플러그인은 현재 Codex 환경에 존재할 수 있지만 B2B-STM 핵심 개발 흐름의 필수 구성은 아닙니다. 새 프로젝트의 산출물이나 외부 서비스 요구가 있을 때만 추가합니다.

## 설치와 버전 원칙

- 플러그인 이름, 스킬 이름, 검증한 버전을 함께 기록합니다.
- Git 저장소 기반 도구는 commit SHA를 고정합니다.
- 사용자 전역 설치와 프로젝트 runtime dependency를 분리합니다.
- 설치 후 이름만 확인하지 않고 `--version` 또는 최소 실행으로 동작을 검증합니다.
- API key, cookie, 개인 계정 정보는 하네스 문서와 템플릿에 넣지 않습니다.

### designlang 재현 명령

```powershell
npm.cmd run designlang:setup
npm.cmd run designlang:extract -- http://127.0.0.1:3101 --screenshots --out design-extract-output/example
```

B2B-STM은 GitHub commit `a9e832efe304330a7fc491511c0ca30a34bc0106`, `designlang 13.2.0`을 검증했습니다.

### Archify 재현 명령

```powershell
npx.cmd -y skills add tt-a1i/archify --skill archify --agent codex --global --copy --yes
Set-Location "$HOME\.agents\skills\archify"
node bin\archify.mjs doctor
```

B2B-STM 환경은 GitHub commit `10722002bb8777ecb639d93c49586fae4adf3ae4`, skill metadata `2.17`, folder hash `8aa4cadc3cde102db4009f23f951cb4cf1e28d61`을 검증했습니다. 상세 사용법과 산출물 경계는 [Archify 설치와 하네스 적용](archify-guide.md)을 따릅니다.
