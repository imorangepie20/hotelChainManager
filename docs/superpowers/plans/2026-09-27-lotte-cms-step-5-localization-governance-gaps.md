# CMS 5단계 — 다국어·거버넌스 후속 구현 게이트

상태: 1~4단계 기준선/격차 확정 전에는 실행하지 않는 범위 개요. 파일·테스트 단위 구현 계획이 아니다.
상위 설계: [롯데리조트급 CMS 기능 설계](../../architecture/lotte-resort-level-cms-functional-design.md)
목표: locale별 초안·검토·승인·예약/즉시 발행과 SEO 산출물을 독립 운영하면서 한국어 기존 경로와 공개본을 보존한다.

## 독립 경계

- `website_page` identity/관계는 공유하고 번역별 slug/path/menu/SEO/block 문서와 draft/published version, review/schedule 상태를 분리한다.
- 대상 locale은 우선 `ko`, `en`이며 추가 locale은 allowlist migration과 번역/SEO/E2E가 함께 준비될 때만 연다.
- 현재 기준선은 V20~V23, 영어 번역/검토 구현과 기존 locale 계획이다. 남은 한국어 포함 전체 역할 분리, 예약 발행, locale redirect, sitemap·OG·robots·JSON-LD를 통합한다.
- 현재 저장 모델은 완전 locale-neutral하지 않으며 한국어는 legacy `website_page`, 영어는 translation row에 있다. 한국어를 translation row로 옮기는 작업은 이 계획의 별도 expand/dual-write/read-switch 결정으로 다루고 한 migration에서 contract하지 않는다.

## 입력·산출 계약

- 입력: 1~4단계의 identity/snapshot, block schema, relation, redirect/media usage와 HQ 역할 계약.
- 산출: locale별 draft/review/approved/scheduled/published 상태, scheduler, 공개 SEO/sitemap 계약과 운영 runbook.

## 불변식과 기존 콘텐츠 보호

- 한국어는 기존 row/URL/publication의 호환 locale이다. migration이 한국어 발행 JSON·path·version을 재발행하거나 덮어쓰지 않는다.
- 영어 초안/발행은 한국어와 독립적이다. 누락 영어를 한국어로 무단 fallback하지 않고 404 또는 명시된 안내를 사용한다.
- 요청자는 자기 초안을 승인할 수 없으며 승인된 exact version만 발행/예약할 수 있다. 승인 후 수정은 DRAFT로 되돌린다. 예약은 가변 draft row를 직접 가리키지 않고 승인 시점의 불변 content·metadata·connections·media snapshot과 digest를 참조한다.
- 예약 발행 직전에 경로·연결·미디어·승인 version을 다시 검증하고 실패하면 현재 공개본을 유지한다.
- sitemap/OG/robots/JSON-LD는 PUBLISHED·색인 허용 snapshot만 사용하며 CMS 표시 가격을 실시간 예약 가격처럼 구조화하지 않는다.

## Migration 계획

- [ ] translation/review/role schema와 한국어 호환 view를 inventory하고 schedule, SEO, audit 필드의 drift를 정리한다.
- [ ] 승인 시 불변 publication snapshot을 만들고 schedule table은 `(page_id, locale, publication_snapshot_id)` 유일성, digest, UTC instant, `PENDING|CLAIMED|PUBLISHED|FAILED|PAUSED|CANCELLED`, claim token을 갖고 additive하게 추가한다.
- [ ] locale별 slug/path/canonical/SEO 제약은 기존 ko 데이터를 먼저 audit한 뒤 `NOT VALID`→validate 순으로 건다.
- [ ] ko backfill은 값 복사와 row 수/hash 검증만 수행하고 새 publish audit 또는 publishedVersion 증가를 만들지 않는다.
- [ ] 한국어 shadow row가 필요하면 expand→dual-write→page별 path/version/content/media/relation reconciliation→dual-read canary→read switch 순서를 지키고 legacy 열 제거는 장기 안정화 뒤 별도 계획으로 미룬다.
- [ ] rollback은 새 review/scheduler/SEO generation flag를 끄고 기존 ko facade와 이전 published translation을 제공한다. 번역/감사/schedule 행을 삭제하지 않는다.

## API 계약

| API군 | 계약 |
| --- | --- |
| translation draft | page+locale, expected draft version, locale metadata/block; 다른 locale 무변경 |
| review request/approve/reject | actor, target version, 코멘트 정책; self-approval 403/409 |
| immediate/scheduled publish | approved version, expected published version, UTC instant와 표시 timezone |
| public navigation/resolve | locale별 PUBLISHED snapshot만; missing translation은 정한 404/안내 |
| sitemap/metadata | locale canonical/alternate, OG, robots, JSON-LD의 공개 전용 read model |

모든 변경 응답과 audit는 page ID, locale, target version, actor, timestamp를 포함한다. scheduler는 idempotent claim과 재시도 가능/불가능 실패를 구분한다.

## 권한·정합성

- [ ] `HQ_EDITOR`는 draft/request, `HQ_PUBLISHER`는 approve/reject/publish/schedule, `HQ_ADMIN`은 정책·복구를 담당하고 `BRANCH_STAFF` write를 금지한다.
- [ ] 현재 한국어 명령의 HQ_ADMIN-only 경계와 영어 EDITOR/PUBLISHER 경계를 inventory하고, 한국어 역할 확대 여부를 명시적 보안 결정으로 기록한다. UI 버튼만으로 권한을 넓히지 않는다.
- [ ] self-approval, stale approval, 승인 뒤 수정, 동일 snapshot 중복 발행, 과거 예약, 중복 schedule을 서버에서 거부한다. 수정·반려·보관·경로 이동은 예약을 자동 발행하지 않고 명시적 취소 또는 재승인을 요구한다.
- [ ] scheduler는 DB 시간과 UTC를 기준으로 claim하고 다중 인스턴스에서 exactly-once 공개 결과와 at-least-once 안전한 실행을 보장한다.
- [ ] scheduler는 `FOR UPDATE SKIP LOCKED` 또는 동등한 lease/claim fencing과 idempotency key를 쓰고, claim 전후 snapshot digest·경로·연결·미디어를 재검증하며 실패한 schedule은 원인·재시도 가능성을 보존한다.
- [ ] locale별 path/redirect/canonical/alternate가 충돌·chain·cycle을 만들지 않고 공유 relation이 발행 시 유효한지 재검증한다.
- [ ] audit는 append-only이며 콘텐츠 원문·token·secret을 저장하지 않는다.

## 관리자·고객 UI

- [ ] 관리자는 locale 전환, 미저장 변경 확인, 번역 상태 badge, diff, 검토함, 승인/반려, 서울 시간대 예약 발행과 실패 복구를 제공한다.
- [ ] editor와 publisher action을 키보드로 완결할 수 있고 self-approval/권한 부족 이유를 명확히 보인다. 대화상자는 390px에서 넘치지 않는다.
- [ ] 고객 locale route와 navigation은 같은 locale만 유지하고 누락 번역을 숨긴다. `<html lang>`, canonical, hreflang, OG, robots를 snapshot과 일치시킨다.
- [ ] sitemap은 공개·색인 허용 locale URL만 포함하고 archive/미발행/preview URL을 제외한다.

## 테스트와 E2E

- [ ] migration 테스트로 ko row/hash/version 보존, en 독립성, schedule 제약과 rollback-compatible schema를 검증한다.
- [ ] 서버 통합 테스트로 전체 role matrix, self-approval, stale version, schedule race/retry/restart, publish 실패 시 기존 공개본 보존을 확인한다.
- [ ] 관리자 Playwright로 ko/en 독립 저장, request→approve→즉시/예약 발행, 반려, 키보드, 390px, 권한 오류를 검증한다.
- [ ] 고객 Playwright로 `/`와 `/en/*`, 누락 번역, canonical/hreflang/OG/robots, sitemap 포함/제외와 예약 시각 전후 전환을 검증한다.
- [ ] 실제 `Asia/Seoul` 경계와 UTC 저장, DST가 있는 표시 timezone fixture, 프로세스 재시작 뒤 예약 보존을 검증한다.

## 배포·롤백·완료 기준

1. translation/SEO/schedule schema → locale-compatible read → review UI → scheduler → sitemap/metadata 순으로 flag를 연다.
2. scheduler lag/failure, locale 404, canonical 충돌, sitemap URL 수, self-approval 거부 audit를 관찰한다.
3. 문제 시 새 scheduler claim과 locale write/SEO flag를 끄고 in-flight lease를 drain한 뒤 마지막 정상 published translation과 기존 ko facade로 복귀한다. 공개본·번역·snapshot·audit를 삭제하지 않는다.
   pending schedule은 `PAUSED`, 운영자가 취소한 항목은 `CANCELLED`로 보존하고 한국어 공개본으로 자동 fallback하지 않는다.
4. 1단계 감사가 실제 격차와 정확한 Create/Modify/Test 경로·명령을 확정한 별도 구현 계획을 승인하기 전에는 이 절을 실행하지 않는다. 이후 migration/API/권한·정합성, 재시작·rollback drain 포함 scheduler, 관리자/고객 E2E, SEO 산출물, 기존 ko URL·hash·version 보존 증거가 모두 있어야 완료다.
