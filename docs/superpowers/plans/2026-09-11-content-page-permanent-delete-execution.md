# 일반 콘텐츠 페이지 영구 삭제 실행 기록

- 기준 작업 트리: 사용자가 제공한 현재 공유 작업 트리
- Task 1: 완료 — 보관된 page-scoped row 정리와 자산 유지, active/stale/SECTION/branch 거부를 통합 테스트로 고정하고 controller DELETE 204를 추가했다.
- Task 2: 완료 — 보관 후 영구 삭제 dialog, 204 client, 홈 전환과 갱신 tree를 연결했고 대상 E2E와 TypeScript 검사를 통과했다.
- Task 3: 완료 — 문서 갱신, Docker API 재빌드, health·기존 공개 resolve·유효하지 않은 세션 DELETE 401, `git diff --check`를 확인했다.
- Ruling: 영구 삭제는 `ACTIVE` 페이지나 자산 파일을 삭제하지 않고, 이미 고객 공개에서 분리된 `ARCHIVED CONTENT_PAGE`와 그 page-scoped 데이터만 제거한다. 보관·복원 단계를 거치게 해 운영 중인 고객 페이지의 우발적 삭제를 막는다.
