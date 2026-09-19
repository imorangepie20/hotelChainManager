# hotelChainManager 하네스·스킬·플러그인 현황

기준일: 2026-09-20. 현재 작업 PC의 저장소, 사용자 스킬 디렉터리, 플러그인 manifest, Codex 설정의 플러그인 활성화 항목 및 이번 세션의 사용 가능 목록을 대조했다. 설치일이나 모든 과거 세션의 호출 이력을 조사한 문서는 아니다.

## 1. 요약과 확인 수준

이 프로젝트의 기본 하네스는 **AGENTS.md + 프로젝트 문서 + 작업별 스킬 + 변경 범위에 맞는 검증**으로 구성된다. `harness/`는 B2B-STM에서 가져온 이식 원문이다. 현재 저장소에 별도의 `.claude/agents/`, `.claude/skills/`, 루트 `CLAUDE.md` 기반 호텔 전용 에이전트 팀은 없다.

| 구분 | 현재 확인 결과 | 해석 |
|---|---|---|
| 프로젝트 하네스 | 루트 AGENTS.md, 프로젝트 원칙·현황·설계·계획·변경 기록 | 호텔 업무와 개발 절차의 기준 |
| 프로젝트 로컬 스킬 | Impeccable 1개, 스킬 버전 4.3.1 | 현재 PC에 설치됨. Git 제외 경로 |
| 사용자 공통 스킬 | `~/.agents/skills`에 58개 | 여러 프로젝트가 공유하며 전부 호텔 프로젝트 필수는 아님 |
| 사용자 Codex 스킬 | `~/.codex/skills` 바로 아래 2개 | humanizer, i-have-adhd. 세션 노출 여부는 서로 다름 |
| 시스템 스킬 | `~/.codex/skills/.system`에 6개 | 사용자 추가 설치 수에 포함하지 않음 |
| 명시 활성 플러그인 | 사용자 config.toml에서 15개 `enabled = true` | 설치 manifest도 확인. 전체 기능 실행 성공을 뜻하지 않음 |
| 추가 세션 제공 플러그인 | sites, plugin-management | 캐시·스킬 노출 확인. 위 15개 활성화 항목에는 없음 |
| 캐시만 확인한 플러그인 | openai-templates | 설치 캐시 외 활성·호출 가능 여부 미확인 |

이 문서에서 **설치 확인**은 파일·manifest 존재, **활성 설정**은 명시 설정, **세션 제공**은 현재 도구·스킬 목록 노출, **사용 기록**은 프로젝트 문서에 남은 수행 근거를 뜻한다. 기능 실행 검증은 별도이며 이번 작업에서는 설치·연결·훅 실행을 수행하지 않았다.

## 2. 프로젝트 하네스 구조와 사용 흐름

| 구성 | 역할과 근거 |
|---|---|
| [루트 AGENTS.md](../../AGENTS.md) | 프로젝트 경계, 조사 순서, 구현·검증·문서화 규칙 |
| [핵심 원칙](../project-rules/core-principles.md), [문서 접근 순서](../project-rules/doc-access-order.md) | 일자 재고와 실제 객실 분리, Spring Boot의 가격·재고·예약 확정 권한, 문서 조사 순서 |
| [프로젝트 개요](../overview/project-brief.md), [현재 개발 상태](../overview/current-development-context.md) | 제품 범위, 구현 상태, 검증 범위, 다음 작업 |
| [전체 구현 설계](../architecture/full-site-implementation-design.md), [의사결정](../decisions/decision-log.md) | 관련 아키텍처·업무 관계·결정 근거 |
| `docs/superpowers/specs/`, `docs/superpowers/plans/`, `docs/plans/` | 설계와 구현·조사 계획 보관. 폴더명만으로 특정 스킬 호출을 증명하지는 않음 |
| `docs/changes/` | 변경 이유, 검증 결과, 미검증 범위와 후속 작업 |
| [SDTPL_ADM/AGENTS.md](../../SDTPL_ADM/AGENTS.md) | 관리자 하위 작업 시 설치된 Next.js 문서를 먼저 확인하도록 지정 |
| [SDTPL_ADM/CLAUDE.md](../../SDTPL_ADM/CLAUDE.md) | 관리자 디렉터리에 있는 별도 지침 파일. 루트 호텔 하네스와 구분 |
| [이식 하네스 원문](../../harness/README.md) | 재사용 구조·스킬 요구·템플릿·과거 환경 검증 기록 |

작업은 원칙·현황·개요 → 관련 설계·업무 흐름 → 변경 기록 → 하위 지침·구현·테스트 순서로 조사한다. 설계·계획·완료 기준을 기록한 뒤 구현하며, 직접 테스트와 필요한 타입·빌드 검사를 먼저 수행한다. DB/API·키보드·모바일 상호작용 검증은 변경 범위에 맞춘다. 전체 테스트·브라우저 회귀는 사용자 요청 또는 변경 영향이 요구할 때 실행한다.

### 핵심 및 조건부 스킬

[이식 manifest](../../harness/harness-manifest.yaml)가 선언한 아래 스킬은 모두 현재 세션 목록에서도 확인된다. 이는 적용 후보의 확인이며 모든 작업에서 일괄 실행한다는 의미가 아니다.

| 구분 | 스킬 | 목적 |
|---|---|---|
| 핵심 | using-superpowers | 세션 시작 시 적용할 스킬 판단 |
| 핵심 | brainstorming | 기능·동작 설계 전 의도·범위·선택 정리 |
| 핵심 | writing-plans, executing-plans | 다단계 계획 작성과 실행 |
| 핵심 | test-driven-development | 기능·버그의 의미 있는 실패 테스트 |
| 핵심 | systematic-debugging | 원인 조사 후 수정 |
| 핵심 | verification-before-completion | 완료 주장 전 검증 근거 확인 |
| 조건부: 구조도 | archify | 아키텍처·업무 흐름·상태 전이 시각화 |
| 조건부: UI | ui-styling, ui-ux-pro-max, designlang:extract-design | UI 구현·사용성 검토·디자인 추출 |
| 조건부: 하네스 | harness:harness | 하네스 감사·설계·유지보수 |
| 조건부: 리뷰 | requesting-code-review, receiving-code-review | 독립 검토와 피드백 평가 |
| 조건부: 병렬 작업 | dispatching-parallel-agents, subagent-driven-development | 독립 작업의 위임·조율. 실제 세션의 위임 허용 범위를 따라 사용 |

## 3. 프로젝트 로컬 추가 구성: Impeccable

| 항목 | 확인 결과 |
|---|---|
| 스킬 위치 | `.agents/skills/impeccable/SKILL.md` |
| 스킬 버전 | frontmatter `metadata.version: 4.3.1` |
| 실행기 버전 표기 | `scripts/VERSION`: `0.1.5`. 스킬 버전과 별도 |
| 역할 | 프런트엔드 설계·리뷰·접근성·반응형·타이포그래피·색상·애니메이션·디자인 문서화 |
| 실행 파일 | `scripts/impeccable.cmd`, `scripts/bin/windows-x64/impeccable.exe` 존재 |
| 보조 에이전트 정의 | `impeccable_asset_producer`, `impeccable_documenter`, `impeccable_finish_reviewer`, `impeccable_manual_edit_applier`의 TOML 파일 |
| Codex 훅 | `.codex/hooks.json`의 `PostToolUse`와 `Stop` |
| 훅 내용 | Edit·Write·apply_patch 뒤 `impeccable hook`, 종료 시 같은 훅. 설정 timeout은 각각 5초·30초 |
| 관련 로컬 상태 | `.impeccable/config.json`, `.impeccable/review/` 존재 |
| Git 경계 | `.agents/`, `.codex/`, `.impeccable/`는 루트 `.gitignore`에 등록됨 |

스킬·실행 파일·훅 설정의 존재를 확인했다. 훅이 현재 Codex에서 실제 실행되는지, 보조 에이전트가 실제 등록·호출되는지, 검사 결과가 정상인지는 이번에 검증하지 않았다. 저장소를 새로 clone하는 것만으로 이 로컬 구성이 복원되지는 않는다.

사용자 전역에도 `impeccable`이 있어 같은 이름이 두 경로에서 노출된다. 이 프로젝트에 기록한 4.3.1은 **프로젝트 로컬 파일 기준**이며, 호출할 때는 세션이 제공한 스킬 경로를 확인한다.

## 4. 사용자 공통 스킬 58개

설치 루트는 `~/.agents/skills/`이다. 아래 항목은 각각 하위 `SKILL.md` 존재와 현재 세션 노출을 확인했다. 분류는 탐색 편의를 위한 것이며, 각 스킬의 실제 적용 조건은 해당 SKILL.md를 따른다. `archify`의 metadata 버전은 2.17이며 나머지 버전은 이 표에서 일괄 추정하지 않는다.

| 분류 | 스킬 |
|---|---|
| 개발 절차·계획·협업 (13) | using-superpowers, brainstorming, writing-plans, executing-plans, test-driven-development, systematic-debugging, verification-before-completion, requesting-code-review, receiving-code-review, dispatching-parallel-agents, subagent-driven-development, using-git-worktrees, finishing-a-development-branch |
| 변경 범위·조사·품질 (8) | karpathy-guidelines, investigate-first, lean-build, migration, safe-refactor, surgical-patch, verify-and-stop, diagnosing-superpowers |
| 디자인·시각화 (9) | archify, banner-design, brand, design, design-system, impeccable, slides, ui-styling, ui-ux-pro-max |
| 웹·React·Vercel (8) | deploy-to-vercel, vercel-cli-with-tokens, vercel-composition-patterns, vercel-optimize, vercel-react-best-practices, vercel-react-native-skills, vercel-react-view-transitions, web-design-guidelines |
| 글·스킬·도구 (6) | humanizer, writing-guidelines, writing-skills, find-skills, gepeto, pinokio |
| Caveman 계열 (14) | cavecrew, caveman, caveman-commit, caveman-compress, caveman-discover, caveman-evidence-review, caveman-explore, caveman-help, caveman-learn, caveman-manage, caveman-optimize, caveman-review, caveman-setup, caveman-stats |

Caveman 관련 스킬 설치는 Caveman Cloud 계정·게이트웨이·실험 연결의 증거가 아니다. Vercel 관련 스킬 설치도 이 호텔 프로젝트의 Vercel 배포 이력을 뜻하지 않는다.

### 사용자 Codex 경로와 시스템 스킬

| 경로 | 파일로 확인한 스킬 | 현재 세션 |
|---|---|---|
| `~/.codex/skills/` | humanizer, i-have-adhd | humanizer는 노출됨. i-have-adhd는 제공 목록에 없음 |
| `~/.codex/skills/.system/` | imagegen, openai-docs, plugin-creator, review-agent, skill-creator, skill-installer | review-agent를 제외한 5개가 제공 목록에 있음 |

`humanizer`는 사용자 공통 경로와 Codex 경로에 중복 설치되어 있다. `karpathy-guidelines`는 공통 스킬과 아래 플러그인 스킬로 각각 제공된다. 이름이 비슷해도 버전·내용·실행 경로가 같다고 가정하지 않는다.

## 5. 플러그인 목록과 버전

버전은 로컬 `plugin.json`을 읽어 확인했다. 캐시 기본 구조는 `~/.codex/plugins/cache/<공급원>/<이름>/<버전>/`이며 manifest는 `.codex-plugin/plugin.json` 또는 `.claude-plugin/plugin.json`에 있다. diagram-design의 여러 플랫폼 manifest는 플러그인 1개로 센다.

### 명시 활성화된 15개

다음은 `~/.codex/config.toml`의 해당 플러그인 항목에서 `enabled = true`를 확인한 목록이다. 아래 역할은 제공 도구·스킬 범위의 요약이며 프로젝트에서 사용 완료했다는 뜻이 아니다.

| 플러그인 ID | 버전 | 역할·제공 항목 |
|---|---|---|
| harness@harness-marketplace | 1.2.0 | `harness:harness`: 하네스 감사·구성·유지보수 |
| designlang@designlang | 13.3.0 | `designlang:extract-design`, `designlang:source-command-dna`: 디자인 언어 추출·비교 |
| andrej-karpathy-skills@karpathy-skills | 1.0.0 | `andrej-karpathy-skills:karpathy-guidelines`: 변경 범위와 검증 기준 |
| diagram-design@diagram-design | 2.6.33 | `diagram-design:diagram-design`: 구조도·업무·데이터 시각화 |
| codex-app-tools@openai-bundled | 0.1.4 | Codex 작업·사이드바·자동화·파일 패널 등 앱 도구 |
| browser@openai-bundled | 26.911.61220 | 브라우저 관련 구성. 개별 도구와의 매핑·실행은 미검증 |
| chrome@openai-bundled | 26.911.61220 | Chrome 관련 구성. 개별 도구와의 매핑·실행은 미검증 |
| unified-computer-use@openai-bundled | 26.911.61220 | 현재 세션 `mcp__cua_repl` 브라우저 제어 도구 |
| computer-use@openai-bundled | 26.911.61220 | `computer-use:computer-use` 스킬. 현재 CUA의 네이티브 앱 제어는 비활성으로 안내됨 |
| visualize@openai-bundled | 1.0.37 | `visualize:visualize`: 대화 내 시각화·인터랙티브 도구 |
| documents@openai-primary-runtime | 26.909.61513 | `documents:documents`: Word 문서 생성·수정·렌더 검증 |
| pdf@openai-primary-runtime | 26.909.61513 | `pdf:pdf`: PDF 생성·분석·렌더 검증 |
| spreadsheets@openai-primary-runtime | 26.909.61513 | 파일 스프레드시트 작업 및 `excel-live-control` |
| presentations@openai-primary-runtime | 26.909.61513 | PowerPoint·Google Slides 대상 발표 자료 작업 |
| template-creator@openai-primary-runtime | 26.909.61513 | 개인 재사용 산출물 템플릿 스킬 생성 |

### 그 외 캐시와 세션 제공 항목

| 플러그인 ID | 버전 | 확인 수준 |
|---|---|---|
| sites@openai-curated-remote | 0.1.65 | 캐시와 sites-building·sites-hosting·sites-preview-troubleshooting 스킬 노출 확인. 명시 활성 15개와 별도 |
| plugin-management@openai-curated-remote | 0.1.0 | 캐시와 plugin-management 스킬 노출 확인. 명시 활성 15개와 별도 |
| openai-templates@openai-curated-remote | 0.1.1 | 캐시 manifest만 확인. 현재 스킬 목록에서 별도 항목 미확인 |

위 버전은 최신 온라인 버전이 아니라 **이 PC에서 확인한 버전**이다. 설치 시점·마켓플레이스 갱신 여부·다른 PC의 설치 상태는 확인하지 않았다.

### 추천 목록에만 있는 미설치 항목

현재 세션에서 다음 16개는 설치되지 않은 추천 플러그인으로 안내됐다: Dropbox, Box, Codex Security, Figma, GitHub, Gmail, Google Calendar, Google Drive, Linear, Notion, OpenAI Developers, Outlook Calendar, Outlook Email, SharePoint, Slack, Teams.

추천 표시를 설치·계정 연결·권한 부여로 해석하지 않는다.

## 6. 호텔 프로젝트에서 확인한 사용 근거

| 대상 | 근거 | 확인 범위 |
|---|---|---|
| 이식 하네스 적용 | [프로젝트 기반 변경 기록](../changes/2026-09-10-project-foundation.md) | 호텔용 AGENTS.md·원칙·현황·예약 설계 작성 기록 |
| 설계·계획 흐름 | `docs/superpowers/specs/`, `docs/superpowers/plans/` 및 변경 기록 | 절차의 산출물 존재. 개별 스킬 호출 로그 전수 확인은 아님 |
| Archify | [관리자 참조 분석](../architecture/admin-reference-analysis.md) | 구조도 9/9, 오류·경고 0, 네 데스크톱 크기 검증 기록. 이번에 재실행하지 않음 |
| Impeccable | 프로젝트 SKILL.md, 실행기, 보조 에이전트, Codex 훅 | 로컬 설치·설정 근거. 실제 UI 검사 성공 이력은 이번 조사에서 확정하지 않음 |
| harness:harness | 이번 현황 문서화 작업 | SKILL.md의 현황 감사·불일치 구분 절차 참조 |
| designlang·기타 플러그인 | 현재 설정·manifest·세션 목록 | 설치·제공 상태 확인. B2B-STM의 사용 기록을 호텔 프로젝트 실적으로 옮기지 않음 |

React·Next.js·Spring Boot·PostgreSQL·FastAPI·LangGraph·Docker Compose·Playwright는 제품 및 개발·검증 도구다. Codex 스킬·플러그인 수에 포함하지 않는다. 특히 LangGraph 예약 컨시어지는 고객 대상 제품 AI이며, 개발 에이전트 작업 절차인 하네스와 별도다.

## 7. 이식 원문과 현재 상태의 차이

| 원문 내용 | 현재 해석 |
|---|---|
| manifest `source_project: B2B-STM` | 이식 출처. 현재 프로젝트는 hotelChainManager |
| designlang 13.2.0 및 과거 commit | 현재 플러그인 manifest는 13.3.0. 과거 CLI 검증·commit을 현재 설치 검증으로 쓰지 않음 |
| Archify 2.17 및 과거 folder hash | 현재 metadata도 2.17. 현재 폴더 hash·Git commit까지 같다는 보장은 없음 |
| `reference_theme_is_mutable: false` | 호텔 루트 AGENTS.md는 SDTPL_ADM을 호텔 업무에 맞게 수정하도록 허용함 |
| `docs/architecture/system-outline.md` | 현재 저장소에 없음. 실제 전체 구현 설계 등 관련 문서를 확인해야 함 |
| `docs/business-logic/operating-scenarios.md` | 현재 해당 디렉터리·파일 없음. 실제 설계·개요·의사결정에서 업무 흐름을 조사함 |
| manifest의 Archify guide `docs/harness/archify-guide.md` | 실제 이식 가이드는 [harness/archify-guide.md](../../harness/archify-guide.md) |
| 원문의 `designlang:setup`, `designlang:extract` 명령 | B2B-STM 재현 예시. 호텔 프로젝트에서 실행 가능한 명령으로 검증하지 않음 |
| harness 스킬의 Claude 에이전트 팀 생성 절차 | 현재 저장소에 해당 팀 정의 없음. 스킬 설치 자체가 호텔 전용 팀 구축을 뜻하지 않음 |

`harness/` 원문은 출처·이식 참고용으로 유지한다. 호텔 작업에는 현재 AGENTS.md와 실제 프로젝트 문서를 우선 적용하며, 설치 목록은 이 문서의 기준일을 확인한다.

## 8. 재확인과 인수인계

1. 프로젝트 AGENTS.md와 현재 개발 상태를 읽는다.
2. 로컬 `.agents/skills/impeccable`, `.codex/hooks.json` 및 Git 제외 여부를 확인한다.
3. 사용자 스킬 루트별 `SKILL.md`를 세고 현재 세션 제공 목록과 대조한다.
4. 사용자 config.toml에서는 플러그인 ID와 활성화 값만 확인하고, 버전은 각 plugin.json에서 읽는다. 전체 설정·토큰·계정 정보는 문서에 복사하지 않는다.
5. 새 환경에서 필요한 도구만 설치한 뒤 최소 실행으로 동작을 따로 확인하고 결과를 변경 기록에 남긴다. 이 문서는 설치 스크립트나 환경 잠금 파일이 아니다.

### 이번 검증과 남은 범위

- 확인: 저장소 규칙·이식 원문, 스킬 디렉터리 수, Impeccable 버전·훅·에이전트 정의, 플러그인 활성화 항목·manifest 버전, 세션 노출, Archify 과거 사용 근거.
- 미검증: 모든 스킬의 실행·트리거, 플러그인 외부 연결·인증, Impeccable 훅 실행, 모든 과거 호출 이력, 온라인 최신 버전, 새 PC 재현.
- 다음 작업: 설치나 동작 점검이 필요할 때 해당 항목만 최소 실행하고, 설치·활성·사용 기록을 각각 갱신한다.
- 문서 작성 계획: [현황 문서화 계획](../plans/2026-09-20-harness-skill-plugin-inventory.md).
