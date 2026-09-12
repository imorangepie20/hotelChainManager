# 고객 웹 CMS 기능 명세

최종 갱신: 2026-09-11  
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
- 보관된 업로드 자산의 영구 삭제와 파일 교체는 후속 범위다. 번들 이미지와 참조 중인 자산은 향후에도 삭제 대상에서 제외한다.

## 7. 주요 API 계약

| 구분 | API | 용도 |
| --- | --- | --- |
| 공개 | `GET /api/website/navigation` | 발행 메뉴 조회 |
| 공개 | `GET /api/website/pages/resolve?path=...` | 발행 페이지와 신뢰 가능한 `hotelId` 조회 |
| 공개 | `GET /api/website/media/{mediaId}/content` | 활성 업로드 이미지 전달 |
| 본사 | `GET/PUT /api/staff/website/home` | 홈 초안 조회·저장 |
| 본사 | `POST /api/staff/website/home/publish` | 홈 발행 |
| 본사 | `GET /api/staff/website/pages` | 페이지 트리 조회 |
| 본사 | `POST /api/staff/website/pages` | 일반 페이지 생성 |
| 본사 | `GET/PUT /api/staff/website/pages/{pageId}` | 일반 페이지 초안 조회·저장 |
| 본사 | `POST /api/staff/website/pages/{pageId}/publish` | 일반·지점 랜딩 발행 |
| 본사 | `POST /api/staff/website/pages/{pageId}/archive`, `/restore` | 일반 페이지 보관·초안 복원 |
| 본사 | `DELETE /api/staff/website/pages/{pageId}` | 보관된 일반 페이지 영구 삭제 |
| 본사 | `GET /api/staff/website/pages/{pageId}/versions` | 발행 이력 조회 |
| 본사 | `POST /api/staff/website/pages/{pageId}/versions/{sourceVersion}/restore-draft` | 이전 발행 콘텐츠를 초안으로 복원 |
| 본사 | `GET /api/staff/website/pages/{pageId}/versions/compare?baseVersion=&compareVersion=` | 두 발행 snapshot 비교 |
| 본사 | `GET/POST /api/staff/website/media` | 미디어 카탈로그·업로드 |
| 본사 | `GET /api/staff/website/media/{mediaId}/usages` | 미디어 사용 위치 조회 |
| 본사 | `PATCH /api/staff/website/media/{mediaId}` | 자산 이름·기본 alt 수정 |
| 본사 | `POST /api/staff/website/media/{mediaId}/archive`, `/restore` | 참조 없는 자산 보관·복원 |

## 8. 현재 완료 기준과 후속 범위

현재 CMS는 초안/발행 분리, 페이지 트리, 홈·지점·일반 페이지 편집, SEO, 안전한 이미지 카탈로그·업로드·참조 보호, 미디어 보관/복원, 일반 페이지 보관/복원/영구 삭제, 발행 이력 복원·비교, 저장 전 preview까지 구현했다.

다음 단계는 페이지 부모 이동과 깊은 트리, 보관된 업로드 자산의 영구 삭제·파일 교체, 한국어·영어 번역과 승인, 인증된 초안 URL preview, canonical·OG·robots, 예약 발행, 블록 이동 감지다.

검증 기록과 테스트 범위는 [CMS 변경 기록](../changes/2026-09-10-web-content-management.md), 전체 제품 경계는 [전체 구현 설계서](full-site-implementation-design.md), 최신 진행 상태는 [현재 개발 상태](../overview/current-development-context.md)에 기록한다.
