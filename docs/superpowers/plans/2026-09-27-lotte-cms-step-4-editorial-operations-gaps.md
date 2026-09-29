# CMS 4단계 — 편집 운영 후속 구현 게이트

상태: 1~3단계 기준선/격차 확정 전에는 실행하지 않는 범위 개요. 파일·테스트 단위 구현 계획이 아니다.
상위 설계: [롯데리조트급 CMS 기능 설계](../../architecture/lotte-resort-level-cms-functional-design.md)
목표: 발행 페이지 이동과 redirect, version 비교/복원, 미디어 variant·교체·삭제 유예, 데스크톱/390px preview를 일관된 운영 흐름으로 묶는다.

## 독립 경계

- 이 단계는 콘텐츠 의미를 바꾸지 않고 운영자가 기존 초안/발행본과 자산을 안전하게 이동·비교·대체·복원하는 명령을 소유한다.
- 현재 기준선은 V15, V17~V19, V24~V27 및 기존 move/redirect/version/media/preview 계획이다. 구현 시작 시 완료된 기능을 inventory하고 빈 연결·권한·E2E만 보완한다.
- 현재 발행 페이지 이동이 과거 `website_page_version`의 path/parent/slug를 수정하는지 read-only 진단한다. 과거 snapshot은 immutable이어야 하므로 신규 이동은 현재 canonical과 redirect만 바꾸고 과거 version을 rewrite하지 않는다. 이미 수정된 행은 근거 없이 되돌리지 않고 별도 ADR과 복구 계획을 요구한다.
- locale별 검토·예약 발행·SEO 생성은 5단계가 소유한다. 이 단계의 redirect와 preview는 locale 확장 가능한 key/DTO를 제공한다.

## 입력·산출 계약

- 입력: 1~3단계의 path/version/snapshot, block/media usage, DRAFT/PUBLISHED relation 계약과 object-store mode.
- 산출: immutable move/redirect/version, media lifecycle/variant와 read-only preview 계약. 5단계는 locale key로 이를 확장한다.

## 불변식과 기존 콘텐츠 보호

- 이동 전 영향 목록은 root·descendant의 old/new path, redirect, 충돌을 모두 계산하며 확인된 expected version 없이는 명령을 실행하지 않는다.
- 발행 경로 변경은 old canonical URL을 직접 새 canonical로 301 연결한다. chain·cycle·외부 URL·다른 locale target을 허용하지 않는다.
- 미디어 대체는 새 asset/version을 만들고 선택된 DRAFT usage만 옮긴다. 원본과 PUBLISHED snapshot을 덮어쓰지 않는다.
- 발행 usage, version snapshot, quarantine/유예 중 자산은 영구 삭제하지 않는다. 사용자 자산을 테스트 목적으로 이동·보관·삭제하지 않는다.
- preview는 read-only grant이며 TTL·폐기·410을 지킨다. 미리보기 UI는 저장·발행/lifecycle 요청을 보내지 않는다.

## Migration 계획

- [ ] redirect, media variant/job/claim token, preview grant, delete/quarantine schema와 index를 현재 DB에서 audit한다.
- [ ] 누락 필드는 최신 Flyway 다음 번호의 expand migration으로 추가하고 FK/unique/check는 기존 데이터 검증 후 validate한다.
- [ ] media object migration은 DB metadata와 object store copy를 분리하고 digest·size·count audit 뒤 pointer를 전환한다. raw key나 secret을 로그에 남기지 않는다.
- [ ] cleanup/영구 삭제는 migration에서 실행하지 않는다. retention 만료와 reference 0을 재확인하는 별도 승인 명령으로 둔다.
- [ ] 롤백 시 move/variant/preview write와 새 worker claim만 끈다. 이미 이동된 canonical path의 old URL을 살리는 redirect read는 계속 유지하고 새 object/행은 즉시 삭제하지 않는다.

## API 계약

- move impact `GET`은 old/new path와 affected descendants를 반환하고 move `POST`는 draft/published/lifecycle expected version과 확인 token을 요구한다.
- 공개 resolve는 page가 없을 때 active redirect를 한 번만 적용해 `301 Location`을 반환한다. redirect 관리 정보는 공개 body에 넣지 않는다.
- version list/compare/restore는 immutable snapshot을 읽고 restore는 새 DRAFT version만 만든다.
- media usage/impact/replacement/archive/restore/delete/variant retry는 명시적 asset version과 idempotency/claim 조건을 사용한다.
- preview grant는 서버 `expiresAt`을 반환하고 만료 전 200, 만료·폐기 후 410이며 `Cache-Control: no-store`를 유지한다.

## 권한·정합성

- [ ] editor는 draft move/compare/preview, publisher는 published move·redirect 승인·archive, admin은 영구 삭제/운영 복구만 수행하도록 서버에서 나눈다.
- [ ] move는 descendants path, snapshot, redirect, audit을 한 transaction으로 처리하고 충돌·stale·chain이면 전부 rollback한다.
- [ ] move transaction은 새 immutable version과 audit을 추가하되 기존 version snapshot을 UPDATE하지 않는 계약을 통합 테스트로 고정한다.
- [ ] replacement는 source/target asset 상태와 usage version을 잠근 뒤 draft usage만 원자적으로 옮긴다.
- [ ] variant worker는 claim token과 상태 전이로 중복 처리를 막고 retry 상한·격리 실패를 기록한다.
- [ ] 영구 삭제 직전 모든 draft/published/version usage와 retention을 재조회한다.

## 관리자·고객 UI

- [ ] 이동 대화상자는 영향 목록과 redirect 결과를 보여주고 키보드 확인 전 명령을 보내지 않는다.
- [ ] compare는 block ID 기준 추가/삭제/이동/수정을 표시하고 restore가 새 초안임을 명시한다.
- [ ] 미디어 라이브러리는 variant 상태, usage, replacement scope, 보관/삭제 가능 이유와 복구 방법을 제공한다.
- [ ] preview는 데스크톱/390px, 현재 초안/발행본 비교, 만료 절대 시각을 제공하며 focus/Escape를 지킨다.
- [ ] 고객은 old path 301, 새 path canonical, responsive variant fallback을 안전하게 처리하고 broken media 시 layout shift를 제한한다.

## 테스트와 E2E

- [ ] 서버 통합 테스트로 root+child 이동, collision/chain/cycle/stale 원자성, version restore 불변성, media usage/retention, variant claim, preview 200→410을 검증한다.
- [ ] object store contract test로 digest·content type·range/variant fallback과 장애 rollback을 검증한다.
- [ ] 관리자 Playwright로 영향 확인→이동, compare→restore draft, media 대체/삭제 차단, preview 키보드·390px·TTL을 검증한다.
- [ ] 고객 Playwright로 old URL 301, canonical/새 page, responsive image, preview 만료 전/후와 draft 비노출을 확인한다.
- [ ] E2E는 격리 fixture만 만들고 종료 시 fixture ID만 정리한다. 실제 사용자 page/media에는 lifecycle 명령을 보내지 않는다.

## 배포·롤백·완료 기준

1. read-compatible schema/worker → impact/compare API → 관리자 명령 → 공개 redirect/variant 순으로 flag를 연다.
2. redirect hop, media 404, variant queue age, preview 410 비율과 orphan usage를 관찰한다.
3. 문제 시 move write, worker claim, preview write를 각각 끈다. 이미 이동된 canonical path는 flag로 원복되지 않으므로 redirect read를 끄지 않는다. canonical을 되돌려야 하면 충돌을 재검증한 보상 이동으로 새 version·audit·직접 redirect를 남긴다. 자동 data cleanup은 하지 않는다.
   variant worker rollback은 새 claim을 먼저 중지하고 in-flight lease를 drain한 뒤 이전 worker/API를 배포한다.
4. 1단계 감사가 실제 격차와 정확한 Create/Modify/Test 경로·명령을 확정한 별도 구현 계획을 승인하기 전에는 이 절을 실행하지 않는다. 이후 migration/API/권한·원자성, 관리자/고객 E2E, TTL 실제 경과, redirect를 유지하는 rollback rehearsal, 사용자 콘텐츠 무변경 증거가 있어야 완료다.
