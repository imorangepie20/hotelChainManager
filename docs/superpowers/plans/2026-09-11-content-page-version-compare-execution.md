# 일반 콘텐츠 페이지 발행 이력 비교 실행 기록

- 기준 커밋: `264741c810cb9dc4d38b7739e0fdb4cda3846891`
- Task 1: 완료 — `WebsitePageVersionComparison`과 snapshot DTO, 본사 전용 compare GET, page scope·snapshot 무결성 검증, `WebsitePageIntegrationTest` 13건 통과.
- Task 2: 완료 — 발행 이력 비교 dialog, version 선택, 읽기 전용 SEO/HERO/TEXT/CTA 비교, `동일`·`변경`·`추가`·`제거` 텍스트 상태, 대상 Chromium Playwright 1건과 TypeScript 검사 통과.
- Task 3: 완료 — Docker API 재빌드 성공, 4080 health `UP`, 기존 `/brand/story` 공개 resolve 읽기 전용 확인, 문서 갱신과 공백 오류 확인 완료.
- Ruling: 단일 version 조회 두 번 대신 `baseVersion`·`compareVersion`을 함께 받는 비교 endpoint를 사용한다 — 서버가 두 snapshot의 동일 page scope와 오름차순을 한 계약으로 확인해 UI가 잘못 섞을 여지를 줄인다 — 잘못된 경우 API 범위가 다소 좁아질 수 있다.
- Ruling: 보관된 `CONTENT_PAGE`의 비교를 허용하고 현재 활성 미디어 검증은 하지 않는다 — 이력 검토는 당시 snapshot을 읽는 기능이며 보관이 과거 내용 검토를 막으면 안 된다 — 손상된 역사 snapshot은 400으로 거부된다.
- Ruling: 사용자가 제공한 공유 작업 트리에서는 branch·worktree·commit을 만들지 않는다 — 기존 변경을 분리하거나 덮어쓰지 않기 위해서다 — Git 기반 review package 대신 Task별 변경 파일과 focused test evidence로 검토한다.
