# CMS 2단계 — 상세 페이지 기반 후속 구현 게이트

상태: 1단계 기준선 감사 전에는 실행하지 않는 범위 개요. 파일·테스트 단위 구현 계획이 아니다.
상위 설계: [롯데리조트급 CMS 기능 설계](../../architecture/lotte-resort-level-cms-functional-design.md)
목표: 콘텐츠 기반 계약 위에 상세 블록의 저장·검증·관리자 편집·고객 렌더링을 추가하되 가격·재고·예약 확정은 계속 예약 도메인에 둔다.

## 독립 경계

- 대상 블록은 `RICH_TEXT`, `IMAGE_GALLERY`, `FEATURE_GRID`, `SPEC_TABLE`, `OPERATING_HOURS`, `LOCATION`, `ACCORDION`, `NOTICE_LIST`, `PROMOTION_SUMMARY`, `RELATED_COLLECTION`, `BOOKING_CTA`다. 현재 `ContentPageValidator`, 고객 parser/renderer와 `WebsitePageIntegrationTest`에 이미 구현된 항목은 1단계 감사에서 완료로 분류하고 다시 만들지 않는다.
- 이 단계는 블록 schema와 renderer를 소유한다. `room_type`·지점 관계의 영속화와 목록 필터는 3단계, 이동·미디어 운영은 4단계, locale·승인은 5단계에 둔다.
- 현재 공개 `BOOKING_CTA`와 fresh availability 동작은 비활성화하거나 재작성하지 않고 기준선으로 보존한다. 실제 미구현 schema/preview 격차가 확인될 때만 이 단계가 맡고, 도메인 대상 검증과 신규 CTA E2E는 3단계가 맡는다.
- 기존 `HERO/TEXT/CTA`와 이미 발행된 rich block snapshot은 계속 읽는다. 현재 기준선은 기존 상세 블록 구현과 고객 parser/관리자 preview이며 구현 착수 시 실제 완료 범위를 테스트로 재확인한다.

## 입력·산출 계약

- 입력: 1단계의 `ContentKind`, stable block ID, immutable published snapshot과 공개 resolve 계약.
- 산출: versioned block union, 상세/카드 공개 DTO, 관리자 form·preview 계약. 3단계는 CTA와 연결 가능한 ID 자리만 소비한다.

## 불변식과 기존 콘텐츠 보호

- 새 필드는 optional/additive로 시작하고 block마다 stable `blockId`와 `schemaVersion`을 사용한다. 과거 block JSON은 일괄 재작성하지 않는다.
- 서버 validator와 고객 parser는 필드 allowlist, 길이·배열 상한, UUID 자산, 안전한 내부 URL 계약을 동일 fixture로 검증한다.
- 자유 HTML·Markdown·script·iframe·data URL·외부 이미지 URL을 저장하거나 렌더링하지 않는다.
- CMS의 가격·혜택은 표시 문구일 뿐이다. `BOOKING_CTA`는 지점/선택 room type intent만 전달하고 availability 요청은 Spring API가 다시 계산한다.
- 검증용 발행은 격리 fixture에서만 수행하며 기존 사용자 page/version/media usage를 수정하지 않는다.

## Migration 계획

- [ ] JSON 문서 자체를 ALTER할 필요가 없는지 먼저 확인한다. schema version/index가 필요할 때만 최신 Flyway 다음 번호로 additive migration을 만든다.
- [ ] media usage에 `block_id`, field path 또는 snapshot schema version이 부족하면 nullable 열과 backfill 상태표를 추가한다.
- [ ] 기존 usage는 원본 JSON을 수정하지 않고 `(pageId, version, ordinal)` 기반 식별자를 read-time 또는 별도 참조 열에 보강한다.
- [ ] backfill 전후 page/version/media usage 수와 published JSON hash가 같음을 검증한다.
- [ ] rollback은 새 block 생성 UI/API를 feature flag로 막고 기존 renderer를 유지한다. 이미 발행된 새 block을 모르는 구버전으로 내릴 수 없으므로 down-deploy 전 호환 renderer 제공을 배포 gate로 둔다.

## API 계약

- 관리자 save/publish DTO는 `blocks[]`를 discriminated union으로 받고 알 수 없는 type/field, 중복 block ID, 상한 초과를 400으로 거부한다.
- 공개 resolve는 발행 snapshot에서 필요한 필드와 정규화된 media delivery path만 반환한다. 원본 storage key, draft field, 내부 검토 정보는 제외한다.
- `BOOKING_CTA` 응답에는 `hotelId`, 선택 `roomTypeId`, 표시 문구만 허용하고 가격·재고·할인 계산값을 포함하지 않는다.
- block schema 변경은 새 `schemaVersion`과 backward parser를 먼저 배포한다. 기존 필드 제거/이름 변경은 두 단계 contract migration으로만 한다.
- Java validator와 TypeScript parser가 같은 versioned golden corpus를 accept/reject하도록 contract test를 둔다. 새 enum/block은 고객 parser·renderer를 먼저 배포한 뒤 저장과 발행을 별도 flag로 연다.
- 오류 응답은 block index와 안전한 field path를 제공하되 원문 콘텐츠나 개인정보를 로그에 남기지 않는다.

## 권한·정합성

- [ ] 1~4단계에서는 현재 `HQ_ADMIN` 전용 경계를 유지한다. 동일 역할 안에서도 초안 저장과 발행을 별도 서버 명령·검증 경로로 분리하고, 발행 직전에 block, media 상태, 내부 URL, 필수 block을 다시 검증한다. `HQ_EDITOR`/`HQ_PUBLISHER` 확대는 5단계 role matrix와 보안 테스트가 별도로 승인되기 전에는 이 단계에 포함하지 않는다.
- [ ] kind별 required/allowed block matrix, block ID 유일성, 갤러리 2~12개, 운영 시간 `Asia/Seoul`·예외, CTA 대상 범위를 테스트로 고정한다.
- [ ] page 저장, media usage 교체, version snapshot, audit을 한 transaction으로 처리한다.
- [ ] 실패 또는 동시 수정 때 기존 publishedVersion과 공개 media usage가 그대로인지 확인한다.

## 관리자·고객 UI

- [ ] 관리자는 JSON 입력 없이 유형별 form, 추가 가능 block allowlist, 순서 이동, 필수 block 삭제 방지, field별 오류 복구를 제공한다.
- [ ] 데스크톱/390px 미리보기는 현재 로컬 초안만 읽고 save/publish 요청을 보내지 않는다. CTA는 inert 상태다.
- [ ] 고객은 semantic heading/table/list/details를 사용하고 갤러리의 버튼·썸네일·키보드·스크린 리더·모바일 swipe와 reduced motion을 지원한다.
- [ ] 운영 시간은 서울 시간대와 휴무/계절 예외를, 프로모션은 표시 정보와 실제 예약 조건이 다를 수 있음을 명시한다.

## 테스트와 E2E

- [ ] 서버 parameterized test로 모든 block의 최소 유효 예제와 unknown field, unsafe URL, invalid UUID, 상한 경계를 검증한다.
- [ ] 고객 TypeScript contract test가 같은 golden fixture를 accept/reject하는지 확인하고 production build를 통과시킨다.
- [ ] 관리자 Playwright로 block 추가·재정렬·삭제 제한·media 선택·오류 복구·저장 payload·키보드·390px preview를 검증한다.
- [ ] 고객 Playwright로 각 상세 block, 갤러리 상호작용, FAQ와 표 접근성을 검증한다. 기존 CTA 동작은 비회귀만 확인하고 신규 CTA→fresh availability 연결 검증은 3단계 완료 기준으로 둔다.
- [ ] 서버와 고객에서 표시 숫자/시간/CTA가 동일하고 API request에 CMS 가격이 들어가지 않는지 network assertion으로 확인한다.

## 배포·롤백·완료 기준

1. backward-compatible 공개 parser/renderer → 서버 validator/read model → 관리자 block 생성 UI 순으로 배포한다.
2. kind/block별 생성 flag를 분리해 한 유형씩 canary하고 parser rejection, render error, media 404를 관찰한다.
3. 문제 시 관리자 생성 flag를 끄고 신규 발행을 중단한다. 이미 발행된 block은 호환 renderer로 계속 제공하며 snapshot/usage를 삭제하지 않는다.
   구버전 client가 새 payload 전체를 거부할 수 있으면 서버 public adapter가 호환 shape를 제공할 때까지 구버전 binary로 rollback하지 않는다.
4. 1단계 감사가 실제 격차와 정확한 Create/Modify/Test 경로·명령을 확정한 별도 구현 계획을 승인하기 전에는 이 절의 항목을 실행하지 않는다. 이후 migration·API contract·권한/원자성·관리자/고객 E2E·390px·접근성·예약 도메인 경계 증거가 모두 있어야 완료다.
