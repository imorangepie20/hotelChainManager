# ECC(affaan-m/ECC) 프로젝트 설치

날짜: 2026-09-26

## 목적과 범위

사용자가 `https://github.com/affaan-m/ECC`의 설치를 요청했다. 이 문서는 설치 전 설계, 검증 기준, 실행 결과를 기록한다.

이 프로젝트의 기본 하네스는 루트 `AGENTS.md` + `docs/` + `.agents/skills/impeccable` + `.codex/hooks.json`이다. ECC는 그 위에 동작하는 에이전트 하네스 성능 최적화 시스템(68개 에이전트, 292개 스킬, 94개 명령, 훅, 메모리, AgentShield)을 추가한다.

## 설치 전 상태 (2026-09-26)

| 항목 | 상태 | 비고 |
|---|---|---|
| 작업 폴더 ECC 흔적 | `.opencode/`, `.claude/` 없음 | 설치된 적 없는 상태 |
| 루트 패키지 매니페스트 | `package.json` 없음 | 이 프로젝트는 Gradle·Next.js 하위 앱 구조 |
| Codex 플러그인 캐시 | `~/.codex/plugins/cache/ecc/ecc/2.2.2` 존재 | 캐시만 확인. 활성 아님 |
| `~/.codex/config.toml` | ECC 항목 없음 | 명시 활성 플러그인 15개에 ECC 미포함 |
| 사용자 전역 OpenCode | `~/.config/opencode/` 없음 | `opencode.json` 전역 설정 없음 |
| Impeccable | `.agents/skills/impeccable` 로컬 설치 | Git 제외. `.codex/hooks.json` 훅 사용 중 |
| 이식 하네스 원문 | `harness/` | B2B-STM 출처. manifest `source_project: B2B-STM` |

## 설계: 충돌·중복 분석

ECC 설치가 기존 하네스와 겹칠 수 있는 부분과 완화 방안이다.

| 충돌 후보 | 기존 | ECC | 완화 |
|---|---|---|---|
| 스킬 중복 노출 | 사용자 공통 스킬 58개에 `tdd-workflow`, `security-review`, `brainstorming`, `writing-plans`, `systematic-debugging`, `verification-before-completion` 등 이미 포함 | ECC가 같은 이름의 스킬을 추가 | 설치 경로를 프로젝트 로컬 `.agents/skills/`로 한정하고 노출 표에 중복 여부를 명시 |
| AGENTS.md 지시어 | 루트 `AGENTS.md`(호텔 규칙, 문서 접근 순서) | ECC가 `AGENTS.md`에 마크된 블록을 추가 | 원문 블록과 호텔 규칙을 같은 파일에 두지 않는다. ECC는 별도 파일로 설치하고 루트는 변경하지 않는다 |
| 훅 | `.codex/hooks.json` Impeccable PostToolUse·Stop | ECC 훅 런타임 | `--enable-hooks`를 사용하지 않는다. Impeccable 훅을 그대로 유지한다 |
| Impeccable | 로컬 4.3.1 | ECC에 `impeccable` 관련 구성 없음 | 그대로 유지 |
| 규칙(rules) | 호텔 전용 원칙 | ECC rules/common, rules/typescript | 호텔 규칙이 항상 로드되도록 덮어쓰지 않는다 |

## 설계: 설치 방식

`install.ps1` 기반 설치 방식과 npm 기반 설치 방식을 비교했다.

| 항목 | `install.ps1` (소스 체크아웃) | npm `ecc-universal` |
|---|---|---|
| 소스 코드 | 로컬에 전체 저장소 복사 필요 | 패키지만 다운로드 |
| Windows 지원 | `install.ps1` 존재 | npx 지원 |
| 커스터마이징 | 설치 후 스킬·규칙·명령 편집 가능 | 편집 어려움 |
| 검증 | 로컬 파일·스크립트 직접 검사 가능 | 패키지 내용만 검사 |

이 프로젝트는 Windows 환경이고 호텔 업무에 맞게 스킬·규칙·명령을 편집해야 한다. `install.ps1` 방식을 선택한다.

## 설계: ECC 버전

사용자가 제공한 GitHub 저장소의 기본 브랜치는 `main`이다. `git ls-remote --tags` 결과 최신 태그는 `v2.2.1`이다. 원격 `main`의 버전 파일(`VERSION`)은 `2.2.2`다. 태그는 릴리스 시점의 스냅샷이고, `main`의 2.2.2는 태깅되지 않은 후속 커밋들을 포함한다.

사용자가 저장소 링크를 제공했으므로 `main` 브랜치를 기준으로 한다. 설치 후 로컬 `VERSION` 파일로 실제 버전을 확인하고 기록한다.

## 설계: 프로필

ECC는 설치 범위를 정하는 프로필을 제공한다. 이 프로젝트의 기본 하네스가 이미 존재하므로 다음을 선택한다.

- `--profile minimal`: 룰, 에이전트, 명령, 플랫폼 config, 코어 워크플로우. 훅 런타임 제외
- 훅은 `--enable-hooks`를 주지 않는다. Impeccable 훅을 유지한다
- 필요하면 `--modules hooks-runtime`으로 나중에 추가할 수 있다

`--profile minimal`을 선택한다. 이유는 다음과 같다.

1. 이 프로젝트는 이미 Impeccable 훅을 사용 중이다. ECC 훅을 같이 사용하면 훅이 중복 실행될 수 있다.
2. `AGENTS.md`가 이미 있고 문서 접근 순서를 지정하고 있다. 전체 프로필은 루트를 덮어쓸 위험이 있다.
3. minimal은 컴포넌트를 독립적으로 사용할 수 있게 한다. 필요한 스킬만 추가할 수 있다.

## 완료 기준

1. `.tmp/ecc-install/`에 소스를 내려받고 로컬 `VERSION`이 비어있지 않다
2. `install.ps1 --profile minimal --target opencode`를 실행하고 쓰기 내역을 본다
3. `.tmp/ecc-install/` 바깥의 이 작업 폴더와 사용자 환경에 영향을 주지 않았다
4. 프로젝트 로컬 `.agents/skills/`에 설치된 ECC 스킬 목록이 비어있지 않다
5. Impeccable 스킬·`.codex/hooks.json` 훅이 변경되지 않았다
6. 루트 `AGENTS.md`가 변경되지 않았다
7. `git status`에 Git 추적 파일 변경이 없다 (Git 제외 경로 제외)
8. 이 문서의 검증 결과란에 실제로 확인한 내용을 기록했다

## 남은 작업

1. 설치 스크립트를 dry-run으로 검토한다
2. 안전한 대상 경로(작업 폴더 안)에 설치한다
3. 설치된 파일·스킬 목록을 확인한다
4. 하네스 현황·개발 컨텍스트·변경 기록 문서에 반영한다
