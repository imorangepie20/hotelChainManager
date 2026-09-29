# CMS 3단계 — 도메인 연결 후속 구현 게이트

상태: 1·2단계 기준선/격차 확정 전에는 실행하지 않는 범위 개요. 파일·테스트 단위 구현 계획이 아니다.
상위 설계: [롯데리조트급 CMS 기능 설계](../../architecture/lotte-resort-level-cms-functional-design.md)
목표: 콘텐츠 페이지를 실제 지점·객실 유형·관련 콘텐츠에 안전하게 연결하고, 공개 목록/CTA가 그 연결을 사용하되 예약 가격·재고의 권한을 침범하지 않게 한다.

## 독립 경계

- `ROOM`↔`room_type` 1개, `PROMOTION`↔하나 이상 지점/선택 객실 유형, `DINING/FACILITY/EXPERIENCE`↔소유 지점, 관련 페이지/컬렉션 필터를 소유한다.
- page identity와 block schema는 1·2단계 계약을 소비한다. 페이지 이동/redirect와 locale별 발행은 각각 4·5단계가 소유한다.
- 연결은 DRAFT와 PUBLISHED 상태를 분리한다. 초안 변경은 publish 전 고객 목록·상세·CTA를 바꾸지 않는다.

## 입력·산출 계약

- 입력: 1단계 page/version과 2단계 block/CTA DTO, 예약 API의 hotel/room type/availability read 계약.
- 산출: 상태별 normalized relation, reference catalog, collection card와 booking intent. 4·5단계는 관계 snapshot만 소비한다.

## 불변식과 기존 콘텐츠 보호

- CMS 관계는 소개/탐색용 참조다. 객실 정원·판매 상태·재고·가격·요금제·예약 확정은 Spring 예약 도메인의 현재 값만 신뢰한다.
- `ROOM`의 page.hotel과 room_type.hotel은 같아야 한다. `PROMOTION` room type은 선택한 target hotel 집합에 속해야 한다.
- related page는 자기 참조·중복·순환을 금지하고 공개 collection은 PUBLISHED·활성 대상만 반환한다.
- FK는 운영 master 삭제를 CMS가 유발하지 않도록 `RESTRICT`를 사용한다. 비활성 hotel/room type은 신규 저장·발행을 막되 기존 snapshot은 보존한다.
- 현재 객실 유형 삭제 경로가 CMS의 DRAFT/PUBLISHED 연결을 조용히 제거하는지 먼저 재현한다. 연결이 하나라도 있으면 영향 목록과 409를 반환하거나 명시적 unpublish/archive 뒤에만 삭제하며 silent detach는 금지한다.
- 기존 페이지에 관계를 자동 추정·발행하지 않는다. 명시적 관리자 선택 전에는 legacy 동작과 URL을 유지한다.

## Migration 계획

- [ ] 현재 `website_page_room_type`, `website_page_hotel`, `website_page_relation`과 인덱스/FK를 inventory하고 drift만 새 migration으로 보완한다.
- [ ] `(page_id, document_state, target_id)` 유일성, `DRAFT|PUBLISHED`, display order, relation type 제약을 additive하게 도입·validate한다.
- [ ] query plan을 기준으로 collection용 `(document_state, target, kind, published status)` 인덱스를 최소화하고 쓰기 증폭을 측정한다.
- [ ] legacy page에는 빈 relation을 유지한다. backfill이 필요하면 dry-run 결과와 ambiguity 목록을 먼저 만들고 승인된 명시 mapping만 idempotent하게 반영한다.
- [ ] rollback은 관계 write/collection flag를 끄고 legacy page resolve로 복귀한다. 관계 행은 삭제하지 않으며 master 데이터에도 cascade하지 않는다.

## API 계약

| API | 핵심 계약 |
| --- | --- |
| `GET /api/staff/website/content-reference` | 활성 hotel/room type/관련 page의 편집용 최소 read model; 가격·재고 없음 |
| page create/save | `contentKind`, `hotelId`, `connections{roomTypeIds,targetHotelIds,relatedPages}`와 expected draft version |
| publish | expected draft/published version; 관계와 snapshot을 원자적으로 승격 |
| `GET /api/website/collections` | locale, kind, 선택 hotel/태그; 카드 필드만, draft/비활성 제외 |
| page resolve | published connections와 안전한 CTA intent만; 관리자 reference catalog 비노출 |

잘못된 대상은 400, 존재하지 않는 대상은 404, stale version은 409, 권한 부족은 403으로 일관되게 처리한다. collection pagination/order는 결정적 tie-breaker를 포함한다.

## 권한·정합성

- [ ] 1단계에서 확정한 현재 역할 경계를 보존하고 `BRANCH_STAFF` write를 막는다. 한국어 `HQ_EDITOR`/`HQ_PUBLISHER` 확대는 5단계 보안 결정 전에는 넣지 않는다.
- [ ] 저장과 발행 때 hotel/room type active·소속·kind별 cardinality·related graph를 각각 재검증한다.
- [ ] room type 삭제와 page publish의 경합에서 양쪽 대상을 잠그거나 일관 snapshot으로 검증해, 공개 ROOM page만 남고 연결이 사라지는 상태를 만들지 않는다.
- [ ] draft relation 교체, media usage, version, audit을 한 transaction으로 처리하고 publish는 DRAFT→PUBLISHED 복사를 원자화한다.
- [ ] collection query는 발행 relation만 사용하고 비활성/보관 page, 미발행 번역, 다른 지점 데이터를 반환하지 않는다.
- [ ] 예약 CTA 적용 후 availability를 새로 조회하고 응답 offer를 roomTypeId로 필터링하되 request body에 CMS 가격/할인/콘텐츠 ID를 넣지 않는다.

## 관리자·고객 UI

- [ ] 생성 대화상자는 parent→kind→owner hotel/targets→slug 순으로 의존 선택을 지우며, ROOM은 같은 지점 room type 하나만 허용한다.
- [ ] PROMOTION은 하나 이상 지점과 그 합집합 room type만 보여주고 target 해제 시 불일치 선택을 같은 state update에서 제거한다.
- [ ] 고객 목록은 지점·kind 필터, 결정적 순서, 안전한 빈/오류 상태를 제공한다. 카드에는 필요한 요약만 표시한다.
- [ ] 상세 CTA는 예약 검색 상태에 hotel/roomType intent만 적용하고 날짜·인원 focus와 실시간 재조회를 유도한다. 390px에서 picker·카드·dialog가 넘치지 않는다.

## 테스트와 E2E

- [ ] migration/FK/index와 실제 PostgreSQL `EXPLAIN`으로 대표 collection query를 검증한다.
- [ ] 서버 통합 테스트로 same-hotel, promotion cardinality, inactive target, cycle, draft/published 분리, publish 원자성, 권한 matrix를 검증한다.
- [ ] 연결된 room type/page 삭제는 영향 목록과 409를 반환하고 relation·usage·audit·공개 snapshot이 무변경임을 검증한다.
- [ ] 관리자 Playwright로 ROOM/PROMOTION 생성·편집, cross-hotel 오류, stale catalog, 키보드와 390px를 확인한다.
- [ ] 고객 Playwright로 지점별 목록→상세, 빈 목록, draft 비노출, CTA→fresh availability와 selected room filter를 확인한다.
- [ ] 기존 사용자 page/URL/version/media hash와 예약 DB의 inventory/rate/reservation 행 수가 전후 동일함을 기록한다.

## 배포·롤백·완료 기준

1. 관계 schema → reference/read API → 관리자 draft write → publish/collection feature flag 순으로 배포한다.
2. published collection을 기존 navigation/resolve와 shadow 비교하고 target 누락·교차 지점·쿼리 지연을 감시한다.
3. 객실 유형 삭제 보호는 이 단계의 첫 독립 호환 배포로 분리하고 이후 롤백 대상에서 제외한다. `RoomTypeDeletionService`의 silent detach를 제거한 뒤 구 binary로 돌아가야 하면 room-type DELETE kill switch 또는 DB guard로 DRAFT/PUBLISHED 참조 삭제를 계속 막는다.
4. 중단 시 relation publish와 collection write flag만 끄고 기존 published relation read와 삭제 보호는 유지한다. 이미 저장된 draft/published relation은 보존해 재개 가능하게 한다.
5. 1단계 감사가 실제 격차와 정확한 Create/Modify/Test 경로·명령을 확정한 별도 구현 계획을 승인하기 전에는 이 절을 실행하지 않는다. 이후 DB commit 수와 API/collection 결과, 권한·정합성 테스트, 관리자/고객 E2E, 예약 도메인 무변경 증거가 일치해야 완료다.
