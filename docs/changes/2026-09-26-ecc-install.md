# ECC(affaan-m/ECC) 프로젝트 로컬 설치

날짜: 2026-09-26

## 변경 이유

사용자가 `https://github.com/affaan-m/ECC`의 설치를 지시했다. 이 작업 폴더에는 ECC가 설치돼 있지 않았다. ECC는 에이전트 하네스 성능 최적화 시스템으로 계획·테스트·구현·검토·검증·기억 워크플로우와 68개 에이전트, 292개 스킬, 94개 명령을 제공한다. 기존 하네스(루트 `AGENTS.md` + `docs/` + Impeccable + `.codex/hooks.json`)와 충돌하지 않게 프로젝트 로컬에 설치해야 했다.

## 근본 원인

설치를 시도하자마자 두 가지 제약이 드러났다.

1. **타깃 제약.** `--target opencode`는 `.opencode/dist/index.js`·`plugins`·`tools` 컴파일 결과를 요구하고 `OPENCODE_CONFIG_DIR`·`XDG_CONFIG_HOME/opencode`·`~/.config/opencode/` 중 하나에 설치한다. `claude`·`codex`·`qwen`·`hermes`·`openclaw` 타깃은 작업 폴더 바깥(`~/.claude/`·`~/.codex/`·`~/.qwen/`·`~/.hermes/`·`~/.openclaw/`)에 쓴다. 현재 세션의 작업 폴더 권한은 바깥 쓰기를 허용하지 않았다.
2. **종속성 누락.** `git clone` 직후 `install.ps1`이 `js-yaml`을 찾지 못했다. 저장소를 복제만 하면 npm 종속성이 설치되지 않기 때문이다.

## 변경 내용

- `.tmp/ecc-install/ECC`: ECC 저장소를 복제했다. `VERSION`은 2.2.2, 커밋 `e482e579415fde18357cafce70f177ae19fd7f03`이다. `.tmp/`는 `.gitignore` 제외 경로다.
- `.tmp/ecc-install/ECC/node_modules`: `npm install --ignore-scripts --no-audit --no-fund`로 종속성 212개를 설치했다. `install.ps1`이 종속성 누락을 감지하면 자동으로 같은 명령을 실행하도록 돼 있다.
- `.agents/`: `node .tmp/ecc-install/ECC/scripts/install-apply.js --target antigravity --profile minimal --no-hooks`를 실행했다.
- `.agents/ECC.md`: 설치 범위, 적용 순위, 알려진 충돌, 사용 자산, 검증 범위, 유지보수 방법을 지정하는 진입 지침 파일.
- `docs/plans/2026-09-26-ecc-install.md`: 설계, 충돌 분석, 타깃·프로필 선택 근거, 완료 기준.
- `docs/harness/installed-harness-skills-plugins.md`: 기준일 2026-09-26으로 갱신. 요약표에 ECC 행 추가, 9절 신설, 재확인 절차와 검증 범위 갱신.
- `docs/overview/current-development-context.md`: ECC 설치 항목을 최상단에 추가.

## 설치 결과

| 모듈 | 파일 | 위치 |
|---|---|---|
| `rules-core` | 122 | `.agents/rules/<언어>-<주제>.md`. 디렉터리 구조를 평탄화했다 |
| `agents-core` | 68 | `.agents/agents/*.md` |
| `commands-core` | 94 | `.agents/workflows/*.md` |
| `skill-unified-memory` | 1 | `.agents/skills/unified-memory/SKILL.md` |
| `workflow-quality` | 102 | `.agents/skills/<스킬>/...` 48종 |
| `platform-configs` | 0 | 파일을 복사하지 않는다 |

`.agents/ecc-install-state.json`에 `schemaVersion: ecc.install.v1`, `source.repoVersion: 2.2.2`, `source.repoCommit: e482e57…`, `hookConsent: "declined"`와 연산 387개·파일별 SHA-256이 기록됐다.

## 데이터·권한·업무 흐름 영향

- 객실 유형별 일자 재고와 실제 객실 번호 분리, Spring Boot의 가격·재고·예약 확정 권한은 변경하지 않았다. ECC는 개발 에이전트 작업 절차일 뿐 제품 AI가 아니다.
- ECC 자산과 루트 `AGENTS.md`·프로젝트 문서가 충돌하면 호텔 규칙이 우선한다. `.agents/ECC.md` 2절에 순위와 구체적 충돌(최소 커버리지 80% 요구, TDD 의무화, 파일·함수 크기 권장)을 적었다.
- ECC 에이전트는 조회 결과 안내와 검토에만 쓴다. AI가 가격·재고·예약을 확정하지 않는다는 프로젝트 경계를 그대로 유지한다.
- 루트 `AGENTS.md`의 문서 접근 순서를 변경하지 않았다. ECC `AGENTS.md` 블록을 루트에 추가하지 않았다.

## 검증 결과

- `git hash-object AGENTS.md` → `865f1944a69b7b1d7d993cd0e5534318de16830a`. 커밋된 값과 동일. **루트 지침을 덮어쓰지 않았다.**
- `.agents/ecc-install-state.json`의 연산 387개 `contentSha256`을 `Get-FileHash -Algorithm SHA256`으로 비교해 **0건 불일치**.
- `.codex/hooks.json`이 Impeccable PostToolUse·Stop 훅만 포함. ECC 훅이 추가되지 않았다.
- `.agents/skills/impeccable/scripts/VERSION` 0.1.5, `SKILL.md` metadata 4.3.1. 설치 전과 동일.
- `.agents/rules/common-coding-style.md`와 원본 `rules/common/coding-style.md`의 `git hash-object`가 `2f5d1c066186259e939ec6f915fae64d4df8cc8b`로 동일. 복사가 변형 없이 이뤄졌다.
- `git status --porcelain`에서 `.agents/`·`.tmp/`는 `git check-ignore`가 무시됨을 확인. Git 추적 파일 변경에 ECC 설치 항목이 없다.
- `--dry-run --json`으로 먼저 387개 연산의 쓰기 대상을 전부 검토했다. 최상위 디렉터리는 `.agents`뿐이었다.
- `install.ps1`이 `npm install --ignore-scripts`를 실행하는 것을 직접 읽어 확인했다. `--ignore-scripts`는 종속성의 preinstall·postinstall 스크립트 실행을 차단한다.
- 브라우저: 이 변경은 하네스 구성이므로 브라우저 상호작용 검증 대상이 아니다.
- `uninstall.js --target antigravity --dry-run --json`으로 롤백 경로를 확인했다. `status: planned`, 계획 388개(복사 파일 387 + `.agents/ecc-install-state.json`). 전부 `.agents/` 안이고 `.agents/skills/impeccable/`·`.agents/ECC.md`·호텔 하네스 파일은 포함되지 않았다.

## 남은 작업과 제약

- ECC 스킬·워크플로·에이전트의 실제 실행과 하네스 동작은 검증하지 않았다. 새 세션에서 `.agents/skills/`의 ECC 스킬 48종·`.agents/rules/`·`.agents/workflows/`가 노출되는지 확인해야 한다.
- `unified-memory`·`continuous-learning-v2`를 호출하지 않았다. 로컬 상태 파일이 만들어지지 않았다.
- `.agents/rules/` 122개가 "항상 로드되는 규칙"으로 인식되는지 확인하지 않았다. `.agents/ECC.md`의 적용 순위가 실제로 지켜지는지는 사용 중에 검증해야 한다.
- `.tmp/ecc-install/ECC`를 유지하고 있다. 업데이트·구성 변경·제거(`scripts/uninstall.js --dry-run` 후 실행)에 사용한다.
- 다른 PC·다른 하네스에서 같은 설치가 재현되는지 확인하지 않았다. `.agents/`는 Git 제외 경로라 새 클론에는 ECC가 없다. 재현은 `.agents/ECC.md` 5절의 절차를 따른다.
- 루트 `AGENTS.md`에 ECC 블록을 추가하지 않았으므로, 세션이 `.agents/ECC.md`를 자동으로 읽지 못할 수 있다. 작업 시 `.agents/ECC.md`를 먼저 읽어야 한다.
