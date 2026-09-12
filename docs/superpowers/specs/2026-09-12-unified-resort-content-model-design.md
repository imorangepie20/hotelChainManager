# 통합 리조트 콘텐츠 모델 구현 설계

최종 갱신: 2026-09-12  
상태: 사용자 승인 후 구현 계획 작성 대기  
적용 대상: Spring API `4080`, 관리자 `4001`, 고객 웹 `4000`

## 1. 목적과 범위

현재 CMS의 `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`, `CONTENT_PAGE` 초안·발행·감사·미디어 규칙을 유지하면서, 객실·다이닝·부대시설·경험·프로모션·이용 안내·브랜드 상세를 하나의 콘텐츠 페이지 모델로 운영한다.

이번 범위는 `ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`, `PROMOTION`, `GUIDE`, `BRAND`의 생성·편집·연결·발행·공개 상세/목록·미리보기까지다. 번역, 검토/승인, 예약 발행, redirect, OG/robots, 미디어 variant와 파일 교체는 후속 범위로 남긴다.

가격, 재고, 판매 가능 여부, 요금제, 예약 확정과 결제는 계속 예약 도메인의 소유다. CMS는 이를 저장하거나 고객에게 확정값처럼 전달하지 않는다.

## 2. 선택한 구조

`website_page`를 모든 콘텐츠의 identity와 버전 경계로 유지하고 `content_kind`를 추가한다. 유형별로 별도 본문 테이블을 만들지 않고, 본문은 기존의 구조화 JSON block 문서에 둔다. 지점·객실 유형·관련 페이지 연결만 정규화된 별도 테이블로 분리한다.

이 구조는 현재 초안/발행/보관/복원/미디어 usage를 재사용하면서도, 객실과 `room_type`의 소속 및 다지점 프로모션의 대상을 서버에서 확인할 수 있게 한다. JSON에 ID만 저장하는 방식은 관계 무결성과 발행본 불변성을 보장하지 못하므로 사용하지 않는다.

## 3. 페이지·트리 모델

### 3.1 기존 유형과 콘텐츠 종류

| `page_type` | `content_kind` | 소유·역할 |
| --- | --- | --- |
| `HOME_PAGE` | `HOME` | 체인 단일 홈. 예약 바는 코드 소유 |
| `HOTEL_LANDING` | `DESTINATION` | 단일 지점 랜딩 |
| `SECTION` | 없음 | 메뉴·목록 경로를 만드는 체인/지점 범위 컨테이너 |
| `CONTENT_PAGE` | `ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`, `PROMOTION`, `GUIDE`, `BRAND` | 구조화 상세 콘텐츠 |

기존 홈·지점 랜딩은 각각 `HOME`, `DESTINATION`으로 이관한다. 기존 일반 페이지와 `/brand/forest-gallery-demo`는 `BRAND`로 이관한다. 이관은 기존 slug·경로·초안·발행본·발행 이력을 보존하고 사용자가 만든 콘텐츠를 재발행하지 않는다.

`ROOM`, `DINING`, `FACILITY`, `EXPERIENCE`는 `website_page.hotel_id`가 필수다. `PROMOTION`, `BRAND`는 체인 공통이므로 `hotel_id`가 없고, `GUIDE`는 체인 또는 지점 소유를 허용한다. 이번 범위에서 상세 콘텐츠의 직접 부모는 `SECTION`만 허용한다.

### 3.2 트리와 경로

`SECTION`은 체인 공통 또는 특정 지점에 속할 수 있다. 깊이는 경로 segment 기준 최대 4이며, 아래 경로를 허용한다.

| 목적 | 예시 |
| --- | --- |
| 객실 목록·상세 | `/stays/seoraksan/rooms`, `/stays/seoraksan/rooms/forest-suite` |
| 다이닝·시설·경험 | `/stays/jeju/dining`, `/stays/jeju/dining/ocean-table` |
| 체인 프로모션 | `/offers`, `/offers/autumn-escape` |
| 안내·브랜드 | `/guides/check-in`, `/brand/story` |

서버는 새 페이지를 만들거나 부모를 바꿀 때 부모 범위와 segment 수를 검증한다. 지점 범위 `SECTION` 아래의 상세는 같은 `hotel_id`여야 한다. 발행된 하위 페이지가 있는 부모 이동과 redirect는 이번 범위에 넣지 않는다.

## 4. 연결 데이터와 발행본

연결은 본문 JSON과 분리하고, `DRAFT`와 `PUBLISHED` 상태를 각각 보관한다. 공개 API는 `PUBLISHED` 연결만 읽는다. 저장은 초안 연결만 갱신하고, 발행은 같은 트랜잭션에서 초안 연결을 발행 연결로 교체한 뒤 version snapshot에 포함한다.

| 테이블 | 핵심 열 | 규칙 |
| --- | --- | --- |
| `website_page_room_type` | `page_id`, `document_state`, `room_type_id` | `ROOM`은 상태별 정확히 하나. `PROMOTION`은 선택 대상 0개 이상. 모두 소속 지점과 일치해야 함 |
| `website_page_hotel` | `page_id`, `document_state`, `hotel_id` | `PROMOTION`은 상태별 하나 이상인 대상 지점 |
| `website_page_relation` | `page_id`, `document_state`, `target_page_id`, `relation_type`, `display_order` | 관련 콘텐츠·수동 카드. 자기 참조·순환·중복 금지 |

보관 시 공개 연결을 제거하고, 복원 시 초안 연결만 남긴다. 영구 삭제는 초안·발행 연결과 version/audit/media usage를 같은 트랜잭션에서 정리한다. 기존 발행 snapshot에는 당시 `content_kind`와 발행 연결 snapshot을 보존한다.

## 5. 블록과 유형별 최소 계약

모든 새 블록은 stable `blockId`를 가진다. 과거 발행 snapshot은 수정하지 않는다. 기존 블록은 page ID·발행 version·순서에서 결정적인 legacy ID를 응답 시 계산하고, 이후 초안 저장 때만 현재 초안 문서에 block ID를 기록한다. 따라서 과거 이력의 원문은 불변이고, 비교 화면은 legacy ID로 같은 block을 대응할 수 있다.

| 콘텐츠 종류 | 필수 블록/연결 | 선택 블록 |
| --- | --- | --- |
| `ROOM` | HERO, IMAGE_GALLERY, SPEC_TABLE, BOOKING_CTA, room type 1개 | FEATURE_GRID, ACCORDION, NOTICE_LIST, RELATED_COLLECTION |
| `DINING` | HERO, IMAGE_GALLERY, OPERATING_HOURS | RICH_TEXT, LOCATION, NOTICE_LIST, ACCORDION |
| `FACILITY` | HERO, IMAGE_GALLERY, SPEC_TABLE 또는 OPERATING_HOURS | LOCATION, ACCORDION, NOTICE_LIST |
| `EXPERIENCE` | HERO, RICH_TEXT | IMAGE_GALLERY, SPEC_TABLE, NOTICE_LIST, BOOKING_CTA |
| `PROMOTION` | HERO, PROMOTION_SUMMARY, BOOKING_CTA, 대상 지점 1개 이상 | IMAGE_GALLERY, FEATURE_GRID, NOTICE_LIST, RELATED_COLLECTION |
| `GUIDE` | HERO 또는 RICH_TEXT | SPEC_TABLE, LOCATION, ACCORDION, NOTICE_LIST |
| `BRAND` | HERO 또는 RICH_TEXT | IMAGE_GALLERY, FEATURE_GRID, CTA, RELATED_COLLECTION |

서버 validator와 고객 parser는 같은 block allowlist, 필수값, 길이·항목 수 상한, UUID 미디어 전달 경로, 내부 CTA 규칙을 사용한다. 외부 URL, HTML, script, markdown, data URL 및 상위 경로는 계속 거부한다.

## 6. 관리자 API와 편집 흐름

기존 일반 페이지 API를 확장해 `contentKind`, `hotelId`, `connections`를 문서에 포함한다. 저장·발행·보관·복원은 현재의 낙관적 version과 본사 권한을 그대로 요구한다.

| API | 역할 |
| --- | --- |
| `GET /api/staff/website/content-reference` | 활성 지점, 지점별 room type, 공개 후보 페이지를 반환. 가격·재고는 반환하지 않음 |
| `POST /api/staff/website/pages` | 부모·유형·소유 지점·초안 문서·초안 연결을 원자적으로 생성 |
| `GET/PUT /api/staff/website/pages/{id}` | content kind·연결·문서를 함께 조회/저장 |
| `POST /api/staff/website/pages/{id}/publish` | block·미디어·지점·room type·관련 페이지를 재검증하고 발행 연결·snapshot을 갱신 |

관리자는 생성 시 유형과 부모를 먼저 선택한다. 유형별 폼은 필요한 block만 추가할 수 있고, 객실은 지점에 속한 room type만 선택할 수 있다. 프로모션은 하나 이상의 대상 지점을 선택하고, 객실 유형을 고르면 선택된 지점 중 하나에 속하는지 즉시 표시한다. 미리보기는 기존 데스크톱/390px 전환을 그대로 사용하며, CTA는 이동하지 않는다.

## 7. 공개 API와 고객 흐름

`GET /api/website/pages/resolve?path=...`는 발행된 `contentKind`, 본문, 안전한 미디어, 지점/room type/프로모션 대상 연결을 반환한다. 초안, 내부 ID, 관리자 메모와 미발행 연결은 반환하지 않는다.

`GET /api/website/collections?hotelSlug=&kind=`는 발행된 카드 read model만 반환한다. 카드에는 대표 이미지, 분류, 제목, 요약, 핵심 수치 또는 기간, 상세 경로만 포함한다. 빈 목록은 고객 웹에서 지점 안내와 관련 콘텐츠로 대체한다.

객실 CTA는 지점 ID와 room type ID를 고객 예약 상태에 전달한다. 고객은 날짜·인원을 입력하고 기존 availability API를 다시 호출한다. 응답의 실제 `roomTypeId`로 결과를 좁히며, CMS가 가격·할인액·재고를 요청이나 예약 확정에 전달하지 않는다. 프로모션 CTA는 대상 지점 기본값만 전달하며, 표시 가격을 예약 요청에 보내지 않는다.

## 8. 서버 검증과 오류

- 본사 외 관리자 요청은 현재처럼 `403`으로 거부한다.
- `ROOM`은 room type 하나, 소유 지점, 같은 지점 범위를 요구한다. 존재하지 않거나 타 지점 room type은 저장·발행 모두 거부한다.
- `PROMOTION`은 대상 지점 하나 이상을 요구한다. 연결한 room type은 대상 지점 중 하나에 속해야 한다.
- 지점 상세 `DINING`, `FACILITY`, `EXPERIENCE`는 지점 소유와 부모 범위를 요구한다. `GUIDE`는 체인 또는 지점 범위를 허용한다.
- 관련 페이지는 존재·활성 상태·순환 없음·공개 연결의 발행 상태를 발행 직전에 다시 검증한다.
- 저장·발행·보관·삭제는 연결 행, 미디어 usage, version snapshot, audit을 한 트랜잭션으로 처리한다. 충돌은 기존 `409` 규칙을 따른다.

## 9. 이관과 안전성

새 migration은 기존 공개 경로와 발행본을 바꾸지 않는다. 기존 페이지는 동등한 content kind와 legacy block ID만 추가하며, 초안과 발행본이 다른 경우 각각의 문서를 독립적으로 보존한다. 개발 seed는 새 상세 콘텐츠를 자동 발행하지 않는다.

실제 사용자가 만든 페이지·자산에는 검증 목적으로 저장·발행·보관·삭제 요청을 보내지 않는다. Docker API 재빌드는 migration과 focused integration test가 통과한 뒤에만 수행한다.

## 10. 검증 기준

1. 서버 통합 테스트는 모든 콘텐츠 종류의 생성·저장·발행, room type/지점 불일치, 다지점 프로모션, 관계 순환, 권한, 오래된 version, 보관/복원, 발행 snapshot 불변성과 공개 목록의 초안/보관 제외를 검증한다.
2. 고객 parser 테스트는 모든 추가 block의 정상·경계·거부 입력, 안전한 미디어·CTA, content kind별 상세와 목록 카드 계약을 검증한다.
3. 관리자 Chromium E2E는 유형 선택, 지점·room type 선택 제한, 저장 전 미리보기, 390px 전환, 저장/발행 payload와 오류 복구를 검증한다.
4. 고객 브라우저는 객실·다이닝·시설·경험·프로모션·안내·브랜드의 공개 경로, 목록→상세, 객실 CTA의 예약 조건 적용과 모바일 가로 넘침을 확인한다.
5. 변경 기록에는 migration 이유, 검증 결과, 미검증 항목, API 재빌드/실제 사용자 데이터 미변경 여부를 한국어로 기록한다.

## 11. 범위 밖

- 한국어/영어 번역과 번역 승인
- `HQ_EDITOR`·`HQ_PUBLISHER`, 검토 코멘트, 예약 발행
- 발행된 부모 이동, redirect, canonical/OG/robots/sitemap
- 미디어 variant·crop·AVIF/WebP·파일 교체·삭제 유예
- CMS가 소유하는 가격·재고·예약 확정 또는 외부 예약 URL
