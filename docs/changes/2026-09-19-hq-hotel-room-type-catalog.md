# 본사 지점·객실 유형 카탈로그 읽기 전용 조회

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-19.

## 변경 이유

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`가 다음 대상이었다. `hotel`·`room_type`·`rate_plan`·`rate_day` 테이블은 있으나 본사가 이를 보는 API와 화면이 없었다. 검색 API(`AvailabilityService`)만 `rate_day`를 읽었다.
- 1번 메뉴의 전체 범위(지점 생성·수정·판매 중지, 요금제 등록·일자별 요금 변경) 중 읽기 전용 카탈로그 조회만 먼저 연결했다. 쓰기 동작은 가격·재고·예약 확정에 직접 닿으므로 별도 검증이 필요하다.

## 구현 범위

### 서버 (services/api)

- `hotel/HotelCatalogQueryService`: SELECT만 사용한다. `room_type` LEFT JOIN `rate_plan` LEFT JOIN `rate_day`를 묶어 객실 유형별로 요금제와 일자별 요금의 `priced_days`·`min_amount_krw`·`max_amount_krw`·`avg_amount_krw`를 낸다. 요금이 없는 요금제는 LEFT JOIN을 유지해 통계가 null로 내려간다.
- `hotel/HotelCatalogController`: `GET /api/staff/hotels/{hotelId}/room-types`. `StaffAccessService.requireHotel`로 권한을 확인한 뒤 지점 존재 여부를 확인한다. 권한 실패가 403, 지점 부재가 404, 세션 부재가 401이 되도록 순서를 정했다.
- `hotel/HotelNotFoundException`·`web/ApiExceptionHandler`의 `HOTEL_NOT_FOUND` 404 매핑.
- `limit`은 1~100로 고정하고 `offset`을 받아 페이지를 넘긴다. 화면이 임의의 크기를 보내지 못한다.
- `totalCount`는 `limit`·`offset`과 무관하게 전체 객실 유형 수를 센다.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`의 `getRoomTypeCatalog`·`RoomTypeCatalogView`·`RoomTypeCatalogEntry`·`RoomTypeRatePlanSummary`.
- `components/hotel-admin/hotel-catalog.tsx`: 지점 선택기와 객실 유형 표. 각 행에 최대 인원, 요금제 목록, 조식 포함 여부, 객실 유형 전체의 요금 범위를 보여준다. `HQ_ADMIN`만 지점 선택·새로고침이 가능하고 다른 역할은 본사 전용 안내를 본다. 카탈로그를 불러오지 못하면 안내문을 보여준다.
- `app/(dashboard)/dashboard/hotels/page.tsx`.
- `nav.ts`의 `catalogGroup`이 `HQ_ADMIN`에만 노출된다.

### 테스트

- `HotelCatalogQueryIntegrationTest` 7종: 본사 200(요금제·요금 범위), 지점 타 지점 403, 세션 부재 401, 없는 지점 404, `limit` 클램프·`offset` 페이지, 요금 없는 객실 유형, 지점 본인 지점 조회.
- `SDTPL_ADM/e2e/hotel-catalog.spec.ts` 5종: 메뉴 노출 권한, 객실 유형·요금제·요금 범위 표시, 지점 직원 안내, 지점 전환·재조회, 장애 안내.

## 자동 검증

- API `mvnw -Dtest=HotelCatalogQueryIntegrationTest test` **7건** 종료 코드 0.
- API `mvnw compile`·`mvnw test-compile` 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 `ai-operations.tsx`·`media-storage-status.tsx`와 같은 패턴).
- Playwright: `e2e/hotel-catalog.spec.ts` **5건** 종료 코드 0(11.2s).
- 라이브: API 이미지 재빌드 뒤 health 200. 본사 세션으로 속초 카탈로그가 200으로 내려왔고 `totalCount=3`, 각 요금제 `pricedDays=90`, `min`·`max`·`avg` 금액이 모두 채워져 있었다. 지점 직원의 타 지점 조회 403, 없는 지점 404, 세션 없음 401. 관리자 4001의 `/dashboard/hotels` 200.

## 미검증 항목

- 지점 생성·수정·판매 중지와 객실 유형·요금제 등록·수정, 일자별 요금 변경. 쓰기 동작은 다음 작업이다.
- 재고(`inventory_day`) 조회. 2번 메뉴 `재고·가격`의 범위다.
- 요금제별 상세 일자 가격 표. 첫 버전은 객실 유형 단위 범위만 보여준다.
- 390px 모바일 뷰포트에서 메뉴·표의 동작. Playwright 시나리오는 1280px 기본 뷰포트를 썼다.
- 영문 전환. 메뉴·카탈로그 안내는 한국어 단일 언어다.
