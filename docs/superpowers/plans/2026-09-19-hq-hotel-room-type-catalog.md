# 본사 지점·객실 유형 관리 (읽기 전용 카탈로그)

> 상태: 완료. `docs/changes/2026-09-19-hq-hotel-room-type-catalog.md`에 검증 결과를 옮겼다. 최종 갱신 2026-09-19.

## 배경

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`가 다음 대상이다. `hotel`·`room_type`·`rate_plan`·`rate_day` 테이블은 있으나 본사가 이를 보는 API와 화면이 없다. 검색 API(`AvailabilityService`)만 `rate_day`를 읽는다.
- 1번 메뉴의 전체 범위는 지점 생성·수정·판매 중지와 요금제 등록·일자별 요금 변경까지 포함한다. 이 계획은 그중 **읽기 전용 카탈로그 조회**만 다루고 쓰기는 다음 작업으로 뺀다.
- 쓰기 동작은 가격·재고·예약 확정에 직접 닿는다. 핵심 원칙대로 권한은 Spring Boot에 두고, 이번에는 본사가 "현재 무엇이 팔리고 있는지"를 확인하는 수단만 먼저 연결한다.

## 완료 기준

1. `GET /api/staff/hotels/{hotelId}/room-types`가 본사 세션으로 객실 유형별로 이름·최대 인원·요금제(이름·조식 포함 여부·정책 버전)와 일자별 요금의 최소·최대·평균을 반환한다.
2. 지점 직원이 다른 지점을 조회하면 403, 잘못된 세션은 401, 없는 지점은 404.
3. `limit`은 객실 유형 1~100으로 고정하고 offset을 받아 페이지를 넘길 수 있다.
4. 관리자 4001 `/dashboard/hotels`에서 지점을 선택하면 객실 유형 표와 요금제·요금 범위가 표시된다.
5. `nav.ts`의 `호텔 및 객실` 메뉴는 `HQ_ADMIN`에만 노출된다.
6. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다.

## 구현 범위

### 서버 (services/api)

- `hotel/HotelCatalogQueryService`: SELECT만 사용한다. 객실 유형별로 요금제를 묶고 각 요금제의 `rate_day` 통계를 낸다. 쿼리는 `room_type` JOIN `rate_plan` LEFT JOIN `rate_day`를 쓴다.
- `hotel/HotelCatalogController`: `GET /api/staff/hotels/{hotelId}/room-types`. `StaffAccessService.requireHotel(token, hotelId)`로 권한을 확인한다. 없는 지점은 별도 확인 후 404.
- 요금이 아직 없는 요금제는 통계가 0·null로 내려가도록 LEFT JOIN을 유지한다.
- `limit`·`offset`을 받되 서버가 범위를 고정한다. 화면이 임의의 크기를 보내지 못한다.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`에 `getStaffRoomTypes`·`RoomTypeCatalogView` 타입을 추가한다. 기존 `getStaffReservations`와 같은 에러 패턴을 따른다.
- `components/hotel-admin/hotel-catalog.tsx`: 지점 선택기(기존 정적 `hotels` 목록 재사용) + 객실 유형 표. 각 행에 최대 인원, 요금제 목록, 요금 범위를 보여준다.
- `app/(dashboard)/dashboard/hotels/page.tsx`.
- `nav.ts`의 `operationsGroup`에 `호텔 및 객실`을 추가한다. 로드맵 1번 후보 경로 `/dashboard/hotels`를 그대로 쓴다.

### 테스트

- `HotelCatalogQueryIntegrationTest`: 본사 200(요금제·요금 범위 포함), 지점 403, 잘못된 세션 401, 없는 지점 404, `limit` 범위 밖 400.
- 요금이 없는 객실 유형이 0·null로 노출되는지 확인.
- `e2e/hotel-catalog.spec.ts`: 본사 메뉴 노출, 지점 선택 후 객실 유형 표 표시, 지점 직원에게 메뉴가 보이지 않음.

## 미구현 예정 항목

- 지점 생성·수정·판매 중지. 쓰기 동작은 다음 작업에서 다룬다.
- 객실 유형·요금제 등록·수정, 일자별 요금 변경. 가격 권한이 Spring Boot에 있으므로 별도 검증이 필요하다.
- 재고(`inventory_day`) 조회. 2번 메뉴 `재고·가격`의 범위다. 이번에는 요금만 다룬다.
- 요금제별 상세 일자 가격 표. 첫 버전은 범위(최소·최대·평균)만 보여준다.
- 영문 전환. 한국어 단일 언어다.
