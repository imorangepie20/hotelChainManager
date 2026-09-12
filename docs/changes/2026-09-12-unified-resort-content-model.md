# 통합 리조트 콘텐츠 모델 변경 기록

최종 갱신: 2026-09-12

## 완료한 서버 기반

- V16 `content_kind`와 상태별 연결 테이블을 생성·저장·발행 경로에 연결했다.
- `ROOM`은 같은 지점 room type 하나를 초안과 발행본에 분리 저장한다. `PROMOTION`의 지점·room type 범위도 같은 검증기를 사용한다.
- 저장은 DRAFT 연결만 바꾼다. 발행은 DRAFT 연결을 PUBLISHED로 복사하고 version snapshot에 `contentKind`와 `connections`를 남긴다.
- 보관은 PUBLISHED 연결만 비운다. 복원은 DRAFT 연결을 보존한다. 발행 이력 초안 복원과 영구 삭제도 연결 행을 같은 transaction에서 처리한다.
- 본사 전용 `GET /api/staff/website/content-reference`는 지점, 지점별 room type, 활성·공개 CONTENT_PAGE 후보만 반환한다. 가격·재고·판매 가능 상태는 반환하지 않는다.

## 변경 이유

고객 상세와 관리자 편집기가 같은 page identity를 쓰면서도 초안 연결을 공개 연결과 분리해야 한다. 가격·재고·예약 확정 권한은 계속 Spring 예약 도메인에 남긴다.

## 검증

다음 focused Spring 통합 테스트 6건을 통과했다.

```powershell
Set-Location services/api
& .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jowoo\.m2\repository' '-Dtest=WebsitePageIntegrationTest#returnsLegacyKindsBlockIdsAndEmptyConnectionsWithoutChangingPageContent+writesStableBlockIdsWhenAnExistingContentPageDraftIsSaved+validatesRequiredBlocksForEachTypedContentKind+rejectsRoomTypeConnectionsOutsideTheContentHotelScope+keepsDraftAndPublishedConnectionsIsolatedAcrossLifecycle+returnsOnlySafeContentReferencesForHeadquarters' test
```

legacy block ID 읽기 호환, kind별 block·연결 범위, ROOM draft/published 분리, 발행 이력 복원, 보관·복원·삭제, 본사 reference catalog 권한을 확인했다.

## 미검증과 다음 작업

- Docker API runtime에는 V16을 아직 적용하지 않았다.
- 고객 공개 상세/목록 renderer와 관리자 kind·관계 편집기/390px preview는 다음 task에서 구현한다.

## 공개 read model

- 공개 resolve는 `PUBLISHED` 상태 연결만 반환한다.
- `GET /api/website/collections?hotelSlug=&kind=`는 발행·활성 페이지에서 ID, kind, path, title, summary, image, hotel slug만 반환한다.
- 잘못된 지점, 지점 없는 지점 범위 kind, 보관된 페이지, 안전하지 않은 HERO 이미지 경로는 공개 목록에서 제외하거나 거부한다.
- `WebsitePageIntegrationTest` 전체 22건을 통과했다.

## 고객 공개 parser와 예약 intent

- 고객 웹은 콘텐츠 종류별 block allowlist와 필수 block, stable `blockId`, UUID 연결, 안전한 내부 media/map 경로를 다시 확인한다. 외부 경로, 가격 필드, 초안 전용 카드 필드는 렌더하지 않는다.
- 공개 카드 parser는 카드 read model만 허용한다. 업로드 미디어 전달 경로와 `/images` 경로의 상위 이동도 확인한다.
- `applyBookingIntent`는 지점 ID와 선택 room type ID만 검색 상태에 반영한다. 가격, 할인, 재고, rate plan, 예약 API 호출은 포함하지 않는다.
- Node strip-types 실행을 위해 `LatestAvailabilityRequest`의 TypeScript parameter property를 일반 필드 초기화로 바꿨다. 동작은 유지한다.

다음 focused 검증을 통과했다.

```powershell
Set-Location apps/web
node --experimental-strip-types src/lib/content-page.test.ts
node --experimental-strip-types src/lib/content-collection.test.ts
node --experimental-strip-types src/lib/latest-availability-request.test.ts
pnpm.cmd exec tsc -b
```

## 고객 상세·목록 renderer와 예약 재조회

- 고객 웹은 `RICH_TEXT`, `OPERATING_HOURS`, `LOCATION`, `PROMOTION_SUMMARY`, `RELATED_COLLECTION`, `BOOKING_CTA`를 구조화된 HTML로 렌더한다. 운영 시간은 table, 위치는 address, 프로모션은 실제 가격이 아닌 표시 정보 안내를 사용한다.
- `/stays/{hotel}/rooms|dining|facilities|experiences`, `/offers`, `/guides`, `/brand`는 공개 collection API를 읽고, 상세 경로보다 먼저 목록으로 구분한다. 빈 목록과 오류 상태는 별도 안내를 표시한다.
- ROOM CTA는 홈 예약 바에 focus하고, 지점·room type만 검색 상태에 적용한다. availability 요청은 기존 날짜·투숙객·객실 조건만 보내며, 응답 후 room type만 필터한다.
- 사용자의 최소 검증 지시에 따라 production build, 전체 서버 테스트, 전체 브라우저 회귀는 실행하지 않았다.

직접 검증:

```powershell
Set-Location apps/web
node --experimental-strip-types src/lib/customer-route.test.ts
node --experimental-strip-types src/lib/latest-availability-request.test.ts
pnpm.cmd exec tsc -b
```

## 관리자 유형 생성과 참조 선택

- 관리자 CMS는 세션당 `content-reference` 카탈로그를 페이지 트리와 함께 읽고, 실패하면 인라인 재시도를 제공합니다. 객실 유형·지점 ID를 클라이언트에 고정하지 않습니다.
- 새 콘텐츠 페이지 dialog는 유형과 상위 SECTION 범위를 확인합니다. `ROOM`은 선택 SECTION의 지점과 같은 room type 하나만, `PROMOTION`은 대상 지점 하나 이상을 요청에 포함합니다. 생성 요청에는 `contentKind`, `hotelId`, 빈 관련 페이지 목록을 포함한 `connections`와 유형별 안전한 초안 블록만 보냅니다.
- 가격·재고·판매 상태 필드는 생성 요청과 UI에 추가하지 않았습니다. 편집기에는 다음 작업의 연결 picker를 위해 동일 카탈로그를 전달합니다.

다음 focused 검증을 통과했습니다.

```powershell
Set-Location SDTPL_ADM
& .\node_modules\.bin\playwright.cmd test e2e/website-content-editor.spec.ts --project=chromium --grep "creates, saves, and enables publishing a structured content page|creates typed pages with scoped references"
& .\node_modules\.bin\tsc.cmd --noEmit
```

Task 8에서 유형별 block 편집, 연결 picker, 저장·발행 payload, 데스크톱/390px 미리보기를 이어서 구현합니다. API Docker runtime과 실제 사용자 CMS 데이터 변경은 수행하지 않았습니다.
