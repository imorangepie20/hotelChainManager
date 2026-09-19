# 다른 프로젝트에 적용하는 개발 하네스 통합 가이드

문서 버전: 1.1 · 작성일: 2026-09-20

**이 파일 하나를 새 프로젝트의 에이전트에게 주고 “이 문서대로 적용해줘”라고 요청하면 된다.** 아래 적용 지침, 필수 작업 절차, 파일 생성 스크립트와 템플릿은 모두 본문에 포함되어 있다. 기본 하네스는 외부 스킬이나 플러그인을 설치하지 않고 적용할 수 있다. 외부 도구의 바이너리·인증·서비스 기능은 포함하지 않으며 필요할 때 별도로 설치·연결한다.

### 이 문서를 받은 에이전트의 실행 지침

사용자가 이 문서의 적용을 요청하면 다음 작업을 수행한다. 단순 요약이나 설치 목록 제시만으로 종료하지 않는다.

1. 대상 저장소의 기존 규칙·개요·구현·사용자 변경을 읽는다. 프로젝트명·스택·업무·검증 명령은 그 저장소에서 확인한다.
2. 12절에 내장된 기본 절차를 이 프로젝트의 작업 규칙으로 적용한다. 외부 스킬이 없어도 해당 절차를 직접 수행한다.
3. 필수 문서가 없으면 12절 스크립트 또는 현재 환경의 파일 편집 도구로 생성한다. 스크립트는 선택 사항이며 PowerShell이 없어도 본문에 있는 동일 내용을 작성하면 된다.
4. 기존 파일은 보존하면서 누락된 규칙만 문맥에 맞게 병합한다. 실제 경로가 다르면 기존 경로를 유지하고 진입 규칙도 맞춘다. 템플릿의 미정 항목은 조사 결과로 채우고 알 수 없는 항목은 이유를 남긴다.
5. AGENTS.md와 필수 문서 연결을 확인하고 해당 변경 범위의 검증을 수행한다. 기본 하네스 적용을 외부 플러그인 설치 때문에 중단하지 않는다.
6. 적용된 규칙·생성 또는 병합한 파일·검증 결과·미정 사항을 보고한다. 외부 도구 설치 여부는 별도로 보고한다.

기준 자료는 hotelChainManager에서 확인한 개발 절차와 당시 PC의 설치 목록이다. 호텔 업무·포트·DB·계정·배포 대상은 이식하지 않는다. 버전과 설치 상태는 **원본 환경의 2026-09-20 스냅샷**이며 새 환경이나 온라인 최신 상태를 뜻하지 않는다.

## 1. 가장 빠른 적용 방법

1. 이 파일을 새 프로젝트의 `docs/harness/portable-project-harness.md`로 복사하거나 개발 에이전트에게 첨부한다.
2. 2절의 프로젝트 정보를 기존 코드·문서에서 확인한다. 결정할 수 없는 필수 정보만 사용자에게 질문한다.
3. 기존 AGENTS.md와 문서 구조를 우선 활용하고, 없는 규칙·템플릿만 4절을 참고해 추가한다.
4. 기본 절차는 12절의 내장 규칙으로 적용한다. 5~7절의 스킬·플러그인은 있으면 활용하는 선택 확장이다.
5. 실제 설치된 도구와 저장소 명령으로 9절의 검증 표를 채운다. 실행하지 않은 검증은 미검증으로 남긴다.
6. 이미 요청된 작은 작업 하나에 조사 → 계획 → 구현 → 직접 검증 → 기록 흐름을 적용한다. 하네스 확인만을 위해 제품 기능을 새로 만들지는 않는다.

같은 PC의 다른 프로젝트는 사용자 전역 스킬을 공유할 수 있다. 다른 PC에서도 본문만으로 기본 절차를 적용할 수 있다. 외부 스킬·플러그인의 고유 기능까지 사용하려는 경우에만 해당 패키지를 준비한다.

## 2. 새 프로젝트에서 채울 정보

| 항목 | 작성 내용 |
|---|---|
| 프로젝트명·목표 | 프로젝트명과 한 문장 목적 |
| 사용자·업무 | 사용자 역할, 핵심 정상 흐름과 주요 실패 흐름 |
| 도메인 불변 조건 | 중복·동시 처리·권한·상태 전이에서 반드시 지킬 관계 |
| 최종 결정 권한 | 금액·상태·권한 등을 최종 확정하는 서비스나 저장소 |
| AI 경계 | AI가 제안·조회·실행할 수 있는 범위. AI가 없으면 해당 없음 |
| 기술 스택 | 프런트엔드·백엔드·DB·실행 환경과 실제 버전 |
| 프로젝트 격리 | 섞으면 안 되는 다른 프로젝트·DB·계정·배포 환경 |
| 참조 자산 | 경로·출처·사용 허용 범위·수정 가능 여부 |
| 환경 | 개발·테스트 실행 위치, 포트, 비밀값을 제외한 DB 식별 기준 |
| 검증 | 작업 디렉터리, 실제 명령, 성공 조건, 환경 의존 사항 |
| 범위 | 이번 목표, 제외 항목, 완료 기준, 다음 작업 |

빈 값을 추정해서 채우지 않는다. 미정 항목은 `미정: 이유와 확인 방법`으로 표시한다. 비밀번호·토큰·쿠키·실제 고객 정보는 기록하지 않는다.

## 3. 하네스 구조와 운영 원칙

하네스는 **진입 규칙 → 지속 문서 → 작업별 스킬 → 실행·검증 근거**를 연결하는 개발 절차다. 스킬은 작업 방법을 담고, 플러그인은 스킬·도구·앱 등의 묶음을 제공한다. 제품에서 실행되는 AI 에이전트·워크플로 엔진은 별도 애플리케이션 구성이다.

기존 저장소에 같은 역할의 문서가 있으면 그 경로를 유지한다. 아래는 새 저장소용 권장 구조이며 모든 디렉터리를 빈 상태로 만들 필요는 없다.

```text
AGENTS.md
docs/
  project-rules/
    core-principles.md
    doc-access-order.md
  overview/
    project-brief.md
    current-development-context.md
  architecture/              # 관련 설계가 생길 때
  decisions/                 # 중요한 결정이 생길 때
  plans/                     # 설계·계획·완료 기준
  changes/                   # 변경 이유와 검증 결과
  harness/
    portable-project-harness.md
```

- 조사 순서: 루트 규칙 → 원칙·현황·개요 → 관련 설계·업무 흐름 → 변경 기록 → 하위 규칙·구현·테스트.
- 변경 원칙: 요청 범위에 필요한 최소 변경을 수행하고 사용자 변경을 보존한다.
- 기능 검증: 직접 테스트와 필요한 타입·빌드 검사를 우선한다. 전체 회귀는 요청이나 영향 범위가 요구할 때 실행한다.
- UI 원칙: 기존 공통 컴포넌트를 우선하고, 데이터 연결·키보드·모바일 동작을 변경 범위에 맞게 확인한다.
- 완료 판단: 파일·명령·화면 존재만으로 완료라 하지 않는다. 성공 조건과 실행 결과를 함께 남긴다.
- 위임 원칙: 독립적인 하위 작업이 있고 현재 지침·사용자 요청이 허용할 때만 위임한다. 특정 도구명·모델명을 다른 런타임에 그대로 적용하지 않는다.
- 유지보수: 반복된 실패·피드백은 관련 규칙에 반영하되, 사용하지 않는 규칙과 오래된 경로를 계속 쌓지 않는다.

## 4. 복사해서 사용할 문서 템플릿

중괄호로 표시한 값을 새 프로젝트 정보로 바꾼다. 기존 파일을 덮어쓰지 말고 겹치는 규칙과 충돌 여부를 확인해 병합한다.

### 4.1 루트 AGENTS.md

```markdown
# {{프로젝트명}} 작업 규칙

## 작업 전 읽기
1. docs/project-rules/core-principles.md
2. docs/project-rules/doc-access-order.md
3. docs/overview/current-development-context.md

프로젝트 개요 → 관련 설계·업무 흐름 → 변경 기록 → 구현·테스트 순서로 조사한다.
하위 AGENTS.md도 해당 경로에서 적용한다. 문서가 없으면 기존 대체 문서를 찾아 사용한다.

## 프로젝트 경계
- 목표: {{한 문장 제품 목표}}
- {{다른 프로젝트·DB·계정·배포 대상}}과 코드·데이터·비밀값을 혼용하지 않는다.
- 사용자 변경과 요청 범위 밖 코드를 되돌리지 않는다.
- 참조 자산 {{경로}}는 {{수정·재사용 허용 범위}}에 따라 사용한다.
- 비밀값·쿠키·캐시·DB dump는 저장소에 넣지 않는다.

## 도메인 원칙
- {{반드시 보존할 데이터 관계·상태 전이·권한 규칙}}
- {{최종 상태·금액·권한 등을 확정하는 시스템}}
- {{AI의 허용 범위 또는 해당 없음}}

## 구현과 검증
- 설계·구현 계획·완료 기준을 먼저 기록한다. 작은 변경은 짧은 기록으로 충분하다.
- 버그는 원인을 조사하고 의미 있는 재현·회귀 검증을 수행한다.
- 동작 변경은 관련 실패 테스트 또는 동등한 재현 근거를 확보한다.
- 직접 테스트와 필요한 타입·빌드 검사를 우선한다.
- 전체 테스트·브라우저 회귀는 사용자 요청 또는 변경 영향이 요구할 때 실행한다.
- UI는 기존 기능성 컴포넌트를 우선하고 키보드·모바일 동작을 확인한다.
- 외부 환경에 대한 변경은 사용자가 요청한 범위와 현재 권한을 따른다.
- 파일이나 명령의 존재만으로 기능이 완료됐다고 보고하지 않는다.

## 문서화
- 문서는 한국어로 작성하고 경로·명령·식별자는 원문을 유지한다.
- 변경 이유·내용·검증 결과·미검증 항목·다음 작업을 기록한다.
- 실제로 성공한 명령만 검증 완료로 표시한다.
```

### 4.2 원칙·접근 순서·개요

`core-principles.md`에는 2절에서 정한 도메인 불변 조건, 권한 경계, 데이터 일관성, 제외 범위를 기록한다. `doc-access-order.md`에는 3절의 조사 순서를 넣되 실제 존재하는 경로만 연결한다.

`project-brief.md`에는 목표, 사용자 역할, 핵심 업무, 기술 스택, 이번 범위와 제외 범위를 적는다. 상태와 검증 결과를 개요에 중복 누적하지 말고 현재 개발 상태에서 관리한다.

### 4.3 현재 개발 상태

```markdown
# 현재 개발 상태

최종 갱신: {{날짜}}

## 이번 목표
{{작업 목적·범위·완료 기준}}

## 현재 구현
{{실제 구현된 동작과 관련 경로}}

## 검증 결과
| 날짜 | 작업 디렉터리·명령 또는 수동 절차 | 성공 조건 | 결과 |
|---|---|---|---|
| {{날짜}} | {{실제 명령·절차}} | {{관찰할 결과}} | {{통과/실패/미검증과 근거}} |

## 미검증·제약
{{미검증 이유, 영향, 확인에 필요한 조건}}

## 다음 작업
{{우선순위와 시작 위치}}

## 관련 문서
{{실제로 존재하는 설계·계획·변경 기록 경로}}
```

### 4.4 작업 계획·변경 기록

```markdown
# {{작업명}} 계획
- 요청과 문제: {{해결할 구체적 문제}}
- 설계: {{선택한 동작·기존 구조 재사용·영향 경계}}
- 변경 범위: {{파일·모듈과 제외 범위}}
- 구현 순서: {{의존성을 고려한 단계}}
- 완료 기준: {{사용자가 관찰할 수 있는 결과}}
- 검증: {{대상 테스트·API·UI 등 필요한 검사와 성공 조건}}

# {{날짜}} {{작업명}} 변경 기록
- 이유: {{왜 변경했는가}}
- 내용: {{최종 동작과 영향}}
- 검증: {{실행한 명령·절차와 결과}}
- 미검증: {{확인하지 않은 내용과 이유}}
- 다음 작업: {{남은 작업 또는 없음}}
```

### 4.5 설치·동작 확인표

```markdown
# 스킬·플러그인 환경 기록
기준일: {{날짜}} / 환경: {{OS·런타임}}

| 이름·ID | 범위·경로 | 설치 출처·버전 | 세션 노출 | 최소 실행 결과 | 용도 |
|---|---|---|---|---|---|
| {{항목}} | {{프로젝트/사용자/내장}} | {{출처·버전 또는 미확인}} | {{확인/미확인}} | {{명령·결과 또는 미검증}} | {{필요 작업}} |
```

설치 파일 존재, 활성 설정, 세션 제공, 실제 실행 성공은 서로 다른 상태다. 설치표에 구분해서 남긴다.

## 5. 기본 절차 스킬과 선택 기준

| 적용 시점 | 스킬 | 목적 |
|---|---|---|
| 세션 시작 | using-superpowers | 관련 스킬 판단 |
| 기능·동작 설계 | brainstorming | 의도·범위·선택·완료 기준 정리 |
| 다단계 작업 | writing-plans, executing-plans | 계획 작성과 순차 실행 |
| 기능·버그 검증 | test-driven-development | 의미 있는 실패 테스트와 구현 확인 |
| 원인 불명 실패 | systematic-debugging | 증거 기반 원인 추적 |
| 완료 보고 | verification-before-completion | 현재 결과로 완료 주장 검증 |
| 변경 범위 통제 | karpathy-guidelines | 과잉 구현 방지·가정 명시 |
| 독립 리뷰 | requesting-code-review, receiving-code-review | 검토 요청과 피드백 평가 |
| 하네스 유지보수 | harness:harness | 구성·역할·문서와 실제 설치 상태 대조 |

이름 목록을 문서에 적는 것만으로 스킬이 설치되거나 적용되지는 않는다. 해당 환경의 제공 목록과 SKILL.md를 확인한다. 사용할 수 없는 도구를 전제하는 지침은 현재 런타임에 맞춰 적용하고 미지원 사항을 기록한다.

새 프로젝트의 기본 구성은 12절의 내장 절차로 시작한다. 위 스킬과 디자인·시각화·배포·문서 산출물 스킬은 해당 작업을 도울 때 추가한다. 내장 절차는 외부 스킬의 원문 복제나 기능상 동일한 구현을 의미하지 않는다.

## 6. 사용자 전역 스킬 전체 참고 목록

원본 환경의 `~/.agents/skills/`에서 확인한 58개다. `~`는 사용자 홈을 뜻한다. 동일 PC라면 이미 공유될 수 있고, 다른 PC라면 설치가 필요할 수 있다. 이 전체 목록을 필수 설치 목록으로 해석하지 않는다.

| 분류 | 스킬 |
|---|---|
| 개발 절차·계획·협업 (13) | using-superpowers, brainstorming, writing-plans, executing-plans, test-driven-development, systematic-debugging, verification-before-completion, requesting-code-review, receiving-code-review, dispatching-parallel-agents, subagent-driven-development, using-git-worktrees, finishing-a-development-branch |
| 변경 범위·조사·품질 (8) | karpathy-guidelines, investigate-first, lean-build, migration, safe-refactor, surgical-patch, verify-and-stop, diagnosing-superpowers |
| 디자인·시각화 (9) | archify, banner-design, brand, design, design-system, impeccable, slides, ui-styling, ui-ux-pro-max |
| 웹·React·Vercel (8) | deploy-to-vercel, vercel-cli-with-tokens, vercel-composition-patterns, vercel-optimize, vercel-react-best-practices, vercel-react-native-skills, vercel-react-view-transitions, web-design-guidelines |
| 글·스킬·도구 (6) | humanizer, writing-guidelines, writing-skills, find-skills, gepeto, pinokio |
| Caveman 계열 (14) | cavecrew, caveman, caveman-commit, caveman-compress, caveman-discover, caveman-evidence-review, caveman-explore, caveman-help, caveman-learn, caveman-manage, caveman-optimize, caveman-review, caveman-setup, caveman-stats |

추가 경로의 원본 환경 상태:

| 경로 | 항목 | 당시 확인 수준 |
|---|---|---|
| `~/.codex/skills/` | humanizer, i-have-adhd | 파일 2개. i-have-adhd는 세션 목록에서 미노출 |
| `~/.codex/skills/.system/` | imagegen, openai-docs, plugin-creator, review-agent, skill-creator, skill-installer | 시스템 6개. review-agent는 세션 목록에서 미노출 |
| 프로젝트 `.agents/skills/` | impeccable | 로컬 설치. 8절 참조 |

humanizer·impeccable은 여러 경로에 중복될 수 있고, karpathy-guidelines는 아래 플러그인으로도 제공된다. 호출 경로와 버전을 확인하고 이름만 보고 중복 설치·삭제하지 않는다. 시스템·내장 구성은 개인 스킬처럼 무조건 복사하지 않는다.

## 7. 플러그인 전체 참고 목록과 설치 준비

### 7.1 원본 환경의 명시 활성 플러그인 15개

원본 사용자 `~/.codex/config.toml`의 활성화 값과 로컬 plugin.json으로 확인한 값이다. 공급원 이름은 원본 환경의 등록 이름이며, 다른 환경에서 같은 마켓플레이스가 등록되어 있다고 가정하지 않는다.

| 플러그인 ID | 확인 버전 | 선택할 작업 |
|---|---|---|
| harness@harness-marketplace | 1.2.0 | 하네스 설계·감사·유지보수 |
| designlang@designlang | 13.3.0 | 디자인 언어 추출·비교 |
| andrej-karpathy-skills@karpathy-skills | 1.0.0 | 코드 변경 원칙 |
| diagram-design@diagram-design | 2.6.33 | 구조·흐름·데이터 시각화 |
| codex-app-tools@openai-bundled | 0.1.4 | Codex 앱의 작업·파일·자동화 기능 |
| browser@openai-bundled | 26.911.61220 | 브라우저 관련 구성 |
| chrome@openai-bundled | 26.911.61220 | Chrome 관련 구성 |
| unified-computer-use@openai-bundled | 26.911.61220 | 제공되는 브라우저·컴퓨터 제어 도구 |
| computer-use@openai-bundled | 26.911.61220 | 컴퓨터 제어 스킬 |
| visualize@openai-bundled | 1.0.37 | 대화 내 시각화 |
| documents@openai-primary-runtime | 26.909.61513 | Word 문서 작업 |
| pdf@openai-primary-runtime | 26.909.61513 | PDF 작업 |
| spreadsheets@openai-primary-runtime | 26.909.61513 | 스프레드시트·Excel 작업 |
| presentations@openai-primary-runtime | 26.909.61513 | 프레젠테이션 작업 |
| template-creator@openai-primary-runtime | 26.909.61513 | 재사용 산출물 템플릿 |

### 7.2 추가 제공·캐시 항목

| 플러그인 ID | 확인 버전 | 원본 환경 상태 |
|---|---|---|
| sites@openai-curated-remote | 0.1.65 | 캐시·스킬 노출 확인. 위 명시 활성 15개와 별도 |
| plugin-management@openai-curated-remote | 0.1.0 | 캐시·스킬 노출 확인. 위 명시 활성 15개와 별도 |
| openai-templates@openai-curated-remote | 0.1.1 | 캐시만 확인. 활성·호출 가능 여부 미확인 |

원본의 미설치 추천 목록은 Dropbox, Box, Codex Security, Figma, GitHub, Gmail, Google Calendar, Google Drive, Linear, Notion, OpenAI Developers, Outlook Calendar, Outlook Email, SharePoint, Slack, Teams였다. 새 프로젝트가 해당 서비스를 사용할 때만 연결 필요성을 판단한다. 추천 표시·스킬 존재·플러그인 활성화는 계정 인증이나 데이터 접근 권한을 증명하지 않는다.

### 7.3 설치 출처와 재현 방법

1. 현재 런타임이 제공하는 설치 목록·관리 기능에서 항목을 먼저 확인한다. 이미 제공되는 내장 도구는 별도 설치 대상으로 만들지 않는다.
2. 플러그인은 위 ID의 실제 등록 공급원과 패키지 manifest를 확인한다. 로컬 스킬은 출처가 확인된 패키지 전체를 사용한다. SKILL.md만 복사하면 references·scripts·assets가 누락될 수 있다.
3. 새 PC로 옮길 때는 라이선스·출처를 확인한 스킬 패키지 또는 확인된 설치 원본을 준비한다. 사용자 config.toml 전체, 인증 파일, 플러그인 캐시 전체를 복사하는 방식은 사용하지 않는다.
4. 버전·설치 범위·출처를 4.5절 표에 기록하고 현재 세션에서 실제 노출을 확인한다. 필요하면 설치 후 새 세션에서 다시 확인한다.
5. 설치된 버전이 제공하는 최소 검사 방법으로 실행을 검증한다. 검증하지 못하면 이유와 함께 미검증으로 남긴다.

기존 이식 자료에 기록된 출처 식별자는 Archify의 `tt-a1i/archify`, designlang의 `Manavarya09/design-extract`다. 당시 Archify metadata는 2.17이었다. 이 정보는 현재 온라인 저장소나 설치 명령의 유효성을 새로 검증한 결과가 아니며, designlang CLI와 Codex 플러그인은 따로 확인한다.

나머지 전역 스킬의 원격 설치 URL과 commit은 원본 현황 조사에서 확정하지 않았다. 이름을 보고 임의의 저장소 주소나 일괄 설치 명령을 만들지 말고 현재 제공자의 정보를 확인한다. 이 문서는 버전 잠금 파일이나 무인 설치 스크립트가 아니다.

## 8. 선택 구성: Impeccable과 훅

UI 작업이 있는 프로젝트에 적용할 수 있는 로컬 구성의 예다. 원본에서는 다음 파일·설정을 확인했다.

| 항목 | 원본 상태 |
|---|---|
| 스킬 | `.agents/skills/impeccable/SKILL.md`, metadata 4.3.1 |
| 실행기 | `scripts/VERSION` 0.1.5. 스킬 버전과 별도 |
| Windows 실행 파일 | `scripts/impeccable.cmd`, `scripts/bin/windows-x64/impeccable.exe` |
| 보조 역할 | impeccable_asset_producer, impeccable_documenter, impeccable_finish_reviewer, impeccable_manual_edit_applier |
| 훅 설정 | `.codex/hooks.json` |
| 변경 후 훅 | PostToolUse, matcher `Edit\|Write\|apply_patch`, `impeccable hook`, timeout 5초 |
| 종료 훅 | Stop, `impeccable hook`, timeout 30초 |
| 로컬 상태 | `.impeccable/config.json`, `.impeccable/review/` |

새 프로젝트에는 사용 환경에 맞는 패키지와 필요한 보조 자료를 설치하고, 훅은 해당 런타임이 지원하는 형식과 실행 경로를 확인해 기존 설정에 병합한다. Windows 실행 파일을 다른 OS에 복사해 작동한다고 가정하지 않는다. 원본에서는 파일·설정의 존재를 확인했으며 훅의 실제 실행 성공과 보조 에이전트 호출은 검증하지 않았다.

원본 저장소는 `.agents/`, `.codex/`, `.impeccable/` 전체를 Git에서 제외했다. 새 프로젝트는 팀 공유할 규칙·스킬 정의·비밀값 없는 설정과 개인 캐시·세션 상태를 구분해 추적 정책을 정한다. 원본의 제외 규칙을 그대로 적용하면 팀에 필요한 설정도 빠질 수 있다.

## 9. 검증과 완료 기준

### 작업별 검증 표

새 프로젝트에서 실제 명령을 확인해 빈칸을 채운다. 아래 범주가 해당하지 않으면 제외 이유를 적는다. 모든 작업에서 전 항목을 실행하지 않는다.

| 변경 유형 | 직접 검증 | 추가 검증이 필요한 조건 |
|---|---|---|
| 문서 | 경로·링크·목록·서술과 실제 상태 대조 | 실행 명령을 제공하면 명령 유효성 확인 |
| 백엔드 동작 | 관련 단위·통합 테스트, 필요한 빌드 | DB·API·권한·동시성 경계가 바뀌면 해당 실제 경로 확인 |
| UI | 관련 상호작용, 타입·빌드, 키보드·모바일 | 공통 레이아웃·공용 컴포넌트 변경이면 영향 화면 확대 |
| 데이터 이관 | 격리 환경의 migration·정합성 검사 | 복구 요구가 있으면 rollback·백업 복원 검증 |
| AI 기능 | 입력·출력·오류·허용 범위와 실제 조회 근거 | 외부 모델·API를 연결하면 해당 환경의 실패·시간 제한 확인 |
| 스킬·플러그인 | 설치·버전·세션 노출·최소 실행 | 훅·보조 에이전트·외부 연결은 각각 별도 검증 |

### 이식 완료 체크리스트

- [ ] 프로젝트명·목표·업무·기술 스택·격리 경계가 대상 프로젝트 기준이다.
- [ ] AGENTS.md가 가리키는 문서가 존재하며 기존 규칙과 충돌하지 않는다.
- [ ] 필요한 스킬·플러그인의 설치·세션 노출·최소 실행 상태를 구분했다.
- [ ] 중복 설치와 OS·런타임 차이를 확인했다.
- [ ] 실제 검증 명령의 작업 디렉터리·성공 조건·결과를 기록했다.
- [ ] 사용자 변경·데이터·비밀값·개인 설정을 보존했다.
- [ ] 다음 작업자가 현재 상태와 미검증 항목을 알 수 있다.

문서만 정리한 상태는 `하네스 문서 준비 완료`로 보고한다. 실행 환경을 확인하지 않았다면 `설치·동작 검증 완료`로 보고하지 않는다.

## 10. 새 프로젝트 에이전트에게 전달할 요청문

다음 문장과 이 파일을 함께 전달하면 된다.

```text
첨부한 개발 하네스 통합 가이드를 이 프로젝트에 맞게 적용해줘.

먼저 기존 AGENTS.md, 개요, 현재 개발 상태, 기술 스택과 사용자 변경을 조사해.
기존 규칙과 문서 구조를 우선 활용하고, 없는 규칙·문서만 필요한 만큼 추가해.
가이드의 원본 프로젝트명·업무·포트·DB·버전·설치 상태를 이 프로젝트의 사실로 사용하지 마.

프로젝트 경계, 도메인 원칙, 설계·계획·검증·문서화 흐름을 기록해.
필수 파일과 작업 절차는 이 문서의 12절만으로 적용해. 외부 스킬이 없어도 기본 적용을 완료해.
스킬과 플러그인은 선택 확장으로 현재 설치·세션 제공 여부를 확인하고 필요한 항목과 없는 항목을 구분해.
누락 항목은 확인된 설치 출처와 적용 방법을 정리해. 이 요청만으로 계정 연결이나 외부 배포는 하지 마.
기존 설정·스킬·훅은 덮어쓰지 말고 차이를 설명해.

실제 저장소에서 사용할 검증 명령과 성공 조건을 확인하고 문서 링크도 검증해.
마지막에 생성·수정한 문서, 설치 확인 상태, 실행 검증 결과, 미검증 항목과 다음 작업을 알려줘.
필수 정보 중 저장소에서 확인할 수 없는 것만 질문하고 나머지는 계속 진행해.
```

## 11. 이 문서의 작성·검증 범위

- 변경 이유: 다른 프로젝트에 파일 하나로 전달 가능한 가이드가 필요했다.
- 구성: 원본 하네스 절차를 일반화하고 규칙·현황·계획·변경 기록·설치표 템플릿, 전체 참고 목록, 적용 요청문을 통합했다.
- 근거: 원본 환경에서 확인한 스킬 파일·플러그인 manifest·활성 설정·세션 제공 목록과 프로젝트 문서다.
- 검증 대상: 전역 스킬 58개와 명시 활성 플러그인 15개 목록 일치, 필수 템플릿 포함, 외부 파일 링크·개인 절대 경로 의존 부재, Markdown 코드 블록·공백 형식.
- 1.1 실행 검증: 본문의 PowerShell 스크립트를 임시 디렉터리에 직접 적용해 필수 파일 7개 생성, 재실행 시 7개 파일 해시 유지, 기존 AGENTS.md 보존과 나머지 6개 생성 통과를 확인했다. PowerShell 구문과 문서 형식도 확인했다.
- 미검증: 다른 프로젝트 적용, 새로운 설치, 원격 설치 출처의 현재 상태, 훅·플러그인 실제 실행.
- 다음 작업: 대상 프로젝트에서 1절의 적용 절차를 수행하고 4.5절에 그 환경의 확인 결과를 기록한다.

## 12. 외부 설치 없이 적용하는 내장 하네스

### 12.1 기본 작업 절차

다음 규칙은 이 문서 자체의 기본 절차이며 외부 SKILL.md를 읽지 않아도 수행할 수 있다.

| 단계 | 수행할 작업 | 다음 단계로 넘어갈 기준 |
|---|---|---|
| 조사 | 기존 규칙·현재 상태·관련 설계·변경 기록·구현과 테스트를 읽는다 | 문제·변경 위치·도메인 경계를 설명할 수 있음 |
| 범위 정의 | 요청, 성공 조건, 변경·제외 범위, 미정 사항을 기록한다 | 구현에 필요한 결정이 확인됨. 불필요한 재확인은 하지 않음 |
| 계획 | 변경 파일·의존 순서·검증 방법을 짧게 적는다 | 각 단계의 결과와 성공 조건이 있음 |
| 문제 재현 | 버그라면 실패를 재현하고 원인 가설을 증거로 좁힌다 | 수정해야 할 원인과 회귀 검증이 정해짐 |
| 구현 | 기존 구조를 재사용해 최소 범위로 바꾼다 | 요청한 동작을 구현하고 사용자 변경을 보존함 |
| 검증 | 동작 변경의 직접 테스트와 필요한 타입·빌드를 실행한다 | 실제 결과가 성공 조건을 충족하거나 실패·미검증 이유가 기록됨 |
| 검토 | diff에서 범위 밖 변경, 권한·오류·상태 경계 누락을 확인한다 | 중요한 문제를 수정하고 필요한 검증을 통과함 |
| 인계 | 현재 상태·변경 이유·결과·미검증·다음 작업을 갱신한다 | 다른 세션이 이어갈 시작 위치를 알 수 있음 |

UI는 기존 기능성 컴포넌트, 실제 데이터·키보드·모바일 동작을 확인한다. 데이터 변경은 정합성과 필요한 복구 경로를 확인한다. 외부 효과가 있는 작업은 사용자의 요청 범위와 현재 권한을 따른다. 전체 검증은 영향 범위가 요구할 때 확장한다.

### 12.2 필수 파일 생성 스크립트

아래 코드 블록을 `apply-harness.ps1`로 저장하고 PowerShell에서 실행할 수 있다. 에이전트는 문서에서 이 코드 블록을 읽어 실행하거나 같은 내용을 파일 편집 도구로 작성할 수 있다. 원격 다운로드·패키지 설치 없이 대상 디렉터리에 필수 문서 7개를 준비한다.

실행 예: `./apply-harness.ps1 -ProjectRoot .`

대상 프로젝트 디렉터리는 먼저 존재해야 한다. 기존 파일은 수정하지 않고 `EXISTS`로 표시한다. 이후 에이전트가 기존 내용과 비교해 필요한 부분을 병합한다. `CREATED`는 파일 생성 결과이며 도메인 조사·프로젝트 적용·제품 검증 완료를 뜻하지 않는다.

<!-- BEGIN SELF-CONTAINED BOOTSTRAP -->
```powershell
param([Parameter(Mandatory = $true)][string]$ProjectRoot)
$ErrorActionPreference = 'Stop'
$rootItem = Get-Item -LiteralPath $ProjectRoot
if (-not $rootItem.PSIsContainer) { throw 'ProjectRoot must be a directory.' }
$targetRoot = $rootItem.FullName
$projectName = $rootItem.Name
$files = [ordered]@{}
$files['AGENTS.md'] = @'
# 프로젝트 작업 규칙

## 작업 전 읽기
1. docs/project-rules/core-principles.md
2. docs/project-rules/doc-access-order.md
3. docs/overview/current-development-context.md
4. docs/overview/project-brief.md

관련 설계·변경 기록 → 하위 AGENTS.md → 구현·테스트 순서로 조사한다.

## 기본 절차
- 문제·성공 조건·변경 범위·제외 범위를 먼저 정한다.
- 다단계 작업은 변경 파일·순서·검증 방법을 docs/plans/에 기록한다.
- 버그는 재현하고 증거로 원인을 좁힌 뒤 수정한다.
- 동작 변경은 의미 있는 직접 테스트 또는 재현 절차로 검증한다.
- 기존 구조를 재사용하고 사용자 변경·요청 범위 밖 코드를 보존한다.
- 직접 테스트와 필요한 타입·빌드 검사를 우선한다.
- 전체 테스트·브라우저 회귀는 사용자 요청 또는 변경 영향이 요구할 때 실행한다.
- UI는 기존 기능성 컴포넌트를 사용하고 데이터·키보드·모바일 동작을 확인한다.
- diff에서 범위 밖 변경과 권한·상태·오류 경계 누락을 검토한다.
- 변경 이유·결과·미검증·다음 작업을 한국어로 docs/changes/와 현재 상태에 기록한다.
- 파일·명령의 존재만으로 기능 완료를 보고하지 않는다.

## 환경 경계
- 다른 프로젝트의 코드·DB·계정·비밀값·배포 환경을 혼용하지 않는다.
- 외부 변경은 사용자가 요청한 범위와 현재 권한을 따른다.
- 스킬·플러그인은 선택 확장이다. 없어도 위 기본 절차를 직접 수행한다.
- 프로젝트 고유 원칙은 core-principles.md에 조사 근거와 함께 작성한다.
'@
$files['docs/project-rules/core-principles.md'] = @'
# 핵심 원칙

- 기존 사용자 변경을 보존하고 요청 범위 안에서 수정한다.
- 데이터·상태·권한의 최종 결정 주체를 확인한 뒤 해당 경계에서 검증한다.
- 비밀값·쿠키·캐시·DB dump를 저장소에 넣지 않는다.
- 실제로 실행한 검증만 통과로 기록한다.

## 프로젝트별 확인 항목
- 도메인 불변 조건: 미정. 관련 모델·서비스·테스트에서 확인한다.
- 권한·상태·금액의 결정 주체: 미정. 실제 구현에서 확인한다.
- AI 허용 범위: 미정. AI 구성 여부와 역할을 확인한다.
- 참조 자산의 수정·재사용 범위: 미정. 출처와 프로젝트 지침을 확인한다.
- 제외 범위·다른 환경과의 경계: 미정. 프로젝트 개요와 실행 설정을 확인한다.
'@
$files['docs/project-rules/doc-access-order.md'] = @'
# 문서 접근 순서

1. 루트 AGENTS.md와 docs/project-rules/core-principles.md
2. docs/overview/current-development-context.md와 project-brief.md
3. 작업에 관련된 기존 설계·업무 흐름·의사결정
4. 관련 변경 기록
5. 해당 경로의 하위 AGENTS.md, 구현 파일과 테스트

없는 문서의 내용을 추정하지 않는다. 기존 대체 문서를 사용하고 실제 경로를 기록한다.
'@
$files['docs/overview/project-brief.md'] = @'
# 프로젝트 개요

- 프로젝트명: __PROJECT_NAME__
- 목표: 미정. README와 사용자 요청에서 확인한다.
- 사용자 역할·핵심 업무: 미정. 기존 문서·구현에서 확인한다.
- 기술 스택·버전: 미정. 의존성·실행 설정에서 확인한다.
- 이번 범위·제외 범위: 미정. 현재 요청을 기준으로 작성한다.
- 개발·테스트 환경: 미정. 비밀값을 제외한 실행 위치와 검증 방법을 기록한다.
'@
$files['docs/overview/current-development-context.md'] = @'
# 현재 개발 상태

## 하네스 적용 상태
- 기본 문서 생성 단계. 프로젝트 조사·기존 규칙 병합·실행 검증은 아직 완료하지 않았다.

## 현재 구현
- 미확인. 기존 코드와 변경 기록을 읽고 작성한다.

## 검증
| 작업 디렉터리·명령 또는 절차 | 성공 조건 | 실제 결과 |
|---|---|---|
| 아직 실행하지 않음 | 대상 작업별 정의 필요 | 미검증 |

## 다음 작업
1. 기존 규칙과 생성 문서를 대조·병합한다.
2. 개요·핵심 원칙의 미정 항목을 조사 결과로 채운다.
3. 실제 검증 명령을 확인하고 이번 작업에 필요한 검증을 수행한다.
4. 결과·미검증 항목·이어갈 위치를 기록한다.
'@
$files['docs/plans/task-template.md'] = @'
# 작업 계획 템플릿

- 요청·문제:
- 조사 근거·관련 경로:
- 설계·도메인 경계:
- 변경 범위·제외 범위:
- 구현 순서:
- 완료 기준:
- 직접 검증 명령·작업 디렉터리·성공 조건:
- 미정 사항:
'@
$files['docs/changes/change-template.md'] = @'
# 변경 기록 템플릿

- 날짜·작업명:
- 변경 이유:
- 최종 동작·관련 경로:
- 실제 실행한 검증·결과:
- 미검증 항목·이유:
- 다음 작업·시작 위치:
'@

# 쓰기 전에 모든 대상 경로의 링크·파일 충돌을 확인한다.
# 연결된 디렉터리를 통해 프로젝트 밖으로 쓰지 않도록 링크 경로는 거부한다.
foreach ($relativePath in $files.Keys) {
    $destination = Join-Path $targetRoot $relativePath
    $cursor = $destination
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $entry = Get-Item -LiteralPath $cursor -Force
            if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Linked path requires manual application: $cursor"
            }
        }
        $parent = Split-Path -Parent $cursor
        if ($parent -eq $cursor) { break }
        $cursor = $parent
    }
    if ((Test-Path -LiteralPath $destination) -and
        (Get-Item -LiteralPath $destination).PSIsContainer) {
        throw "Expected a file, found directory: $destination"
    }
    $parentPath = Split-Path -Parent $destination
    while ($parentPath -and $parentPath -ne $targetRoot) {
        if ((Test-Path -LiteralPath $parentPath) -and
            -not (Get-Item -LiteralPath $parentPath).PSIsContainer) {
            throw "Expected a directory: $parentPath"
        }
        $parentPath = Split-Path -Parent $parentPath
    }
}

foreach ($relativePath in $files.Keys) {
    $destination = Join-Path $targetRoot $relativePath
    if (Test-Path -LiteralPath $destination) {
        Write-Output "EXISTS: $relativePath (preserved; compare and merge)"
        continue
    }
    [IO.Directory]::CreateDirectory((Split-Path -Parent $destination)) | Out-Null
    $body = $files[$relativePath].Replace('__PROJECT_NAME__', $projectName)
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($body.TrimEnd() + "`n")
    $stream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    Write-Output "CREATED: $relativePath"
}
Write-Output 'Next: investigate project, merge existing rules, fill unknowns, and verify.'
```
<!-- END SELF-CONTAINED BOOTSTRAP -->

### 12.3 생성 후 에이전트가 마무리할 일

1. 기존 파일이라 건너뛴 항목에는 누락된 절차만 병합한다. 기존 문서 체계가 다르면 새 중복 문서보다 기존 문서에 연결한다.
2. 프로젝트명 외의 값은 코드·README·의존성·실행 설정에서 조사해 채운다. 과거 프로젝트의 값을 가져오지 않는다.
3. AGENTS.md의 참조 경로와 기본 절차를 확인한다. 외부 스킬 설치가 없어도 작업을 진행할 수 있어야 한다.
4. 문서 변경을 검증하고 `현재 개발 상태`의 “기본 문서 생성 단계”를 실제 적용 결과로 갱신한다. 제품 실행을 하지 않았으면 그 사실을 남긴다.
5. 선택 스킬·플러그인은 이번 작업에 필요할 때만 추가한다. 원본과 같은 플러그인 기능을 모두 오프라인 재현하는 것은 이 문서의 범위가 아니다.
