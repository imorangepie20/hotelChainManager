# 발행 콘텐츠 페이지 이동·301 redirect 변경 기록

최종 갱신: 2026-09-12

## 변경 내용

- V18은 `website_redirect`에 기존 공개 경로와 새 공개 경로, 대상 페이지, 생성자·생성 시각을 저장하고 `PAGE_MOVED_WITH_REDIRECT` audit action을 추가했다.
- 본사 관리자가 발행된 `CONTENT_PAGE` 트리를 이동하면 root와 하위의 draft·published 경로 및 버전을 같은 transaction에서 갱신하고, 각 이전 공개 경로에 새 경로를 향하는 redirect를 만든다.
- 발행 snapshot의 path를 새 경로로 동기화하며, root snapshot의 parentId와 slug도 현재 구조에 맞춘다.
- 공개 resolve는 현재 활성 공개본을 우선 반환하고, 없을 때만 redirect source를 찾아 HTTP 301과 local `Location`을 반환한다. redirect target을 다시 해석하지 않는다.
- 기존 redirect의 target을 새 source로 사용하거나 기존 source를 새 target으로 만드는 이동은 거부해 redirect chain·cycle을 만들지 않는다.
- 관리자 impact 조회는 발행된 하위 항목의 현재·예정 공개 경로도 제공한다. 대화상자는 301 redirect 목록을 보여 준 뒤에만 이동을 실행하며, 발행본이 있으면 `expectedPublishedVersion`을 함께 전송한다.

## 검증

| 범위 | 결과 |
| --- | --- |
| 서버 통합 | `WebsitePageIntegrationTest` 26건 통과 |
| 대상 시나리오 | 발행 root·child 이동, redirect 2건, 공개 snapshot path, public 301, 관리 API 분기 통과 |
| 안전성 | 두 번째 발행 이동의 redirect chain 거부 및 기존 공개 경로·redirect 유지 확인 |
| 관리자 | 발행/미발행 이동 E2E 2건 통과, TypeScript 검사 통과 |
| API runtime | Docker 재빌드 뒤 Flyway V18 적용, `/actuator/health` `UP`, 기존 `/brand/story` 공개 resolve 확인 |

실제 사용자 CMS 페이지·자산에는 move, save, publish, archive, delete 요청을 보내지 않았다.

## 미검증·다음 작업

- 실제 사용자 CMS 페이지를 이동하지 않았으므로 runtime의 old-path 301/new-path resolve 쌍은 아직 수행하지 않았다. 서버 통합 테스트에서만 확인했다.
- 다국어 redirect, canonical/OG, redirect 만료·수동 관리와 외부 URL은 범위 밖이다.
