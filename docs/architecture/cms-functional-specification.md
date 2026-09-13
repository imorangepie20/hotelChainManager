# 고객 웹 CMS 기능 명세

최종 갱신: 2026-09-13
적용 대상: `STAY HANEUL` 고객 웹과 본사 관리자 `/dashboard/website`

> 이 문서는 현재 구현된 CMS 기능을 기록한다. 롯데리조트급 콘텐츠 운영을 위한 재정의 목표와 이후 구현 경계는 [롯데리조트급 CMS 기능 설계](lotte-resort-level-cms-functional-design.md)를 따른다.

## 1. 목적과 권한

본사 관리자는 개발 배포 없이 고객 웹의 홈, 세 지점 랜딩, 브랜드·캠페인 일반 페이지와 이미지를 초안으로 편집하고 발행한다. 고객은 공개된 발행본만 본다. 예약 가능 객실, 가격, 결제, 예약 확정은 CMS의 관리 대상이 아니며 Spring 예약 도메인이 계속 소유한다.

| 역할 | CMS 권한 |
| --- | --- |
| 비로그인 고객 | 공개 메뉴·발행 페이지·활성 업로드 이미지만 조회 |
| `HQ_ADMIN` | 모든 페이지와 미디어의 조회·편집·발행·수명주기 관리 |
| `BRANCH_STAFF` | CMS 관리 API 접근 불가 (`403`) |

관리자 요청은 `X-Staff-Session`을 사용한다. 브라우저의 버튼 비활성화는 보조 UX이며 권한·버전·수명주기 검증은 Spring API가 최종 수행한다.

## 2. 관리 대상

| 유형 | 경로·소유 | 운영 규칙 |
| --- | --- | --- |
| `HOME_PAGE` | 루트 `/`, 단일 페이지 | slug `home`, 메뉴 비노출, 부모·호텔 없음. 홈 전용 API로만 저장·발행 |
| `HOTEL_LANDING` | `/stays/sokcho`, `/stays/seoraksan`, `/stays/jeju` | 각 호텔과 연결. 지점 소개·경험·오퍼·도착 안내를 운영 |
| `SECTION` | 예: `/brand` | 공개 메뉴의 그룹. 현재 일반 페이지 생성용 부모 |
| `CONTENT_PAGE` | 예: `/brand/story` | 호텔에 속하지 않는 브랜드·캠페인 페이지. 현재는 `SECTION`의 직계 자식만 허용 |
| 미디어 자산 | 내장 이미지 또는 업로드 PNG/JPEG | 페이지별 alt와 별도의 기본 alt·사용 위치·상태·버전을 관리 |

`CONTENT_PAGE`의 경로 깊이는 현재 두 단계까지이며, 임의의 깊은 트리와 부모 이동은 후속 범위다.

## 3. 초안, 발행본, 버전

모든 페이지는 초안과 발행본을 분리한다.

1. **저장**: 관리자가 수정한 초안과 초안 버전만 갱신한다. 고객 웹은 바뀌지 않는다.
2. **발행**: 저장된 초안의 콘텐츠·경로·메뉴를 발행본으로 복사하고 불변 발행 snapshot을 남긴다.
3. **공개**: 고객 웹은 navigation과 resolve API에서 발행본만 읽는다.
4. **동시 편집 방지**: 저장은 `expectedDraftVersion`, 발행과 수명주기 작업은 현재 draft/published/lifecycle 버전을 함께 보낸다. 서버 조건과 실제 값이 다르면 `409`을 반환하고 최신 데이터를 다시 읽게 한다.

발행 이력은 `website_page_version`, 생성·저장·발행·보관·복원·발행본 복원 기록은 `website_page_audit`에 남긴다.

## 4. 페이지 편집 기능

### 4.1 홈

- 히어로, 본문, CTA, 선택 SEO 제목·설명을 편집한다.
- 고객 홈의 노출 순서는 `CMS HERO → 코드 소유 예약 검색·AI → CMS TEXT/CTA → 지점 콘텐츠`다.
- slug, 경로, 메뉴 노출, 메뉴 순서는 고정이며 일반 페이지 생성·발행 API로 우회할 수 없다.

### 4.2 지점 랜딩

- 속초·설악산·제주도별 히어로, 도착 안내, 경험 카드, 오퍼 카드를 편집한다.
- 경험·오퍼 카드는 추가·삭제·위·아래 순서 변경을 지원한다.
- 슬러그, 메뉴 이름, 메뉴 노출, 메뉴 순서를 편집하고 초안·발행 주소를 확인한다.
- 지점 예약 바는 코드가 소유하며 발행 페이지의 신뢰 가능한 `hotelId`만 예약 검색으로 전달한다.

### 4.3 일반 콘텐츠 페이지

- `SECTION`을 선택해 새 페이지의 메뉴 이름, slug, 메뉴 노출, 메뉴 순서, SEO, HERO·TEXT·CTA를 입력해 생성한다.
- 문서는 `seo`와 allowlist 블록만 사용한다. 첫 블록은 하나의 `HERO`여야 하며 `TEXT`, `CTA`를 추가할 수 있다.
- 원격 URL, `data:` URL, 상위 경로, 외부 CTA, HTML·스크립트·허용되지 않은 블록과 필드는 저장 전에 서버에서 거부한다.
- 편집 중인 메모리를 고객 화면과 유사한 읽기 전용 dialog에서 미리 볼 수 있다. 이 미리보기는 저장·발행·보관·복원 요청을 만들지 않으며 CTA도 이동하지 않는다.

### 4.3.1 저장 초안 실제 URL 미리보기

- 본사 관리자·편집자·게시자는 활성 홈·지점 랜딩·일반 페이지의 저장된 한국어 또는 기존 영어 초안을 실제 고객 URL에서 검토한다. 미저장 변경사항이 있으면 먼저 초안 저장이 필요하다. 기존 인메모리 dialog는 별도로 유지한다.
- 발급 시 페이지·언어·초안 버전·경로에 결합한 10분 bearer grant를 만든다. DB에는 SHA-256 해시만 저장하며 같은 발급자·페이지·언어의 이전 활성 링크만 폐기한다. 발급자 또는 본사 관리자는 링크를 폐기할 수 있다.
- 고객은 `#preview=<token>`을 요청 전에 주소에서 제거하고 해당 탭의 `sessionStorage`에만 보관한다. 일반 이동이나 종료 시 공개본으로 돌아가며, 잘못된 링크는 404, 만료·폐기·초안 변경은 410으로 처리하고 공개 콘텐츠로 대체하지 않는다.
- 기존 고객 renderer, 만료 배너, `noindex,nofollow`, 응답 `no-store`를 사용한다. 만료 후 초안을 화면에서 제거하고 예약 검색·예약·결제·취소·AI·콘텐츠 CTA를 키보드와 포인터 모두에서 차단한다.
- API는 HTTPS를 기본 요구하고 `dev` profile에서만 로컬 HTTP를 허용한다. 운영 TLS 프록시 설정은 배포 전 별도 확인한다. 상세 검증과 미검증 범위는 [변경 기록](../changes/2026-09-13-authenticated-saved-draft-url-preview.md)을 따른다.

### 4.4 SEO

- 선택 `seo.title`과 `seo.description`을 지점 랜딩·홈·일반 페이지에 저장할 수 있다.
- 둘 중 하나를 입력하면 모두 필요하며 길이는 각각 최대 60자, 160자다.
- 고객 Vite SPA는 유효한 발행 SEO를 브라우저 title과 description meta에 반영한다. canonical, OG, robots 및 크롤러용 서버 렌더링은 후속 범위다.

## 5. 페이지 수명주기와 이력

### 5.1 보관과 복원

일반 페이지는 다음 상태를 가진다.

| 상태 | 고객 공개 | 관리자 작업 |
| --- | --- | --- |
| `ACTIVE` | 발행본이 있으면 메뉴·resolve로 공개 | 저장, 발행, 버전 비교·복원, 보관 |
| `ARCHIVED` | 공개 본문·메뉴·발행 미디어 사용 위치를 비워 즉시 비공개 | 읽기, 비교, 초안으로 복원, 영구 삭제 |

- 보관은 현재 URL, 초안, 발행 이력, 초안 미디어 사용 위치를 보존한다.
- 복원은 `ACTIVE` 초안으로만 되돌린다. 고객 웹에 다시 보이려면 본사가 검토 후 재발행해야 한다.
- `HOME_PAGE`, `HOTEL_LANDING`, `SECTION`은 보관 대상이 아니다.
- 보관 상태에서는 폼·미디어 선택·저장·발행을 비활성화한다.

### 5.2 발행본 복원과 비교

- 활성 일반 페이지는 현재보다 이전인 발행본의 **콘텐츠만** 현재 초안으로 복원할 수 있다.
- 복원은 현재 URL·메뉴·부모·공개본을 바꾸지 않고 초안 버전을 증가시키며 `VERSION_RESTORED` audit과 초안 미디어 사용 위치를 갱신한다.
- 두 발행 snapshot은 `ACTIVE`와 `ARCHIVED` 일반 페이지 모두에서 읽기 전용으로 비교할 수 있다.
- 비교 화면은 경로·메뉴·SEO·HERO·TEXT·CTA를 나란히 보여 주고 `동일`, `변경`, `추가`, `제거`를 표시한다. 과거 이미지 preview와 블록 이동 판정은 아직 제공하지 않는다.

### 5.3 영구 삭제

- 영구 삭제는 `ARCHIVED CONTENT_PAGE`에만 노출한다.
- 확인 dialog에서 복구 불가와 정리 대상(발행 이력, 감사 이력, 페이지별 미디어 사용 위치)을 설명하고 명시적 확정을 요구한다.
- 서버는 lifecycle·draft·published 세 version, `CONTENT_PAGE` 유형, 보관 상태, 자식 페이지 없음 여부를 잠금 상태에서 다시 확인한다.
- 성공하면 `website_media_usage` → `website_page_version` → `website_page_audit` → `website_page` 순으로 같은 트랜잭션에서 정리하고 `204 No Content`를 반환한다.
- 미디어 자산 행과 업로드 파일은 유지한다. 활성 페이지, 홈, 지점 랜딩, SECTION은 삭제할 수 없다.

## 6. 미디어 관리

### 6.1 카탈로그와 업로드

- 본사는 활성 자산과 보관 자산을 분리해 조회하고, 자산 카드에서 이름·규격·기본 alt·현재 version·사용 위치 수를 확인한다.
- PNG/JPEG만 업로드할 수 있다. 서버는 실제 이미지 형식·디코드, 최대 10 MiB, 최대 24,000,000 픽셀을 검사한다.
- 업로드 파일은 UUID storage key로 저장하고 `/api/website/media/{id}/content` 전달 URL을 발급한다. 내장 정적 이미지는 기존 `/images/...` 경로를 유지한다.
- 이미지 선택은 명시적 `선택` 버튼으로 현재 편집 문서에 적용한다. 기본 alt는 선택 시 페이지별 alt의 초기값으로만 복사하며, 이후 페이지별 alt 수정은 자산 기본 alt를 바꾸지 않는다.

### 6.2 참조 보호와 상태

- 페이지 저장·생성·발행은 활성 자산 UUID와 해당 UUID의 전달 경로를 서버에서 재검증한다.
- `website_media_usage`는 페이지·초안/발행 상태·필드 경로·페이지별 alt를 기록한다.
- 사용 위치가 하나라도 있거나 현재 화면의 저장되지 않은 초안이 자산을 선택한 경우 보관 버튼을 비활성화한다. 서버도 사용 위치 0건을 다시 확인한다.
- 보관된 업로드 자산은 새 문서에서 선택할 수 없고 공개 전달도 `404`다. 파일은 유지하며 복원하면 다시 활성 자산이 된다.
- 자산 이름·기본 alt·보관·복원은 자산 version을 조건으로 처리한다. 오래된 요청은 `409 WEBSITE_MEDIA_VERSION_CONFLICT`로 거부한다.
- 참조 없는 `UPLOADED`·`ARCHIVED` 자산만 보관 후 30일과 자산 version 확인을 거쳐 영구 삭제한다. 번들·활성·참조 중인 자산은 삭제하지 않는다.

### 6.3 현재 이미지 파일 교체

- 공통 이미지 필드의 `파일 교체`는 기존 파일을 덮어쓰지 않고 새 PNG/JPEG 자산을 업로드한다. 지점 대표 이미지, 홈·일반 페이지 HERO, 갤러리의 개별 이미지에 적용한다.
- 업로드한 새 자산을 선택하고 `교체 확인`에서 기존·새 이미지와 영향 범위를 검토한 뒤 `교체하기`로 현재 메모리 초안 위치만 변경한다.
- 페이지별 alt는 유지한다. 관리자는 새 이미지의 의미에 맞는지 확인·수정해야 한다. 일반 `미디어 선택`의 기본 alt 복사 동작은 그대로다.
- 다른 위치·페이지·현재 공개본·발행 이력·기존 자산·파일은 자동으로 변경하지 않는다. 초안 저장과 명시적 발행은 기존 페이지 API·version·usage 계약을 따른다.
- 취소·업로드 실패·닫힌 뒤 도착한 업로드 응답은 페이지에 적용하지 않는다. 업로드 후 취소한 새 자산은 카탈로그에 남으며 기존 보관·삭제 정책으로 관리한다.
- 파일 덮어쓰기는 제공하지 않는다. 여러 저장 초안을 바꾸는 작업은 아래의 별도 영향 확인·확정 흐름만 사용한다.

### 6.4 활성 초안 사용 위치 일괄 교체

- `HQ_ADMIN`은 활성 원본 자산에서 새 PNG/JPEG 자산을 업로드한 뒤 교체 영향부터 조회한다. 대상은 업로드로 생성된 `ACTIVE` 자산이어야 한다.
- 영향 목록에는 활성 `HOME_PAGE`, `HOTEL_LANDING`, `CONTENT_PAGE`의 한국어·영어 `DRAFT` 위치와 각 초안 version을 표시한다. 발행 사용 위치와 보관 페이지 초안은 제외 수로 따로 알린다.
- 확정 요청은 원본·대상 자산 version과 영향 조회에서 받은 `pageId`, `locale`, `fieldPath`, 초안 version 전체를 보낸다. 서버는 잠금 상태에서 현재 집합과 정확히 비교하며 하나라도 달라지면 `409 WEBSITE_MEDIA_REPLACEMENT_CONFLICT`로 전체를 거부한다.
- 성공 시 자산 UUID와 전달 URL만 교체하고 페이지별 alt·caption·블록 순서를 보존한다. 공개본·발행 usage·과거 snapshot·보관 페이지·기존 자산과 파일은 바꾸지 않는다.
- 영어 초안이 바뀌면 일반 영어 저장과 동일하게 검토·승인을 `DRAFT`로 무효화하고 `APPROVAL_INVALIDATED` event를 남긴다. 변경 audit은 허용된 `DRAFT_SAVED` action과 `MEDIA_DRAFT_USAGES_REPLACED` operation으로 기록한다.
- 새 자산 업로드만으로 초안은 바뀌지 않는다. 영향 확인 뒤 명시적 확정이 필요하며, 취소한 업로드 자산은 카탈로그에 남아 기존 보관·삭제 정책으로 관리한다.

### 6.5 한국어·영어 독립 초안과 발행

- 기존 기획의 다국어 요구를 V20 확장 단계로 구현했다. 페이지 identity·종류·지점·부모 구조는 공유하고 한국어 기존 저장 모델과 API는 유지한다. 영어 콘텐츠·메뉴·SEO·alt·캡션·연결 snapshot·버전·발행 이력은 추가 번역 테이블에서 관리한다.
- 본사 관리자가 한국어/영어를 선택한다. 영어 조회만으로 초안을 생성하지 않으며 `한국어 초안을 가져오기` 후 직접 번역·저장·발행한다. 미저장 언어 전환은 확인창으로 보호한다. 구조 이동·페이지 전체 보관/복원/삭제는 한국어 화면에서 수행한다.
- 영어 경로는 `/en` 접두사와 기존 구조 슬러그를 사용한다. 영어 발행본은 한국어 수정·이동만으로 바뀌지 않는다. 영어 초안 저장 후 재발행하면 변경된 영어 주소에 단일 301을 생성한다. 언어별 임의 슬러그는 후속 범위다.
- 공개 navigation/resolve/collection은 `locale=ko|en`을 받는다. resolve의 `/en` 경로는 locale 생략 시에도 영어로 해석한다. 미발행 영어는 404/빈 목록으로 응답하고 한국어 콘텐츠로 대체하지 않는다. 영어 메뉴는 번역된 페이지만 표시하며 미번역 SECTION 라벨 대신 자식 페이지를 루트 메뉴로 표시한다.
- 전체 페이지 보관은 두 언어 공개본과 공개 미디어 참조를 중단한다. 복원은 초안만 유지하며 언어별 재발행이 필요하다. 영구 삭제는 번역·번역 이력을 함께 제거하되 자산은 보존한다.
- 고객 영어 CMS 화면과 누락 안내를 제공하며 예약·결제·AI 전체 UI 영어화는 포함하지 않는다. 예약 이동은 한국어 예약 화면으로 연결된다는 점을 표시한다. 설계와 검증은 [다국어 변경 기록](../changes/2026-09-12-cms-locales.md)을 따른다.

### 6.6 영어 번역 검토와 승인

- V21은 영어 번역 row에 `DRAFT`, `IN_REVIEW`, `APPROVED`, `PUBLISHED` 상태와 검토 대상 `draft_version`을 추가하고, 요청·승인·반려·승인 무효화·발행 event를 최근순 최대 50건까지 보존한다. 기존 공개본 중 발행 source version과 현재 초안 version이 같은 row는 `PUBLISHED`로 backfill하며 소급 event는 만들지 않는다. 나머지는 `DRAFT`다. V22는 비-DRAFT 상태의 검토 version이 반드시 존재하도록 DB 제약을 보강한다.
- `DRAFT → IN_REVIEW → APPROVED → PUBLISHED`만 정상 진행한다. `IN_REVIEW` 반려는 1~2,000자의 사유와 함께 `DRAFT`로 돌아간다. 요청·승인 comment는 선택이며 최대 2,000자다.
- 영어 저장은 초안 version을 올리고 `IN_REVIEW`, `APPROVED`, `PUBLISHED` 상태를 `DRAFT`로 무효화해 `APPROVAL_INVALIDATED`를 남긴다. 이미 공개된 snapshot과 공개 media usage는 그대로 유지한다.
- 발행은 현재 초안 version과 연결된 `APPROVED`만 허용한다. 검토 전 direct publish, 오래된 version, 중복·잘못된 전이는 `409`로 거부한다. 공개 API에는 검토 상태·comment·담당자를 노출하지 않는다.
- 일반 페이지 보관은 영어 공개본과 공개 usage를 비우면서 `PUBLISHED`를 같은 version의 `APPROVED`로 바꾼다. 복원 후에는 보관 전에 승인된 현재 초안을 다시 발행할 수 있으며, 초안을 저장하면 기존 규칙대로 승인이 무효화된다.
- 관리자 홈·지점 랜딩·일반 페이지 영어 편집기는 공통 action bar와 상태 배지, 반려 dialog, 검토·기존 발행 이력을 사용한다. 미저장·처리 중·보관 상태에서는 전이를 막고, mutation 성공 뒤 이력 조회가 실패하면 성공 상태를 유지한 채 읽기만 다시 시도한다.
- V23은 `HQ_EDITOR`와 `HQ_PUBLISHER`를 추가한다. 편집자와 `HQ_ADMIN`은 영어 초안 작성·검토 요청, 승인자와 `HQ_ADMIN`은 승인·반려·발행을 할 수 있다. 서버는 검토 요청 event의 행위자와 승인자를 비교해 자가 승인을 거부한다. 물리 장치 검증, 예약 발행, 알림·경보, 한국어 승인 workflow는 범위 밖이다. 상세 근거는 [영어 번역 검토·승인 변경 기록](../changes/2026-09-12-cms-translation-review.md)을 따른다.

### 6.7 비동기 WebP variant

- 활성 업로드 PNG/JPEG에는 원본 폭 이하인 640px·1280px WebP 작업을 같은 DB transaction에서 멱등 enqueue한다. 기존 활성 업로드도 V25에서 같은 조건으로 backfill하며 번들·보관·작은 원본은 제외한다.
- PostgreSQL row가 `PENDING → PROCESSING → READY/FAILED` 큐와 결과 메타데이터를 함께 소유한다. worker는 `FOR UPDATE SKIP LOCKED`와 5분 lease로 한 건씩 선점하고, 30초·2분 backoff를 거쳐 최대 3회 시도한다. 만료된 attempt 3의 PROCESSING도 terminal FAILED다.
- WebP는 원본 비율을 유지해 quality 0.82로 만들고 다시 decode해 규격을 확인한다. 완료 파일은 claim별 고유 immutable storage key를 사용하며 기존 파일을 덮어쓰지 않는다. attempt와 V27 claim token이 일치하는 현재 claim만 READY를 확정한다.
- 파일·DB 경계는 transaction 결과에 맞춰 보상한다. rollback은 해당 claim 파일만 제거하고 commit만 잠금 중 확인한 이전 파일을 제거한다. 결과를 알 수 없는 `STATUS_UNKNOWN`에서는 파일을 보존한다.
- 공개 variant는 활성 업로드 자산의 READY 파일만 `image/webp`와 1년 immutable cache로 전달한다. 다른 상태, 보관·번들 자산, 실제 파일 누락은 404이며 원본으로 fallback하지 않는다.
- 관리자 미디어 선택기는 대기·처리·완료 규격과 용량·실패 원인·시도 횟수를 표시한다. 열린 대화상자의 현재 자산에 진행 중 작업이 있을 때만 2초 간격으로 해당 variant를 갱신하며, `HQ_ADMIN`은 FAILED 640·1280 작업을 폭별로 다시 시도할 수 있다.
- 보관은 원본과 READY variant 공개 전달을 함께 중단하고 복원은 기존 READY를 다시 노출하며 누락 작업만 enqueue한다. 영구 삭제는 원본과 모든 READY 파일을 함께 격리해 commit 시 제거하고 rollback 시 복구한다.
- V25·V26·V27은 additive expand migration이다. 이전 binary는 새 table·열·파일을 무시할 수 있지만, claim-token fencing은 새 코드의 claim부터 적용되므로 rolling 배포에서는 이전 worker를 drain하고 in-flight lease를 정리한 뒤 전환한다. 롤백은 이전 API image를 먼저 배포하고 table과 파일을 유지한다.
- 기존 원본 URL과 한국어·영어 페이지 JSON은 유지한다. 공개 발행본과 저장 초안 미리보기 응답은 참조 중인 활성 업로드 자산의 READY variant만 응답 전용 `mediaVariants`로 제공하며, 고객 HERO와 이미지 갤러리는 검증된 값만 `<picture>`·`srcset`으로 사용하고 원본 `<img>`를 fallback으로 둔다. 상세 결과는 [비동기 미디어 variant 변경 기록](../changes/2026-09-13-async-media-variants.md)과 [고객 반응형 미디어 변경 기록](../changes/2026-09-13-customer-responsive-media.md)을 따른다.

### 6.8 저장소 정합성 점검과 S3 호환 이관

- `HQ_ADMIN`은 미디어 선택기에서 수동으로 로컬·S3 저장소를 읽기 전용 점검한다. 서버는 업로드 원본과 storage key가 있는 READY variant의 DB 참조를 저장소별 실제 객체와 비교한다. 기존 최상위 점검 필드는 현재 읽기 우선 저장소 결과로 유지한다.
- DB가 참조하지만 없는 파일은 누락, `.trash` 밖의 참조 없는 일반 파일은 orphan, 수정 후 10분이 지난 참조 없는 `.tmp`는 오래된 임시 파일로 분리한다. 최근 `.tmp`와 `.trash`는 진행 중 처리·transaction 격리 영역이므로 제외한다.
- 응답은 정렬된 상대 storage key와 점검 시각만 반환한다. 절대·상위 경로는 노출하지 않으며 파일 삭제·복구·격리, DB 수정과 정기 실행은 하지 않는다.
- 저장 모드는 `local`, `mirror`, `s3-primary`다. 모든 지원 모드는 로컬 사본을 유지한다. `mirror`는 로컬을 읽고 로컬 뒤 S3에 기록하며, `s3-primary`는 S3를 우선 읽고 로컬로 한 번 fallback한 뒤 S3와 로컬에 기록한다. 공개 URL·DB storage key·cache 계약은 바꾸지 않는다.
- `mirror`의 명시적 backfill은 DB가 참조하는 `localOnly` 객체를 한 번에 최대 100개까지 S3에 추가 복사한다. 다른 내용의 기존 S3 객체는 덮어쓰지 않고 mismatch로 보고하며, 로컬 파일과 DB는 변경하지 않는다. 상세 운영 절차와 검증은 [S3 미디어 저장소 이관 기록](../changes/2026-09-13-s3-media-storage-migration.md)을 따른다.

## 7. 주요 API 계약

| 구분 | API | 용도 |
| --- | --- | --- |
| 공개 | `GET /api/website/navigation` | 발행 메뉴 조회 |
| 공개 | `GET /api/website/pages/resolve?path=...` | 발행 페이지와 신뢰 가능한 `hotelId` 조회 |
| 공개 | `GET /api/website/media/{mediaId}/content` | 활성 업로드 이미지 전달 |
| 공개 | `GET /api/website/media/{mediaId}/variants/{targetWidth}.webp` | 활성 업로드 READY WebP 640·1280 전달 |
| 본사 | `GET/PUT /api/staff/website/home` | 홈 초안 조회·저장 |
| 본사 | `POST /api/staff/website/home/publish` | 홈 발행 |
| 본사 | `GET /api/staff/website/pages` | 페이지 트리 조회 |
| 본사 | `POST /api/staff/website/pages` | 일반 페이지 생성 |
| 본사 | `GET/PUT /api/staff/website/pages/{pageId}` | 일반 페이지 초안 조회·저장 |
| 본사 | `GET/POST/PUT /api/staff/website/pages/{pageId}/translations/en` | 영어 초안 조회·명시적 가져오기·저장 |
| 본사 | `POST /api/staff/website/pages/{pageId}/translations/en/publish` | 영어 독립 발행 |
| 본사 | `GET /api/staff/website/pages/{pageId}/translations/en/versions` | 영어 발행 이력 |
| 본사 | `GET /api/staff/website/pages/{pageId}/translations/en/review` | 영어 검토 상태와 최근 event 최대 50건 조회 |
| 본사 | `POST /api/staff/website/pages/{pageId}/translations/en/review/request` | 현재 영어 초안 검토 요청 |
| 본사 | `POST /api/staff/website/pages/{pageId}/translations/en/review/approve` | 검토 중인 같은 version 승인 |
| 본사 | `POST /api/staff/website/pages/{pageId}/translations/en/review/reject` | 필수 사유와 함께 검토 반려 |
| 본사 | `POST /api/staff/website/pages/{pageId}/publish` | 일반·지점 랜딩 발행 |
| 본사 | `POST /api/staff/website/pages/{pageId}/archive`, `/restore` | 일반 페이지 보관·초안 복원 |
| 본사 | `DELETE /api/staff/website/pages/{pageId}` | 보관된 일반 페이지 영구 삭제 |
| 본사 | `GET /api/staff/website/pages/{pageId}/versions` | 발행 이력 조회 |
| 본사 | `POST /api/staff/website/pages/{pageId}/versions/{sourceVersion}/restore-draft` | 이전 발행 콘텐츠를 초안으로 복원 |
| 본사 | `GET /api/staff/website/pages/{pageId}/versions/compare?baseVersion=&compareVersion=` | 두 발행 snapshot 비교 |
| 본사 | `GET/POST /api/staff/website/media` | 미디어 카탈로그·업로드 |
| 본사 관리자 | `GET /api/staff/website/media/storage-audit` | DB 참조와 저장소별 실제 미디어 객체의 읽기 전용 정합성 점검 |
| 본사 관리자 | `GET /api/staff/website/media/storage-migration` | 로컬·S3 참조 객체 일치 상태와 fallback 횟수 조회 |
| 본사 관리자 | `POST /api/staff/website/media/storage-migration/backfill` | `mirror`에서 로컬 객체를 S3로 최대 100개 추가 복사 |
| 본사 | `GET /api/staff/website/media/{mediaId}/usages` | 미디어 사용 위치 조회 |
| 본사 관리자 | `POST /api/staff/website/media/{mediaId}/variants/{targetWidth}/retry` | FAILED WebP 640·1280 작업 수동 재시도 |
| 본사 관리자 | `GET /api/staff/website/media/{mediaId}/draft-replacement-impact?targetMediaId=...` | 활성 한국어·영어 초안 교체 영향 조회 |
| 본사 관리자 | `POST /api/staff/website/media/{mediaId}/draft-replacements` | 확인한 활성 초안 사용 위치 원자적 교체 |
| 본사 | `PATCH /api/staff/website/media/{mediaId}` | 자산 이름·기본 alt 수정 |
| 본사 | `POST /api/staff/website/media/{mediaId}/archive`, `/restore` | 참조 없는 자산 보관·복원 |

## 8. 현재 완료 기준과 후속 범위

현재 CMS는 초안/발행 분리, 페이지 트리, 홈·지점·일반 페이지 편집, SEO, 안전한 이미지 카탈로그·업로드·참조 보호, 미디어 보관/복원, 일반 페이지 보관/복원/영구 삭제, 발행 이력 복원·비교, 저장 전 preview까지 구현했다.

페이지 부모 이동·redirect·최대 4단계 트리, 보관된 업로드 자산의 30일 유예 영구 삭제, 현재 이미지 위치의 파일 교체, 활성 한국어·영어 초안 사용 위치 일괄 교체, 비동기 640px·1280px WebP variant와 고객 HERO·갤러리 `<picture>`·`srcset`, 저장소별 읽기 전용 audit와 `local → mirror → s3-primary` S3 호환 이관, 한국어·영어 독립 초안/발행과 영어 번역 검토·승인 첫 단계와 인증된 저장 초안 실제 URL 미리보기도 구현했다. 다음 단계는 언어별 임의 슬러그·SECTION 번역·영어 이력 복원/비교, CDN 계정·DNS·purge 연동, canonical·OG·robots, 예약 발행·알림, 한국어 승인과 블록 이동 감지다.

검증 기록과 테스트 범위는 [CMS 변경 기록](../changes/2026-09-10-web-content-management.md), 전체 제품 경계는 [전체 구현 설계서](full-site-implementation-design.md), 최신 진행 상태는 [현재 개발 상태](../overview/current-development-context.md)에 기록한다.
