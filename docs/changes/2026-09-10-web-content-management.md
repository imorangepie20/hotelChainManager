# 고객 웹 CMS 1단계 변경 기록

최종 갱신: 2026-09-11

## 변경 이유와 현재 범위
본사가 고객 웹의 지점별 콘텐츠와 발행 주소·메뉴를 운영할 수 있도록 초안·발행본을 분리하고 공개 조회를 연결했다. V10은 지점 랜딩 외에 `SECTION` 아래의 안전한 일반 콘텐츠 페이지를, V11은 루트 `/`의 고정 홈페이지 CMS를, V12는 안전한 이미지 카탈로그와 업로드·사용 위치를, V13은 자산 메타데이터와 참조 보호 보관·복원을, V14는 일반 페이지의 공개 중단과 초안 복원을 추가했다. 목표인 리조트 운영 수준 CMS 전체가 완성된 상태는 아니다.

## 구현 내용
- `V8__web_content.sql`: 지점별 초안·발행본, 발행 버전, 저장·발행 감사 로그 테이블. 마이그레이션 시 존재하는 호텔에는 빈 문서를 만든다.
- 개발 프로필의 `R__web_content_data.sql`: 속초·설악산·제주도의 기존 랜딩 콘텐츠를 초안과 발행본에 이관한다. 두 문서가 모두 빈 경우에만 갱신하며, 초기 이관 자체는 직원 발행 이력·감사 로그를 생성하지 않는다.
- Spring `webcontent`: 공개 발행본 조회, 본사 전용 초안 조회·저장·발행·버전 조회. `X-Staff-Session`으로 `HQ_ADMIN`을 확인한다. 현재 지점 직원은 CMS 관리 조회도 허용하지 않는다.
- 신규 호텔의 관리 요청 시 콘텐츠 행을 생성한다. 존재하는 호텔에 공개 문서가 없으면 빈 객체를 반환한다.
- `JsonConfiguration`: CMS가 사용하는 Jackson ObjectMapper 빈 누락으로 API가 시작되지 않던 문제를 수정했다.
- 고객 웹: 공개본을 조회하고, 실패하거나 제목이 유효하지 않으면 지역별 정적 기본값을 사용한다. 나머지 필드는 형식을 확인해 기본값으로 보완한다. 경험·오퍼는 배열 단위로 검사하며 빈 배열은 허용한다. 지점 전환 후 이전 요청의 늦은 응답은 무시한다.
- 관리자 `/dashboard/website`: 지점 선택, 초안 편집·저장, 저장된 초안 발행, 버전 목록. 히어로·도착 안내·경험·오퍼는 라벨이 있는 폼으로 편집하고 경험·오퍼 카드를 추가·삭제·위아래 이동할 수 있다. 고객 화면 구성을 확인하는 미리보기와 확장 필드를 위한 고급 JSON 편집도 유지한다. 잘못된 `SDTPL_ADM/SDTPL_ADM` 경로의 페이지 파일을 실제 `SDTPL_ADM/src/app` 라우트로 옮겼다.
- 관리자 편집 상태: 화면에 저장되지 않은 변경을 표시하고, 변경 중에는 발행을 비활성화한다. 저장 성공 후에만 발행할 수 있으며, 미저장 상태에서 지점을 바꾸면 변경을 버릴지 확인하는 대화상자를 표시한다. JSON 문법 오류와 API 오류도 구분한다.
- 관리자 글꼴: 빌드 시 Google Fonts 다운로드가 필요한 `next/font/google`을 제거하고 한글을 포함한 로컬 시스템 글꼴 스택으로 교체했다.
- 동시 편집 보호: 관리자 저장 요청은 `expectedDraftVersion`, 발행 요청은 `expectedDraftVersion`과 `expectedPublishedVersion`을 보낸다. 서버는 버전 조건을 포함한 단일 UPDATE로 처리하며 다른 편집·발행이 먼저 반영된 경우 `WEB_CONTENT_VERSION_CONFLICT` 409를 반환한다.
- 서버 콘텐츠 검증: 히어로 이미지·대체 텍스트·영문 표기·제목·설명, 도착 안내의 주소·체크인/아웃·안내 문구, 경험·오퍼 카드의 필수 필드를 검사한다. 경험·오퍼 배열은 비어 있을 수 있다. 선택 `seo` 객체가 있으면 제목과 설명을 모두 요구하고 각각 60자·160자를 넘지 못하게 한다.
- 검색 결과 편집: 본사 폼에서 선택 `seo.title`·`seo.description`을 입력하고 검색 결과 미리보기를 확인한다. 고객 웹은 발행본 SEO가 있으면 브라우저 `title`과 `meta[name=description]`에 반영하며, 이전 문서에는 지점별 기본값을 사용한다. 이 Vite SPA 단계는 브라우저 메타데이터만 갱신하며 canonical·OG·robots·크롤러 렌더링은 포함하지 않는다.
- `V9__website_pages.sql`: `website_page`, `website_page_version`, `website_page_audit`를 추가했다. 페이지는 초안·발행 슬러그·경로·메뉴 설정과 랜딩 콘텐츠를 한 행에서 함께 버전 관리한다. 기존 `hotel_web_content`의 3개 랜딩 콘텐츠·발행 이력·감사 로그를 보존 이관하며, 개발 프로필의 `R__website_page_data.sql`은 새 개발 DB에도 3개 랜딩을 생성한다.
- 페이지 주소와 메뉴: 지점 랜딩은 `/stays/sokcho`, `/stays/seoraksan`, `/stays/jeju`를 사용한다. 공개 `GET /api/website/navigation`은 공개 메뉴 항목만, `GET /api/website/pages/resolve?path=...`는 발행 콘텐츠와 신뢰 가능한 `hotelId`만 반환한다. 기존 호텔 콘텐츠 endpoint와 본사 CMS endpoint는 새 페이지 레코드를 사용하는 호환 facade로 전환했다.
- 관리자 페이지 트리: `/dashboard/website`의 왼쪽 패널은 사이트 → 숙소 → 지점 랜딩 트리를 실제 본사 API에서 읽는다. 중앙 폼은 슬러그·메뉴 이름·노출·순서와 초안/발행 주소를 표시하며, 이 메타데이터를 기존 콘텐츠와 같은 저장 요청에 포함한다.
- 고객 경로: 고객 웹은 `/stays/:slug`을 공개 resolve API로 해석하고 반환된 `hotelId`로 예약 바를 연결한다. 공개 메뉴의 지점 링크로도 전환할 수 있다. URL에는 가격·재고·예약 토큰·개인 정보를 넣지 않는다.
- `V10__content_pages.sql`: `website_page` 유형을 `SECTION`, `HOTEL_LANDING`, `CONTENT_PAGE`로 확장하고, 유형별 `hotel_id`·`parent_id` 소유 제약과 `CREATED` 감사 action을 추가했다. `/brand` `SECTION`은 초기화하지만 일반 페이지는 자동 생성하지 않는다.
- 일반 페이지 계약: `CONTENT_PAGE`는 `SECTION`의 직계 자식이고 `hotel_id`는 항상 `NULL`이다. 문서는 `seo`와 `blocks`만 가지며, 첫 블록이자 유일한 `HERO` 1개와 `TEXT`, `CTA`만 허용한다. 서버는 알 수 없는 필드, 빈 값, 필드 길이 초과, 원격·상위 이동 이미지 경로, 외부 CTA를 거부한다. CTA는 `/`, `/#fragment`, 또는 안전한 로컬 경로와 fragment만 사용할 수 있다.
- page ID CMS API: 본사는 `POST /api/staff/website/pages`, `GET/PUT /api/staff/website/pages/{pageId}`, `POST /api/staff/website/pages/{pageId}/publish`, `GET /api/staff/website/pages/{pageId}/versions`로 생성·초안 조회·저장·발행·버전을 처리한다. 생성·저장·발행은 각각 감사 로그를 남기며, 저장·발행의 양수 버전 조건과 SQL 조건부 갱신으로 동시 편집 충돌을 막는다. 지점 직원은 서버에서 403을 받는다.
- 관리자 일반 페이지 편집: 제공 테마의 트리는 `SECTION`과 직계 leaf를 선택하도록 확장됐다. `+ 페이지` 대화상자는 상위·메뉴·슬러그·노출·순서를 받고, 구조화 편집기는 HERO/TEXT/CTA와 SEO만 수정한다. JSON 원문이나 임의 블록 유형은 노출하지 않으며, 미저장 변경 중 페이지 이동은 확인 대화상자로 보호한다.
- 고객 일반 경로: 공개 resolve 응답의 `hotelId`는 nullable이며, 고객 웹은 `/brand/story` 같은 최대 두 단계의 CMS 경로를 정규화한다. `CONTENT_PAGE`는 예약 바 없이 별도 allowlist 렌더러로 표시한다. 파서는 서버와 독립적으로 안전한 이미지·CTA·블록 구조를 다시 검사하고 HTML 삽입을 사용하지 않는다.
- `V11__home_page.sql`: 루트 `/`만 사용하는 단일 `HOME_PAGE`를 추가했다. 홈의 `hotel_id`·`parent_id`는 `NULL`, `slug`는 `home`, 메뉴 노출은 `false`로 고정하고 partial unique index로 단일 행을 보장한다. 기본 발행 문서, 버전 1 스냅샷, `MIGRATED` 감사 기록도 만든다.
- 홈 전용 CMS API: `GET`·`PUT /api/staff/website/home`, `POST /api/staff/website/home/publish`, `GET /api/staff/website/home/versions`는 `HQ_ADMIN`만 사용한다. 저장 요청은 콘텐츠와 `expectedDraftVersion`만 받으며, 서버가 고정된 홈 메타데이터를 적용한다. 기존 `POST /api/staff/website/pages/{pageId}/publish`는 `HOME_PAGE` 발행을 거부한다.
- 홈 관리자 편집: 제공 테마의 페이지 트리는 `SECTION`보다 앞에 고정 `홈` leaf를 표시한다. 홈은 일반 페이지 생성의 부모가 될 수 없고, 편집기에서는 slug·경로·메뉴·노출·순서 같은 페이지 정보 필드를 숨긴다. SEO와 `HERO`·`TEXT`·`CTA` 콘텐츠만 홈 전용 저장·발행 API로 처리한다.
- 홈 고객 렌더링: 고객 `/`는 유효한 발행 `HOME_PAGE`만 적용한다. 화면 순서는 `CMS HERO → 코드 소유 예약 검색·AI → CMS TEXT/CTA → 기존 지점 경험·오퍼·객실·도착 안내`이며, 문서 조회 실패나 검증 실패 시 기존 정적 홈을 유지한다. 가격·재고·예약 확정은 계속 Spring 예약 API와 기존 코드가 소유한다.
- `V12__website_media_catalog.sql`: `website_media_asset`와 `website_media_usage`를 추가했다. 내장 속초 해안 PNG는 안정 UUID `14000000-0000-0000-0000-000000000001`로 등록하고, 현재 홈·일반 페이지 HERO와 지점 랜딩 문서를 자산 ID와 전달 경로로 이관했다. 기존 `website_page_version` 스냅샷은 변경하지 않는다.
- 미디어 서버 계약: `HQ_ADMIN`만 `GET/POST /api/staff/website/media`, `GET /api/staff/website/media/{mediaId}/usages`를 호출한다. PNG/JPEG만 실제 ImageIO 형식·디코드·최대 10 MiB·24,000,000 픽셀로 확인한 뒤 UUID 키로 `/app/media`에 저장한다. 업로드는 `/api/website/media/{id}/content` 공개 URL로만 전달하며, 내장 파일은 기존 `/images/...` URL을 유지한다.
- 자산 참조와 사용 위치: 저장·생성·발행 전 활성 자산 UUID를 확인하고 서버가 전달 경로를 다시 채운다. 낙관적 버전 UPDATE가 성공한 뒤 같은 트랜잭션에서 초안 또는 발행 사용 위치를 교체한다. 원격 URL·data URL·상위 경로·다른 자산의 URL은 고객 파서와 서버 양쪽에서 거부한다.
- 관리자 미디어 UX: 홈·일반 페이지·지점 랜딩의 자유 이미지 경로 입력을 공통 `MediaField`로 교체했다. 16:9 미리보기, 자산명·규격·사용 수, 페이지별 대체 텍스트, PNG/JPEG 업로드, 자산 카드, 사용 위치와 명시적 `선택` 버튼을 제공한다. 기본 alt는 선택 때만 현재 페이지 alt에 복사되며 이후 문서별로 수정할 수 있다.
- `V13__website_media_archive.sql`: `website_media_asset.status` 제약을 `ACTIVE`와 `ARCHIVED`로 확장했다. 자산명·기본 alt·상태 변경은 자산별 `version` 조건을 사용하며, 오래된 저장·상태 전환은 안정 코드의 409로 거부한다.
- 미디어 수명주기 API: 본사만 `GET /api/staff/website/media?includeArchived=true`, `PATCH /api/staff/website/media/{mediaId}`, `POST /archive`, `POST /restore`를 사용한다. 보관은 초안·발행 사용 위치가 0일 때만 가능하며, 페이지 저장의 활성 자산 공유 잠금과 보관/복원의 배타 잠금으로 새 참조와 보관이 교차하지 않게 했다.
- 보관 효과와 한계: 보관된 업로드 자산은 공개 전달과 새 문서 참조가 404/거부되지만 파일은 삭제하지 않는다. 새 공개 업로드 응답은 `Cache-Control: public, max-age=0, must-revalidate`로 제공해 캐시 재사용 전 활성 상태를 다시 확인한다. 이 정책 변경 전 이미 저장된 장기 불변 캐시는 서버에서 되돌릴 수 없으므로 해당 클라이언트 캐시 만료 전까지는 남을 수 있다. 내장 `/images/...` 정적 파일은 고객 정적 배포 경로이므로 보관만으로 숨기지 않으며, 새 CMS 문서 참조는 막는다.
- 관리자 미디어 관리 UX: 선택기는 활성 자산만 선택 카드로 보이고, 선택 자산의 이름·기본 alt·현재 버전·사용 위치 수를 편집한다. 사용 중인 자산은 사유와 함께 보관이 비활성화되고, 참조 없는 자산은 확인 대화상자 후 보관하며 별도 보관 목록에서 복원한다. 자산 기본 alt 저장은 기존 페이지별 alt를 바꾸지 않는다.
- V13 관리자 경합 보완: 대화상자를 닫으면 저장하지 않은 자산명·기본 alt 입력을 버리고 다시 열린 카탈로그 값으로 채운다. 현재 페이지가 아직 저장하지 않은 HERO 자산도 보관 대상에서 제외한다. 상태 전환 뒤에는 최신 카탈로그·사용 위치를 다시 읽으며, 409 충돌은 최신 데이터를 다시 표시한다. 보관 업로드 자산은 브라우저가 차단된 공개 URL을 미리 불러오지 않도록 설명 placeholder를 표시한다.
- `V14__website_page_lifecycle.sql`: `CONTENT_PAGE`에 `ACTIVE`/`ARCHIVED`, 수명주기 버전과 보관 시각·행위자를 추가하고 `ARCHIVED`·`RESTORED` 감사 action을 허용한다. 보관된 페이지의 URL은 예약된 채로 남고 초안·발행 이력은 삭제하지 않는다.
- 일반 페이지 수명주기 API: 본사만 `POST /api/staff/website/pages/{pageId}/archive`, `POST /restore`를 수명주기·초안·발행 버전과 함께 호출한다. 보관은 현재 공개 본문·메뉴와 `PUBLISHED` 미디어 usage만 비워 고객 resolve/navigation에서 즉시 제외한다. 복원은 `ACTIVE` 초안으로만 전환하며 공개는 다시 발행해야 한다. 보관된 페이지의 저장·발행은 409으로 막고 `HOME_PAGE`·지점 랜딩·SECTION은 보관 대상이 아니다.
- 관리자 페이지 수명주기 UX: 일반 페이지의 명시적 보관 확인은 고객 웹·메뉴에서 즉시 내려간다는 점과 URL·이력 보존을 안내한다. 보관 상태에서는 폼·미디어 선택·저장·발행을 비활성화하고 트리에 `보관`을 표시한다. `초안으로 복원` 뒤에는 편집만 다시 가능하며 재발행해야 공개된다. 미저장 변경이 있으면 보관 버튼을 비활성화한다.

## 확인한 검증
| 범위 | 결과와 한계 |
| --- | --- |
| Spring 컴파일 | 통과 |
| PostgreSQL 서비스 통합 테스트 | `WebContentIntegrationTest` 4개 통과. 초안 저장 시 공개본 유지, 발행 시 공개본 변경, 전체 랜딩 필수 필드와 SEO 빈 제목·160자 초과 설명 거부, 오래된 저장·발행 버전 충돌을 확인 |
| 개발 DB | API 컨테이너 재빌드 후 V8 및 초기 콘텐츠 마이그레이션 적용 로그 확인 |
| 공개 API | 4080 포트에서 3개 지점 제목과 경험 카드 3개 조회 확인 |
| 고객 웹 | `pnpm build` 통과. Playwright 브라우저에서 속초 발행본의 기본 SEO 제목과 설명이 `document.title`, `meta[name=description]`에 반영됨을 확인. 정적 기본값과 초기 발행본이 같으므로 이것만으로 본사 UI 발행 흐름을 입증하지는 않음 |
| 관리자 | `/dashboard/website`의 Playwright 단일 회귀 테스트 1개 통과: 히어로 제목·SEO 편집값의 저장 요청 반영, 경험 카드 순서 변경, 미리보기 이미지 경로, 편집 후 발행 비활성화와 저장 후 활성화 확인 |
| 실제 CMS HTTP 권한 | 검증 전용 호텔·계정으로 지점 조회·저장·발행 각각 403, 본사 조회·저장·발행 각각 200 확인 |
| 실제 발행·공개 조회 | 본사 저장·발행 후 공개 API에서 `CMS HTTP VERIFIED` 확인, 버전 1건과 `DRAFT_SAVED`·`PUBLISHED` 감사 로그 각 1건 확인. 검증 데이터는 모두 삭제 |
| 실제 버전 충돌 | 새 API 이미지에서 최신 저장 200·동일 버전 재저장 409, 최신 발행 200·동일 버전 재발행 409, 공개 제목 반영 확인. 검증 데이터는 모두 삭제 |
| 실제 SEO HTTP 흐름 | API 컨테이너 재빌드 후 검증 전용 본사 계정으로 빈 SEO 제목 저장 400, 유효 초안 저장·발행, 공개 API의 SEO 제목 반영을 확인. 검증 호텔·계정·세션은 모두 삭제 |
| 페이지 데이터 이관·공개 API | `WebsitePageIntegrationTest` 4건 통과. 발행 전 새 경로 비노출, 발행 시 콘텐츠·경로·메뉴 동시 노출, 이관 시 초안과 발행본이 다른 기존 페이지의 공개 발행본 유지, 본사 페이지 트리 조회, 예약어·중복 슬러그 거부를 확인 |
| 페이지 트리 관리자 | Playwright 1건 통과. 사이트 트리, 슬러그·메뉴 이름·순서 편집과 해당 `page` 저장 요청 포함, 기존 콘텐츠·SEO·순서 변경·발행 방지 UX를 확인 |
| 실제 페이지 API·권한 | API 4080 readiness `UP`, 공개 메뉴 1개 그룹·3개 지점, `/stays/sokcho` resolve, 본사 페이지 트리 1개 그룹·3개 랜딩, 속초 지점 직원의 페이지 트리 403을 확인 |
| 고객 직접 경로 | 별도 브라우저 탭에서 `http://127.0.0.1:4000/stays/sokcho`의 발행 히어로·예약 바·공개 지점 메뉴·페이지 제목을 확인 |
| Archify | CMS 구조도 showcase 9개 항목 통과. 서비스 기능 검증과는 별개 |
| V10 서버 통합 테스트 | `WebContentIntegrationTest,WebsitePageIntegrationTest` 12개 통과, 실패·오류 0. 일반 페이지 생성·발행·비공개 분리, `hotelId = null`, 부모·경로·원격 링크/이미지 거부, 양수 버전, 본사/지점 권한, 기존 랜딩 호환을 확인 |
| V10 관리자 코드 검증 | `pnpm.cmd exec tsc --noEmit` 통과. `website-content-editor.spec.ts` Chromium Playwright 3개 통과: 기존 랜딩, 미저장 이동 보호, 일반 페이지 생성·저장·발행 mock 흐름을 확인 |
| V10 고객 코드 검증 | `customer-route.test.ts`, `content-page.test.ts` 통과 및 `pnpm.cmd build` 통과. 최대 두 단계 경로, 인코딩된 구분자·잘못된 블록·안전하지 않은 이미지/CTA 거부와 프로덕션 빌드를 확인 |
| V10 실제 통합 | 로컬 API `4080` readiness `UP` 후 본사 세션으로 브랜드 SECTION 아래 `/brand/story` `CONTENT_PAGE`를 생성·발행했다(초안 v1, 발행 v2). 공개 navigation 노출과 resolve의 `type: CONTENT_PAGE`, `hotelId: null`을 확인했다. `4000` 직접 경로에서 문서 제목·메뉴 브랜드 이야기·HERO/TEXT/CTA·로컬 CTA 링크의 접근성 트리 렌더링을, 실제 로그인한 `4001` CMS 새로고침에서 브랜드 SECTION > 브랜드 이야기 트리 항목을 확인했다. |
| V11 서버 통합 테스트 | `WebContentIntegrationTest,WebsitePageIntegrationTest` 13건 통과, 실패·오류 0. 홈의 공개/초안 분리, 본사 전용 저장·발행·버전, 지점 직원 거부, 루트 resolve, navigation 제외, 일반 page API의 HOME_PAGE 우회 발행 차단을 확인 |
| V11 관리자 코드 검증 | `pnpm.cmd exec tsc --noEmit` 통과. `website-content-editor.spec.ts` Chromium Playwright 4건 통과: 고정 홈 선택, 페이지 정보 숨김, 홈 전용 save/publish 요청과 미저장 발행 방지를 확인 |
| V11 고객 코드 검증 | 콘텐츠 파서 검사와 `pnpm.cmd build` 통과. HOME_PAGE 타입, 유효한 루트 문서만 적용하는 fallback, 히어로 뒤 콘텐츠 렌더링을 확인 |
| V11 실제 API·브라우저 통합 | API 컨테이너 재빌드에서 Flyway V11 적용을 확인했다. 개발 API에서 본사 세션으로 기존 홈 초안을 내용 변경 없이 저장·발행해 초안·발행 버전을 모두 2로 만들었다. 공개 `/` resolve는 `type: HOME_PAGE`, `hotelId: null`을 반환했고 navigation에는 루트 항목이 없었다. 실제 in-app browser에서 고객 `4000`의 CMS 히어로·예약 검색/AI·본문/CTA 조합과 관리자 `4001` CMS 트리의 고정 홈 항목을 확인했다. |
| V12 서버 통합 테스트 | `WebsiteMediaIntegrationTest`, `WebContentIntegrationTest`, `WebsitePageIntegrationTest` 17건 통과. 본사/지점 권한, 실제 PNG/JPEG 형식 판별, 잘못된 형식·10 MiB 초과·24 MP 초과 거부, 공개 전달, 내장 자산 사용 위치, 새 UUID·위조 경로 계약을 확인했다. |
| V12 관리자·고객 코드 검증 | `pnpm.cmd exec tsc --noEmit` 통과. `website-content-editor.spec.ts` Chromium Playwright 7건 통과: 구조화 페이지 선택, 지점 랜딩 업로드·페이지별 alt, 실패 업로드 상태, 사용 위치, 저장 JSON, 기존 저장·발행 UX를 확인했다. 고객 `content-page.test.ts`와 `pnpm.cmd build`도 통과했다. |
| V12 Docker·브라우저 통합 | API 재빌드에서 Flyway V12와 반복 migration 적용을 확인했고, `hotel` 사용자로 Compose `hotel-media-data`의 `/app/media` 쓰기와 readiness `UP`을 확인했다. 공개 홈 resolve는 내장 자산 UUID와 `/images/sokcho-coast-hero.png`를 반환했다. 실제 in-app browser의 본사 CMS 선택기에서 자산 1건, 초안·발행 사용 위치 10건, 미리보기와 페이지별 alt 입력을 확인했다. 검증용 업로드를 영구 카탈로그에 남기지 않기 위해 실제 페이지 발행은 수행하지 않았다. |
| V13 미디어 수명주기 | `WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest` 20건 통과. 자산 메타데이터·버전 충돌·본사/지점 권한, 참조 자산 보관 거부, 참조 없는 업로드 보관·복원, 보관 업로드 공개 404를 확인했다. 관리자 `tsc --noEmit`과 CMS Playwright 9건도 통과했다. API 재빌드에서 Flyway V13과 readiness `UP`을 확인했고, 실제 CMS에서 10개 사용 위치가 있는 내장 자산의 보관 제어가 사유와 함께 비활성인 것을 확인한 뒤 저장·보관 없이 닫았다. |
| V13 보완·V14 페이지 수명주기 | `WebsiteMediaIntegrationTest,WebContentIntegrationTest,WebsitePageIntegrationTest` 22건 통과. 업로드 공개 응답의 재검증 캐시 정책, 대화상자 임시 메타데이터 폐기, 현재 미저장 페이지 자산의 보관 방지, 보관 업로드 placeholder, 409 최신 카탈로그 재조회와 일반 페이지 보관·복원·명시적 재발행을 확인했다. `pnpm.cmd exec tsc --noEmit`과 CMS Playwright 15건도 통과했다. Docker API 재빌드에서 Flyway V14와 readiness `UP`을 확인했고, 활성 `/brand/story`의 공개 resolve·navigation을 읽기 전용으로 확인했다. 사용자 페이지를 실제로 보관하거나 저장하지 않았다. |
| V15 발행본 콘텐츠 초안 복원 | `WebsitePageIntegrationTest` 12건 통과. 이전 발행본의 콘텐츠만 현재 초안으로 가져오고 공개 콘텐츠·메뉴·경로·부모를 유지한 뒤, 명시적 재발행에서만 공개가 바뀌는지를 확인했다. source version 0·현재 발행본 version(400 `INVALID_REQUEST`)·다른 페이지에만 존재하는 version·보관 페이지·오래된 lifecycle 요청·지점 직원은 거부하며, 초안 미디어 usage와 `VERSION_RESTORED` audit을 확인했다. 관리자 TypeScript 검사와 CMS Playwright 전체 16건도 통과했고, 마지막 성공 안내 문구 조정 뒤 복원 대상 E2E 1건과 TypeScript 검사를 다시 통과했다. 미저장 초안의 비활성 사유·현재 저장 초안 대체 경고를 포함한 확인 대화상자·세 버전 요청·복원 뒤 초안 갱신을 확인했다. Docker API 재빌드에서 Flyway V15·readiness `UP`, 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했다. 실제 사용자 페이지의 저장·복원·발행은 실행하지 않았다. |

2026-09-11: V15로 활성 `CONTENT_PAGE`의 이전 `website_page_version`을 현재 초안에 복원하는 흐름을 추가했다. 서버는 source page·구조·활성 미디어·현재 콘텐츠 계약과 lifecycle/draft/published 버전, 현재 `publishedVersion`보다 작은 source version을 한 트랜잭션에서 확인하고, `draft_content`와 `draft_version`만 바꾼 뒤 `VERSION_RESTORED` audit과 `DRAFT` 미디어 usage를 기록한다. 관리자 이력 카드에서는 이전 발행본만 선택할 수 있고, 미저장 편집 중에는 이유를 보여 주며 비활성화한다. 복원은 고객 공개를 바꾸지 않고 재발행 뒤에만 반영된다. 실제 개발 콘텐츠에는 복원 요청을 보내지 않았다.

## V16 발행본 비교

2026-09-11: `CONTENT_PAGE`의 두 발행본을 비교하는 본사 전용 GET `/api/staff/website/pages/{pageId}/versions/compare?baseVersion=&compareVersion=`를 추가했다. 서버는 같은 페이지의 양수·오름차순 version과 snapshot의 page type·parent·metadata·구조화 콘텐츠를 확인하고 immutable snapshot만 반환한다. `ACTIVE`와 `ARCHIVED` 모두 허용하며 활성 미디어 검사, 이미지 요청, 초안·공개본·수명주기·audit·media usage 변경은 하지 않는다. 관리자는 발행 이력의 이전 버전에서 비교 dialog를 열고, 기준·비교 version을 바꾸어 경로·메뉴·SEO·HERO·TEXT·CTA를 나란히 검토한다. 상태는 색뿐 아니라 `동일`·`변경`·`추가`·`제거` 문구로 표시하며 `닫기`와 Escape 뒤 시작 버튼으로 포커스를 복귀한다.

검증: `WebsitePageIntegrationTest` 13건 통과. 비교 결과의 발행 시각·metadata·content, 현재 상태와 audit/media usage 불변성, 보관 페이지 비교, 지점 권한, 잘못된·다른 페이지 version, 손상 snapshot 거부를 확인했다. 관리자 `pnpm.cmd exec tsc --noEmit`와 comparison 대상 Chromium Playwright 1건을 통과했고, UI에서 version 변경 GET, 닫기·Escape·포커스 복귀와 POST/PUT 부재를 확인했다. Docker API 재빌드가 성공했고 health `UP` 및 기존 `/brand/story` 공개 resolve를 읽기 전용으로 확인했다. 실제 사용자 페이지에는 저장·발행·복원·비교 요청을 보내지 않았다.

## V17 일반 콘텐츠 페이지 초안 미리보기

2026-09-11: `CONTENT_PAGE` 편집기 상단에 `미리보기`를 추가했다. 관리자 dialog는 서버의 저장본이 아니라 HERO·TEXT·CTA와 페이지 메타데이터의 현재 입력값을 고객 화면에 가까운 구성으로 읽어 표시한다. HERO 이미지는 고객 파서와 같은 `/images/...`, `/api/website/media/{uuid}/content` 안전 전달 형식과 UUID 일치를 확인한 경우에만 고객 웹 origin에서 표시하고, 그 밖의 값은 네트워크 요청 없이 자리표시자로 바꾼다. CTA는 미리보기 안에서 화면 이동을 하지 않는다. `닫기`와 Escape 모두 시작 버튼에 포커스를 되돌린다.

검증: 관리자 `pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "previews unsaved content page edits"` 1건과 `pnpm.cmd exec tsc --noEmit`를 통과했다. E2E는 저장 전 HERO·TEXT 입력이 표시되는지, 고객 웹 origin의 내장 이미지 경로, 닫기·Escape 포커스 복귀와 staff website PUT/POST 부재를 확인한다. 관리자 전용 읽기 UI 변경이므로 API 재빌드와 실제 사용자 페이지의 저장·발행은 수행하지 않았다.

## V18 일반 콘텐츠 페이지 영구 삭제

2026-09-11: 보관된 `CONTENT_PAGE`만 영구 삭제하는 본사 전용 `DELETE /api/staff/website/pages/{pageId}`를 추가했다. 요청은 lifecycle·draft·published version을 모두 요구한다. 서버는 행 잠금 뒤 보관 상태·version·자식 페이지 부재를 확인하고 `website_media_usage`, `website_page_version`, `website_page_audit`, `website_page` 순으로 같은 트랜잭션에서 삭제한다. 자산 파일과 `website_media_asset`는 유지한다. 활성 페이지·오래된 version·하위 페이지는 409 `WEBSITE_PAGE_DELETE_CONFLICT`, 일반 페이지가 아닌 대상·없는 page는 404로 거부한다.

관리자는 보관된 일반 페이지에서만 `영구 삭제`를 볼 수 있다. 확인 dialog는 메뉴명과 발행 이력·감사 이력·미디어 사용 위치가 함께 사라지고 복구할 수 없다는 점을 알린다. 성공하면 고정 홈으로 이동하고 갱신된 페이지 트리를 읽는다.

검증: `WebsitePageIntegrationTest` 14건 통과. 보관된 페이지의 parent/version/audit/media usage 정리, 자산 유지, 활성 page·오래된 lifecycle request·SECTION·지점 권한 거부를 확인했다. 관리자 `pnpm.cmd exec playwright test e2e/website-content-editor.spec.ts --project=chromium --grep "permanently deletes"` 1건과 `pnpm.cmd exec tsc --noEmit`를 통과했다. Docker API 재빌드와 4080 health `UP`, 기존 `/brand/story` 공개 resolve를 확인했고 유효하지 않은 세션의 DELETE가 401 `STAFF_AUTHENTICATION_REQUIRED`로 거부되는 것을 읽기 전용으로 확인했다. 실제 사용자 페이지에는 보관·삭제 요청을 보내지 않았다.

## 남은 작업과 완료 기준
1. 자산 파일 교체는 기존 자산을 덮어쓰지 않고 새 자산을 만든 뒤 사용 위치를 명시적으로 이동하는 방식으로 구현한다.
2. 한국어·영어 콘텐츠, 번역 승인 흐름, 인증된 초안 미리보기, canonical·OG·robots를 구현한다.
3. 예약된 발행(스케줄 발행), 블록 이동 감지, 정적 배포 history fallback을 후속 단계로 구현한다.

## 문서 검토
2026-09-10: 분산된 후속 메모를 위 상태로 통합했다. 초기 이관·공개 조회를 미구현으로 표기하던 내용을 수정하고, 확인한 검증과 미검증을 분리했다. 이번 문서 정리에서는 앱 테스트를 재실행하지 않았다.

2026-09-11: 관리자 빌드 차단 원인을 외부 글꼴 다운로드로 확인해 로컬 글꼴로 교체했다. 미저장 편집 보호 UX와 단일 회귀 테스트를 추가하고, 실제 4080 API에서 본사·지점 권한, 저장·발행·공개 조회, 버전·감사 로그를 검증했다.

2026-09-11: 저장·발행 요청에 낙관적 버전 조건을 추가했다. 서비스 통합 테스트와 실제 4080 HTTP 요청으로 오래된 저장·발행이 409로 거부되는 것을 확인했다.

2026-09-11: 히어로·도착 안내·경험·오퍼를 직접 수정하는 폼과 카드 추가·삭제를 구현했다. 고급 JSON 편집을 함께 유지하고 폼 변경값의 저장 요청 반영을 E2E로 확인했다.

2026-09-11: 경험·오퍼 카드의 위아래 이동과 고객 화면 형태의 미리보기를 추가했다. 전체 랜딩 스키마 검증과 선택 SEO 검증을 서버에 적용하고, 관리자 SEO 폼·검색 결과 미리보기·고객 브라우저 메타 반영을 연결했다. Spring 통합 테스트 4건, 관리자 Playwright 1건, 고객 웹 빌드, 실제 API 저장·발행·공개 조회 및 브라우저 메타를 확인했다.

2026-09-11: `website_page`를 지점 랜딩의 페이지·경로·메뉴·콘텐츠 발행 기준으로 추가했다. 기존 콘텐츠 테이블은 이관 원본과 호환 endpoint로 보존한다. 3개 지점은 `/stays/sokcho`, `/stays/seoraksan`, `/stays/jeju`로 공개되며, 관리자 CMS의 페이지 트리와 슬러그·메뉴 설정, 고객 웹 직접 경로·공개 메뉴를 연결했다. 서버 통합 테스트 8건, 관리자 Playwright 1건, 고객 웹 빌드와 실제 API·브라우저 확인을 수행했다. 실제 로그인 UI에서 변경값을 저장·발행하는 검증은 사용자 콘텐츠를 임의로 바꾸지 않기 위해 남겨 두었다.

2026-09-11: V10으로 `/brand` `SECTION` 직계의 `CONTENT_PAGE`를 추가했다. 페이지는 SEO와 HERO/TEXT/CTA allowlist 문서만 저장하며, 로컬 이미지·CTA 경로, `HQ_ADMIN` 권한, 초안/발행 버전과 감사 로그를 서버에서 검증한다. 관리자 구조화 편집기와 고객 공개 경로·렌더러까지 연결했고, 서버 통합 테스트 12건, 관리자 타입 검사·Playwright 3건, 고객 경로/파서 검사와 빌드는 통과했다. 로컬 API `4080`에서 본사 세션으로 `/brand/story`를 생성·발행(초안 v1, 발행 v2)한 뒤 공개 navigation·resolve, 고객 `4000` 직접 경로의 접근성 트리, 실제 관리자 `4001` CMS 트리를 확인했다.

2026-09-11: V11으로 루트 `/`의 단일 `HOME_PAGE`를 추가했다. 홈은 고정 메타데이터와 본사 전용 API를 사용하고 일반 page 발행 API에서는 발행할 수 없다. 관리자에는 고정 홈 항목과 콘텐츠 전용 편집기를, 고객에는 CMS 히어로와 예약/AI 흐름을 분리한 조합 렌더링을 연결했다. 대상 서버 통합 테스트 13건, 관리자 타입 검사·Playwright 4건, 고객 콘텐츠 검사·빌드, Docker Flyway V11, 실제 본사 저장·발행·공개 resolve·navigation 제외와 고객/관리자 in-app browser 확인을 완료했다.

2026-09-11: V12로 미디어 카탈로그와 안전한 이미지 업로드를 추가했다. 문서는 자산 UUID와 서버 정규화 전달 URL을 함께 보관하고, 초안·발행 사용 위치를 별도로 추적한다. API 재빌드에서 Flyway V12·media volume 권한을 확인했고, 서버 테스트 17건, 관리자 타입 검사·Playwright 7건, 고객 파서·빌드를 통과했다. 실제 본사 관리자 선택기에서 내장 자산·10개 사용 위치·미리보기·alt 입력을 확인했다. 자산 삭제·보관 기능이 아직 없어 검증용 업로드를 공개 페이지에 저장하거나 발행하지는 않았다.

2026-09-11: V13으로 자산 이름·기본 alt 편집과 안전한 보관·복원을 추가했다. 보관은 초안·발행 사용 위치가 없는 자산만 허용하며, `FOR KEY SHARE`와 `FOR UPDATE`로 페이지 저장과 보관의 경합을 막는다. 대상 서버 테스트 20건, 관리자 타입 검사와 CMS Playwright 9건, Docker Flyway V13·readiness를 확인했다. 실제 본사 CMS에서는 사용 중인 내장 자산이 보관 불가 안내와 함께 비활성인 것만 확인했고, 사용자 자산을 저장·보관·삭제하지 않았다.

2026-09-11: V13 보완으로 캐시된 공개 업로드가 보관 뒤에도 장기간 재사용되는 문제를 `max-age=0, must-revalidate`로 줄였고, 선택기 닫기·미저장 페이지 자산·보관 preview·409 최신 데이터 재조회 경합을 E2E로 고정했다. V14는 `CONTENT_PAGE`만 `ACTIVE`/`ARCHIVED` 수명주기로 전환한다. 보관은 공개본과 공개 usage를 비우되 초안·이력·URL을 남기며, 복원은 공개를 자동으로 되살리지 않는다. 서버 22건과 CMS 15건, Docker Flyway V14·readiness를 확인했다. 실제 개발 콘텐츠는 보관하지 않았다.

## V19 미디어 자산 영구 삭제

2026-09-12: 참조 없는 `ARCHIVED UPLOADED` 자산만 보관 30일 뒤 영구 삭제하는 본사 전용 DELETE API를 추가했다. V19는 보관 시각을 기록하고 기존 보관 자산을 `updated_at`으로 보정한다. 파일을 같은 볼륨의 격리 경로로 이동한 뒤 DB 삭제를 수행하며, 롤백 시 원복하고 커밋 시 제거한다. 관리자 선택기는 삭제 가능일과 명시적 복구 불가 확인을 제공한다. 상세 검증은 [변경 기록](2026-09-12-media-permanent-delete.md)을 따른다.
