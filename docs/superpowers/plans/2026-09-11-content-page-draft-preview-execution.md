# 일반 콘텐츠 페이지 초안 미리보기 실행 기록

- 기준 작업 트리: 사용자가 제공한 현재 공유 작업 트리
- Task 1: 완료 — 저장 전 HERO·TEXT 입력, dialog 표시, 닫기·Escape 포커스 복귀와 변경 API 부재를 E2E로 고정했다.
- Task 2: 완료 — `ContentPagePreviewDialog`, 안전한 고객 미디어 전달 경로 확인, placeholder, CTA 비이동 표현과 상단 미리보기 제어를 추가했다.
- Task 3: 완료 — 대상 Playwright와 TypeScript 검사를 통과했고 문서 갱신, `git diff --check`, 실제 로그인된 CMS의 읽기 전용 dialog 열기·닫기 확인을 완료했다.
- Ruling: 고객 웹 컴포넌트를 직접 재사용하지 않고 관리자 전용 읽기 dialog를 둔다. 관리자 번들의 의존 경계를 유지하면서도 같은 구조화 문서와 미디어 안전 규칙을 적용할 수 있기 때문이다.
