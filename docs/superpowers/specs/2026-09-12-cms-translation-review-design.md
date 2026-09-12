# CMS 영문 번역 검토·승인 설계

## 목표

현재 영어 번역은 본사 관리자가 저장 직후 바로 발행할 수 있다. 이번 단계에서는 영어 초안을 특정 version에 묶어 검토 요청·승인·반려하고, 승인된 version만 발행하도록 제한한다. 기존에 공개한 영어 7개 페이지의 발행 snapshot은 유지한다.

## 범위

- 대상은 `website_page_translation.locale = 'en'`인 영어 번역이다.
- 첫 단계에서는 기존 `HQ_ADMIN`이 작성·검토 요청·승인·반려·발행을 모두 수행한다.
- 상태는 `DRAFT → IN_REVIEW → APPROVED → PUBLISHED`다.
- 반려는 `IN_REVIEW → DRAFT`이며 사유가 필수다.
- 저장된 초안이 바뀌면 이전 검토·승인은 무효가 되고 `DRAFT`로 돌아간다. 현재 공개 snapshot은 바꾸지 않는다.
- 예약 발행, 알림, `HQ_EDITOR`·`HQ_PUBLISHER` 역할 분리, 한국어 승인, 코멘트 대화형 스레드는 후속 범위다.

## 데이터 모델

V21은 기존 테이블을 삭제·이전하지 않는 확장 migration이다.

`website_page_translation`에 다음 현재 상태를 추가한다.

- `review_status`: `DRAFT`, `IN_REVIEW`, `APPROVED`, `PUBLISHED`
- `reviewed_draft_version`: 검토 요청·승인·발행 상태가 묶인 영어 초안 version. `DRAFT`에서는 `NULL`이다.

DB 제약은 `DRAFT`일 때 version 연결이 `NULL`이고, 나머지 상태에서는 양수이며 현재 `draft_version` 이하가 되도록 강제한다.

기존 영어 번역은 다음처럼 이관한다.

- 공개본이 있고 `published_from_draft_version = draft_version`이면 `PUBLISHED`와 해당 version으로 이관한다. 기존 공개를 소급해 승인한 이력은 만들지 않는다.
- 공개본이 없거나 발행 후 초안이 다시 저장된 번역은 `DRAFT`, `reviewed_draft_version = NULL`이다.

`website_translation_review_event`는 append-only 감사 기록이다.

- identity: 증가형 `id`, `page_id`, 고정 locale `en`
- 행위: `REVIEW_REQUESTED`, `APPROVED`, `REJECTED`, `APPROVAL_INVALIDATED`, `PUBLISHED`
- 대상: `draft_version`
- 행위자와 시각: `actor_id`, `created_at`
- 설명: 선택 코멘트. `REJECTED`는 공백 제거 후 1~2,000자가 필수다.

페이지 삭제 시 번역과 함께 event를 cascade 삭제한다. 직원 삭제 시 `actor_id`만 `NULL`로 남겨 시각·행위·version을 보존한다.

## 상태 전이와 정합성

모든 쓰기는 staff session의 `HQ_ADMIN` 권한, 페이지 `ACTIVE` 상태, 요청의 낙관적 version을 확인하고 page row와 영어 translation row를 기존 순서대로 잠근 단일 트랜잭션에서 처리한다.

1. 영어 초안 초기화는 `DRAFT`로 시작한다.
2. 영어 저장은 `draft_version`을 증가시킨다. 기존 상태가 `IN_REVIEW`, `APPROVED` 또는 `PUBLISHED`면 `DRAFT`와 `reviewed_draft_version = NULL`로 바꾸고 `APPROVAL_INVALIDATED`를 남긴다. 공개 데이터와 공개 media usage는 유지한다.
3. 검토 요청은 저장된 `DRAFT`만 받는다. 현재 publish validator와 media/path/connection 검증을 먼저 수행하고 `IN_REVIEW`, `reviewed_draft_version = draft_version`으로 바꾼다.
4. 승인은 현재 `IN_REVIEW`이고 `reviewed_draft_version = draft_version`일 때만 가능하다. 승인 코멘트는 선택이다.
5. 반려는 현재 `IN_REVIEW`일 때만 가능하며 반려 사유가 필수다. 상태를 `DRAFT`로 바꾸고 version 연결을 비운다.
6. 발행은 현재 `APPROVED`이며 승인 version과 현재 `draft_version`이 같을 때만 가능하다. 기존 publish 검증을 다시 수행한 뒤 snapshot·version·media usage·redirect를 갱신하고 `PUBLISHED` event를 남긴다.
7. 같은 요청의 동시 실행은 첫 성공 이후 상태 불일치로 두 번째 요청을 `409 Conflict` 처리한다.

`PUBLISHED`는 “현재 초안과 공개본이 동일한 version”이라는 편집 상태다. 이후 저장하면 새 초안은 `DRAFT`가 되지만 이전 공개본은 계속 고객에게 제공된다.

## API

기존 영어 조회·저장 API와 공개 API 응답은 유지한다. 다음 본사 API를 추가한다.

- `GET /api/staff/website/pages/{pageId}/translations/en/review`: 현재 상태, 연결된 초안 version, 최근순 event 최대 50개
- `POST /api/staff/website/pages/{pageId}/translations/en/review/request`: `expectedDraftVersion`, 선택 `comment`
- `POST /api/staff/website/pages/{pageId}/translations/en/review/approve`: `expectedDraftVersion`, 선택 `comment`
- `POST /api/staff/website/pages/{pageId}/translations/en/review/reject`: `expectedDraftVersion`, 필수 `comment`

각 mutation 응답은 갱신된 review 상태와 생성된 event를 반환한다. 기존 `POST .../publish` 요청 형식은 유지하되 승인 상태 검증을 추가한다.

대표 오류 코드는 다음과 같다.

- `WEBSITE_TRANSLATION_REVIEW_STATE_CONFLICT`: 현재 상태에서 요청한 전이를 할 수 없음
- `WEBSITE_TRANSLATION_REVIEW_STALE`: 요청 version과 현재 초안 또는 검토 대상 version이 다름
- `WEBSITE_TRANSLATION_REJECTION_REASON_REQUIRED`: 반려 사유가 비었거나 제한을 벗어남
- `WEBSITE_TRANSLATION_NOT_APPROVED`: 현재 초안이 승인되지 않아 발행할 수 없음

오류 메시지는 현재 상태와 사용자가 취할 복구 행동인 새로고침, 저장 또는 재검토 요청을 함께 안내한다. 공개 API에는 검토 상태·코멘트·담당자를 노출하지 않는다.

## 관리자 UI

영어 편집기 상단에 공통 검토 action bar를 둔다.

- 상태 배지: `초안`, `검토 중`, `승인됨`, `발행됨`
- 보조 정보: 검토 대상 초안 version, 마지막 행위자·시각
- `DRAFT`: 저장된 변경이 없을 때 `검토 요청`
- `IN_REVIEW`: `승인`, `반려`
- `APPROVED`: `발행`
- `PUBLISHED`: 현재 초안과 공개본이 일치함을 표시하며 편집은 계속 허용한다. 저장하면 새 초안이 `DRAFT`가 된다.

미저장 변경이 있으면 검토 요청·승인·발행을 비활성화하고 먼저 저장하라고 안내한다. 반려는 기존 dialog 컴포넌트에서 사유 textarea와 오류를 제공하며 키보드 focus를 복원한다. `HOTEL_LANDING`, `HOME_PAGE`, `CONTENT_PAGE`의 기존 직접 영어 발행 버튼은 공통 action bar의 상태 기반 발행으로 대체한다.

우측 이력 영역에는 검토 event를 최근순으로 표시하고 기존 영어 발행 이력은 유지한다. API 성공 후 후속 이력 조회가 실패해도 이미 성공한 mutation을 실패로 표시하지 않고 상태를 먼저 반영한 뒤 이력만 다시 불러올 수 있게 한다.

## 테스트와 완료 기준

Spring PostgreSQL 통합 테스트에서 다음을 검증한다.

- 기존 공개 영어 snapshot과 URL이 V21 이후 그대로 유지됨
- 초안 초기화와 저장은 `DRAFT`
- 유효한 초안만 검토 요청 가능
- 승인·반려의 정상 전이와 반려 사유 검증
- 승인 version과 다른 초안 발행 차단
- 검토·승인 뒤 저장 시 `DRAFT` 복귀와 기존 공개본·공개 usage 보존
- 승인된 현재 version만 발행되고 event의 version·actor·시각·comment가 보존됨
- stale version, 중복 승인·반려·발행, 보관 페이지, 비본사 세션 차단
- 페이지 영구 삭제 시 review event cascade 삭제

관리자 Playwright는 지점 랜딩과 일반 콘텐츠 페이지에서 `저장 → 검토 요청 → 승인 → 발행`, 필수 반려 사유, 미저장 상태 버튼 비활성화, 오류 복구와 기존 발행 이력을 검증한다. 관리자 TypeScript 검사와 production build를 수행한다. 고객 공개 회귀는 승인 전 기존 공개본 유지와 승인 발행 뒤 새 snapshot 노출을 확인한다.

## 배포와 롤백

V21은 additive migration이라 기존 데이터와 공개 조회를 파괴하지 않는다. 새 애플리케이션을 먼저 V21 호환 상태로 배포하고 관리자 mutation을 연다. V21 적용 뒤 이전 애플리케이션으로 되돌리면 승인 검증을 우회할 수 있으므로, 롤백 시 영어 발행 endpoint를 차단하거나 새 버전으로 다시 올릴 때까지 관리자 발행을 중지한다. 테이블·컬럼을 자동 삭제하지 않는다.
