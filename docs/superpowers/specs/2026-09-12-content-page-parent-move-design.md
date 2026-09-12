# 콘텐츠 페이지 부모 이동·깊은 트리 구현 설계

최종 갱신: 2026-09-12  
상태: 구현 승인됨  
적용 대상: Spring API `4080`, 관리자 `4001`, 고객 웹 `4000`

## 1. 목적과 범위

본사 관리자가 `SECTION`과 `CONTENT_PAGE`로 구성된 콘텐츠 트리에서 초안 또는 미발행 콘텐츠 페이지의 부모와 slug를 안전하게 변경할 수 있게 한다. 경로는 최대 4개 segment로 제한한다. 발행된 페이지 또는 발행된 하위 페이지가 있는 트리는 이번 단계에서 이동할 수 없다.

이번 범위는 페이지 이동, 깊은 트리 생성·조회, 이동 전 영향 확인, 관리자 트리 UI와 관련 서버 검증이다. redirect, 발행 페이지 이동, 드래그 앤 드롭, 다국어 경로, SEO canonical 변경, 미디어 파일 교체·영구 삭제는 포함하지 않는다.

## 2. 선택한 구조

이동은 새 `POST /api/staff/website/pages/{pageId}/move` 명령으로 분리한다. 기존의 일반 저장 API는 본문·메타데이터 초안만 변경하고, 이동은 부모·slug·하위 초안 경로와 draft version을 한 트랜잭션에서 바꾼다.

`website_page`의 `parent_id`와 `draft_path`를 재사용한다. 이동하려는 페이지와 모든 하위 페이지를 결정적 경로 순서로 잠근 뒤, 새 부모 범위·최대 깊이·slug·경로 충돌·순환 참조·수명주기를 검증한다. 발행본 경로와 메뉴는 어떤 경우에도 바꾸지 않는다.

## 3. 트리와 경로 규칙

- `HOME_PAGE`와 `HOTEL_LANDING`은 이동 대상이나 부모가 될 수 없다.
- 이동 가능한 대상은 `SECTION` 또는 `CONTENT_PAGE`의 `ACTIVE` 초안이다. `ARCHIVED` 대상·부모는 거부한다.
- `CONTENT_PAGE`는 `SECTION` 또는 `CONTENT_PAGE` 아래에 둘 수 있다. `SECTION`은 체인 공통 또는 같은 지점 범위의 `SECTION` 아래에 둘 수 있다.
- 지점 소유 상세(`ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`, 지점 `GUIDE`)는 새 부모와 같은 지점 범위여야 한다. 체인 공통 `BRAND`, `PROMOTION`, 체인 `GUIDE`는 체인 공통 부모 아래에만 둔다.
- 새 초안 경로는 부모의 `draft_path + '/' + slug`로 계산하며 `/`를 제외하고 1~4개 segment만 허용한다.
- 대상 자신이나 하위 페이지 아래로 이동할 수 없다. 같은 부모·같은 slug 요청도 이동으로 처리하지 않고 `400`으로 거부한다.
- 새 경로가 다른 활성 페이지의 draft 또는 published canonical path와 충돌하면 `409`으로 거부한다.

## 4. 공개 안전성

이동 대상 또는 그 하위 항목 중 하나라도 `published_version > 0`이면 서버는 `409 WEBSITE_PAGE_MOVE_PUBLISHED_DESCENDANT`를 반환한다. 이는 URL 변경과 redirect 정책이 없는 상태에서 고객 공개 URL이 바뀌는 것을 방지한다.

이동 전 `GET /api/staff/website/pages/{pageId}/move-impact?parentId=&slug=`은 권한·트리·범위를 검증하고 대상과 하위 페이지의 현재·예정 draft 경로, depth, 발행 여부를 반환한다. 이 조회는 데이터와 audit을 변경하지 않는다. 관리자 UI는 발행 항목이 발견되면 이동 제어를 비활성화하고 서버 오류도 그대로 표시한다.

## 5. 낙관적 버전과 감사

명령은 대상의 `expectedDraftVersion`과 `expectedLifecycleVersion`을 요구한다. 대상과 하위 페이지의 현재 상태는 잠금 뒤 다시 읽는다. 성공 시 대상과 경로가 바뀐 각 하위 페이지의 `draft_version`을 1 증가시키고 `PAGE_MOVED` audit을 남긴다. 발행 version, published path, published menu, published media usage, version snapshot은 바꾸지 않는다.

기존 audit 제약에 `PAGE_MOVED`를 추가한다. audit payload에는 이전·이후 부모 ID, 이전·이후 draft path, 변경 root 여부를 기록한다. 하위 경로 재계산은 각 행의 audit으로 추적 가능해야 한다.

## 6. 관리자와 고객 화면

관리자 CMS 트리는 최대 4단계까지 들여쓰기해 표시한다. 선택한 이동 가능한 페이지에서만 “이동” dialog를 열며, 새 부모와 slug를 입력하면 impact 조회 결과를 보여 준다. 확인 버튼은 명시적 경고 문구를 다시 입력하지 않아도 되지만, 영향 목록과 경로를 보여 주고 키보드 취소·Escape·포커스 복귀를 지원한다.

성공 후에는 페이지 트리와 선택 문서를 새로 읽는다. 고객 웹은 기존 공개 resolve와 navigation만 계속 사용하므로 코드 변경 없이 발행본 경로를 보존한다. 초안·미발행 페이지는 고객 공개 API에서 계속 제외된다.

## 7. 오류와 권한

- 본사 외 세션은 impact와 move 모두 `403`이다.
- 없는 페이지·부모는 `404`이며 잘못된 expected version은 `409`이다.
- 경로 깊이 초과, 자기/하위 이동, 범위 불일치, 상태 불가, 같은 위치 요청은 `400`이다.
- 경로 충돌 또는 발행 대상/하위 페이지가 있으면 `409`이다.
- 실패한 요청은 parent, path, version, audit, media usage, 공개 응답을 변경하지 않는다.

## 8. 검증 기준

1. 서버 통합 테스트는 미발행 단일·하위 페이지 이동, 전체 하위 draft path 재계산, 최대 깊이, 순환·지점 범위·경로 충돌·권한·낙관적 충돌을 검증한다.
2. 같은 테스트는 발행 대상 또는 발행 하위 페이지 이동 거부와 모든 실패의 불변성을 검증한다.
3. 관리자 Chromium E2E는 impact 표시, 성공 payload, 거부 오류 표시, Escape와 포커스 복귀, 성공 후 트리 갱신을 검증한다.
4. 변경 범위의 TypeScript 검사와 고객 production build를 실행한다. 실제 사용자 페이지에는 이동 요청을 보내지 않으며, runtime 확인은 읽기 전용 공개 resolve와 navigation으로 제한한다.

## 9. 후속 범위

redirect와 발행 페이지 이동은 `website_redirect`의 locale별 source/target·순환 방지·301 전달·발행 snapshot 정책을 별도 설계한 뒤 추가한다. 보관 업로드 자산의 영구 삭제와 파일 교체는 media lifecycle을 별도로 다룬다.
