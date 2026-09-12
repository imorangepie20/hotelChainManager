# 콘텐츠 페이지 부모 이동·깊은 트리 변경 기록

최종 갱신: 2026-09-12

## 변경 내용

- V17은 `PAGE_MOVED` audit action과 부모·초안 경로 조회 index를 추가했다.
- 본사 전용 impact 조회와 move API는 미발행 `CONTENT_PAGE`의 새 상위·slug·하위 경로를 사전 계산한다.
- 이동은 하위 초안 경로와 draft version·audit만 같은 transaction에서 갱신한다. published path·메뉴·snapshot·media usage는 바꾸지 않는다.
- 이동 대상 또는 하위에 발행본이 있으면 redirect 정책 전까지 거부한다. 최대 깊이는 4 segment다.
- 관리자 CMS는 새 상위와 slug를 입력한 뒤 예정 경로를 확인해야만 이동 요청을 보낸다.

## 검증

| 범위 | 결과 |
| --- | --- |
| 서버 통합 | `WebsitePageIntegrationTest` 23건 통과 |
| 관리자 | 이동 영향 확인·POST payload E2E 1건 통과, TypeScript 검사 통과 |
| API 재빌드 | Docker 이미지 빌드 및 V17 migration 적용 |
| 읽기 전용 runtime | `GET /actuator/health`는 `UP`, `/brand/story` 공개 resolve 유지 |

실제 사용자 CMS 페이지·자산에는 move, save, publish, archive, delete 요청을 보내지 않았다.

## 남은 범위

- published page 이동을 위한 redirect·301·순환 방지 정책
- 보관 업로드 자산의 영구 삭제와 파일 교체
- 다국어 경로·검토·예약 발행
