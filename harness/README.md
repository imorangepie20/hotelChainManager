# 재사용 가능한 프로젝트 하네스

이 디렉터리는 B2B-STM에서 실제로 사용한 작업 규칙, 스킬, 플러그인, 검증 방식을 다른 프로젝트로 옮기기 위한 문서 묶음입니다. 제품의 B2B 업무 규칙과 계정·DB·비밀값은 포함하지 않습니다.

## 문서 구성

| 문서 | 용도 |
|---|---|
| [하네스 구조와 운영 방식](portable-harness-guide.md) | 역할, 문서 흐름, 구현·검증 절차 |
| [플러그인·스킬 목록](plugin-skill-inventory.md) | 실제 사용 항목, 선택 항목, 설치와 프로젝트 파일의 경계 |
| [Archify 설치와 적용](archify-guide.md) | 아키텍처·업무 흐름 다이어그램 설치, 검증, 보관 규칙 |
| [새 프로젝트 적용 절차](bootstrap-checklist.md) | 복사부터 첫 검증까지 순서대로 실행하는 체크리스트 |
| [하네스 manifest](harness-manifest.yaml) | 도구가 읽기 쉬운 구성 요약과 교체 항목 |
| [AGENTS.md 템플릿](templates/AGENTS.template.md) | 새 저장소 루트에 복사할 작업 진입 규칙 |
| [현재 개발 상태 템플릿](templates/current-development-context.template.md) | 세션 간 인계 문서 |
| [변경 기록 템플릿](templates/change-record.template.md) | 의미 있는 변경의 이유·내용·검증 기록 |

## 가장 빠른 적용

1. `templates/AGENTS.template.md`를 새 프로젝트 루트의 `AGENTS.md`로 복사합니다.
2. `docs/project-rules`, `docs/overview`, `docs/changes`를 만들고 두 문서 템플릿을 복사합니다.
3. `bootstrap-checklist.md`의 프로젝트별 교체 항목을 작성합니다.
4. 필요한 스킬과 플러그인이 현재 Codex 세션에 노출되는지 확인합니다. 구조 시각화가 필요하면 `archify-guide.md`에 따라 Archify를 설치합니다.
5. 작은 변경 하나에 문서 접근 → 구현 → 관련 테스트 → 브라우저 검증 → 변경 기록 흐름을 적용합니다.

하네스는 사용자 환경의 플러그인 설치를 저장소 파일만으로 재현하지 않습니다. 저장소에는 규칙과 설치 명령, 버전, 검증 결과를 남기고 자격 증명은 남기지 않습니다.
