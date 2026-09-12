# 발행 콘텐츠 페이지 이동·Redirect 구현 설계

최종 갱신: 2026-09-12

## 범위

발행된 `CONTENT_PAGE`를 이동할 때 기존 공개 경로를 301으로 보존한다. `HOME_PAGE`와 `HOTEL_LANDING`은 제외하며, 다국어·canonical/OG·만료 redirect·외부 URL은 이번 단계에 넣지 않는다.

## 모델과 규칙

`website_redirect(source_path, target_path, page_id, created_at, created_by)`를 추가한다. source와 target은 로컬 canonical path이고 각각 유일하다. source는 현재 active page의 draft/published path와 충돌할 수 없으며 target은 active published page의 path여야 한다.

한 이동에서 root와 모든 발행 하위 페이지의 이전 published path를 새 published path로 매핑한다. redirect target은 다른 redirect source가 될 수 없고, target을 따라가는 chain·자기 참조·순환은 거부한다. 따라서 공개 요청은 최대 한 번만 redirect를 적용한다.

## 이동과 공개 응답

기존 move command는 `expectedDraftVersion`, `expectedLifecycleVersion`, `expectedPublishedVersion`을 추가로 요구한다. 발행 하위 페이지가 있으면 새 parent/slug, 모든 draft·published path, version snapshot, redirect rows, `PAGE_MOVED` audit을 하나의 transaction에서 갱신한다. 실패하면 공개 경로·redirect·audit 어느 것도 바뀌지 않는다.

`GET /api/website/pages/resolve`는 먼저 active page를 찾고, 없으면 redirect source를 한 번 조회해 target page를 resolve한다. redirect 응답은 `301 Location`만 반환하며 초안·관리자 ID를 노출하지 않는다. navigation은 target의 현재 published path만 반환한다.

## 권한과 검증

HQ만 impact와 move를 실행한다. archived page, 범위 불일치, 깊이 초과, stale version은 기존 규칙으로 거부한다. published page 이동은 redirect source 충돌·target chain·하위 발행 path 충돌을 `409`으로 거부한다.

## 검증

서버 통합 테스트는 발행 root/하위 이동과 301 전달, navigation의 새 path, chain·cycle·충돌·권한·stale version·실패 불변성을 검증한다. 관리자 E2E는 redirect 영향 표시와 명시 확인 payload를 검증한다. 실제 사용자 페이지에는 move 요청을 보내지 않는다.
