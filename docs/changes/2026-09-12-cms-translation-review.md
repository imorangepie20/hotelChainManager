# CMS 영어 번역 검토·승인

## 변경 이유와 범위

영어 번역 초안을 저장한 직후 바로 발행할 수 있던 흐름에 version 기반 검토·승인 gate를 추가했다. 대상은 `website_page_translation.locale = 'en'`이며 기존 한국어 API와 공개 API 응답, 이미 공개된 영어 7개 페이지의 snapshot과 URL은 유지한다. 첫 단계에서는 기존 `HQ_ADMIN`이 작성·검토 요청·승인·반려·발행을 모두 수행한다.

## V21·V22 schema와 기존 행 backfill

- `website_page_translation`에 `review_status`와 `reviewed_draft_version`을 추가했다. 상태는 `DRAFT`, `IN_REVIEW`, `APPROVED`, `PUBLISHED`만 허용한다. `DRAFT`의 검토 version은 `NULL`, 나머지는 현재 초안 version 이하의 양수여야 한다.
- V22는 PostgreSQL `CHECK`에서 `NULL` 결과가 통과하는 특성을 보완해, `DRAFT`가 아닌 상태에는 `reviewed_draft_version IS NOT NULL`을 명시적으로 강제한다.
- 공개 콘텐츠가 있고 `published_from_draft_version = draft_version`인 기존 번역은 `PUBLISHED`와 해당 version으로 backfill한다. 공개본이 없거나 발행 뒤 초안이 달라진 row는 기본값 `DRAFT`, 검토 version `NULL`을 유지한다. 기존 발행을 소급한 검토 event는 생성하지 않는다.
- `website_translation_review_event`에 `REVIEW_REQUESTED`, `APPROVED`, `REJECTED`, `APPROVAL_INVALIDATED`, `PUBLISHED`를 append-only로 기록한다. 초안 version, 행위자, 시각, 선택 comment를 보존하고 조회는 최신순 최대 50건이다. 페이지 영구 삭제 시 cascade 삭제하며 직원 삭제 시 행위자 ID만 `NULL`이 된다.

## API와 상태 계약

다음 본사 전용 endpoint를 추가했다.

- `GET /api/staff/website/pages/{pageId}/translations/en/review`
- `POST /api/staff/website/pages/{pageId}/translations/en/review/request`
- `POST /api/staff/website/pages/{pageId}/translations/en/review/approve`
- `POST /api/staff/website/pages/{pageId}/translations/en/review/reject`

mutation은 현재 `expectedDraftVersion`과 comment를 받고, 갱신된 상태와 event를 반환한다. 모든 쓰기는 `HQ_ADMIN`, 활성 페이지, page row 다음 translation row 잠금 순서, 낙관적 version을 같은 transaction에서 확인한다.

1. 초안 초기화는 `DRAFT`다.
2. 검토 요청은 저장된 `DRAFT`의 콘텐츠·미디어·경로·연결을 발행 수준으로 검증한 뒤 `IN_REVIEW`로 전환한다.
3. 승인은 현재 version에 묶인 `IN_REVIEW`만 `APPROVED`로 전환한다.
4. 반려는 `IN_REVIEW → DRAFT`이며 trim 뒤 1~2,000자의 사유가 필수다. 요청·승인 comment는 선택이고 최대 2,000자다.
5. 영어 저장은 version을 증가시키고 기존 `IN_REVIEW`, `APPROVED`, `PUBLISHED`를 `DRAFT`로 무효화해 `APPROVAL_INVALIDATED`를 남긴다. 현재 공개 snapshot과 공개 media usage는 바꾸지 않는다.
6. 기존 영어 publish endpoint는 현재 초안과 같은 version의 `APPROVED`만 발행하고 `PUBLISHED` event를 남긴다. 검토 전 발행은 `WEBSITE_TRANSLATION_NOT_APPROVED`, 오래된 version은 `WEBSITE_TRANSLATION_REVIEW_STALE`, 잘못된 전이는 `WEBSITE_TRANSLATION_REVIEW_STATE_CONFLICT`로 `409` 처리한다.
7. 일반 페이지 보관은 영어 공개 snapshot과 공개 usage를 비우며 `PUBLISHED`를 같은 검토 version의 `APPROVED`로 바꾼다. 복원 뒤 그 초안을 다시 발행할 수 있고, 복원 전후 초안을 저장하면 승인은 무효화된다.

검토 상태·comment·담당자는 공개 API에 노출하지 않는다.

## 관리자 공통 UI와 복구

- `HOME_PAGE`, `HOTEL_LANDING`, `CONTENT_PAGE` 영어 편집기에 `초안`, `검토 중`, `승인됨`, `발행됨` 상태 배지와 공통 action bar를 적용했다. 기존 영어 direct publish 버튼은 상태 기반 발행으로 대체했고 한국어 발행 UI는 유지했다.
- 미저장, 처리 중, 보관 상태에서는 전이 action을 비활성화한다. 반려 dialog는 필수 textarea와 API 오류를 표시하며 취소·Escape·성공 뒤 trigger로 focus를 복원한다.
- 검토 event는 행위, `초안 vN`, 행위자 또는 `알 수 없는 담당자`, 한국어 현지 시각, comment를 최근순으로 표시한다. 기존 영어 발행 이력도 함께 유지한다.
- mutation 응답을 먼저 화면 상태에 반영한다. 이후 검토·발행 이력 읽기가 실패해도 이미 성공한 mutation을 실패로 되돌리거나 재전송하지 않고, 경고와 `이력 다시 불러오기`로 읽기만 재시도한다.

## 최종 회귀 검증

2026-09-13 KST에 계획의 명령을 새로 실행했다.

- `services/api`의 `.\mvnw.cmd -Dtest=WebsiteTranslationIntegrationTest test`: 16건 통과, 실패 0, 오류 0, 건너뜀 0. 이 suite의 격리 schema에서 V20까지 구성한 뒤 V21·V22를 실제 적용해 current-publication만 `PUBLISHED`로 backfill하고 다른 기존 row는 `DRAFT`로 남기는 것을 검증했다. 초기 상태, 최근 50건·행위자 mapping, 요청·승인·반려, 필수 사유, direct publish gate, stale/중복/권한/보관, 저장 무효화와 공개본·usage 보존, 영문 홈·지점·일반 페이지, 공개 fallback 금지, 영구 삭제 cascade를 포함한다.
- `SDTPL_ADM`의 `npm.cmd run test:e2e -- e2e/website-content-editor.spec.ts --workers=1 --max-failures=1`: Chromium 46건 통과. 1280px·390px 일반 페이지와 홈, 지점 랜딩의 저장→검토 요청→승인→발행, 반려 사유와 focus 복귀, dirty·보관·pending guard, 검토 이력, mutation 성공 뒤 이력 읽기 실패 복구, 기존 CMS 편집·보관·복원·삭제·미디어·이동 회귀를 포함한다.
- `SDTPL_ADM`의 `npm.cmd run build`: 종료 코드 0. Next.js 16.2.7 compile·TypeScript와 정적 페이지 103개 생성을 완료했다.
- `apps/web`의 `pnpm.cmd run build`: 종료 코드 0. TypeScript와 Vite production build가 2,898개 모듈을 변환했다.

Maven 실행에는 Mockito 동적 agent의 향후 JDK 비호환 경고가 있었고, Playwright에는 `FORCE_COLOR` 때문에 `NO_COLOR`가 무시된다는 경고가 있었으나 실패는 없었다.

## 실제 migration과 읽기 전용 공개 확인

- `docker compose up -d --build api`는 API image build와 로컬 API container 재생성을 완료했다. 마지막 200줄의 API log에서 개발 PostgreSQL 16.15 schema가 V20에서 `21 - website translation review`로 이동했고 `Successfully applied 1 migration ... now at version v21`을 확인했다. 이어 Tomcat 4080 기동과 `Started HotelApplication in 8.646 seconds`를 확인했다.
- `GET http://localhost:4080/actuator/health`는 HTTP 200, `status=UP`, `liveness`·`readiness` group을 반환했다.
- mutation이나 인증 없이 공개 resolve GET만 사용했다. `/en`은 HTTP 200 `HOME_PAGE`, `/en/stays/sokcho`, `/en/stays/seoraksan`, `/en/stays/jeju`는 각각 HTTP 200 `HOTEL_LANDING`, `/en/brand/story`, `/en/brand/haneul-story`, `/en/brand/forest-gallery-demo`는 각각 HTTP 200 `CONTENT_PAGE`였고 일곱 응답 모두 콘텐츠가 비어 있지 않았다.
- 컨테이너의 기존 DB 환경으로 SELECT 한 번만 실행했다. 공개 콘텐츠가 있고 `published_from_draft_version = draft_version`인 번역은 7행, 그중 `review_status = 'PUBLISHED'`도 7행, 불일치는 0행이었다.
- save·검토 요청·승인·반려·발행·보관·삭제 endpoint를 호출하지 않았고 DB나 사용자 CMS 데이터를 수정하지 않았다. `.env`와 credential은 읽거나 출력하지 않았다.

## 호환과 롤백 제약

V21은 기존 테이블·공개 콘텐츠를 삭제하지 않는 additive migration이다. 그러나 V21 적용 뒤 이전 binary로 되돌리면 이전 영어 publish 구현이 승인 gate를 검사하지 않아 승인을 우회할 수 있으므로 안전하지 않다. 롤백해야 한다면 영어 발행 endpoint와 관리자 CMS mutation을 차단한 상태에서 V21 호환 새 binary로 복귀해야 한다. V21 table·column을 자동 삭제하는 down migration이나 사용자 데이터 되돌리기는 수행하지 않는다. 실제 롤백 배포 훈련은 검증하지 않았다.

후속 최종 검토에서 비-DRAFT 상태의 `NULL` 검토 version과 review 처리 중 sibling 편집 가능성을 보강했다. `WebsiteTranslationIntegrationTest#migratesOnlyCurrentV20PublicationsToPublishedReviewState` 1건, 관리자 pending-review E2E 1건, `npx.cmd tsc --noEmit`을 focused 재검증했으며 모두 통과했다. 전체 suite와 build는 이 보강 뒤 반복 실행하지 않았다.

## 미검증과 제외 범위

- 실제 물리 장치와 실기기 브라우저 검증은 수행하지 않았다. Playwright의 1280px·390px viewport만 자동 검증했다.
- 작성자와 승인자의 역할 분리, `HQ_EDITOR`·`HQ_PUBLISHER` 같은 새 권한은 구현·검증하지 않았다. 현재는 `HQ_ADMIN` 단일 역할이다.
- 예약 발행과 scheduled publishing, notification·alert는 구현·검증하지 않았다.
- 한국어 승인 workflow와 전체 예약·결제·AI UI 영어화는 범위 밖이다.
- 전체 backend suite, 전체 관리자 E2E, 실제 rollback 훈련과 운영 배포는 실행하지 않았다. 이번 확인은 지정된 번역 통합 suite, 관리자 편집기 E2E 전체 파일, 두 production build와 로컬 API·DB read-only 검증에 한정한다.
