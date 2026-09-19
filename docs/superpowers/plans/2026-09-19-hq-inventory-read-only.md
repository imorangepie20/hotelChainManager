# 본사 재고·가격 읽기 전용 조회

> 상태: 완료. `docs/changes/2026-09-19-hq-inventory-read-only.md`에 검증 결과를 옮겼다. 최종 갱신: 2026-09-19.

## 배경

- `admin-menu-roadmap.md` 2번 `재고·가격`이 다음 대상이다. `inventory_day`는 예약·만료·변경 작업이 내부적으로만 변경한다. 본사가 객실 유형별 일자 재고를 직접 볼 수단이 없다.
- 1번 메뉴와 같은 순서로 **읽기 전용 조회**를 먼저 붙인다. 재고 조정·판매 중지 구간 설정·일괄 업로드는 쓰기 동작이므로 다음 작업으로 뺀다. 조회가 먼저 있어야 본사가 조정 대상을 식별할 수 있다.
- 가격은 1번 카탈로그가 이미 `rate_day` 범위를 보여준다. 이번에는 객실 유형별 **일자 재고**(capacity·held·confirmed·remaining)를 노출하고, 같은 화면에서 객실 유형을 선택하면 일자 재고와 해당 객실 유형의 요금을 함께 본다.

## 완료 기준

1. `GET /api/staff/hotels/{hotelId}/inventory`가 본사 세션으로 객실 유형별 일자 재고를 반환한다. 각 행은 `stayDate`·`capacity`·`held`·`confirmed`·`remaining`을 가진다.
2. `from`·`to`는 ISO 날짜이고, 서버가 기간을 검증한다. `to`가 `from`보다 이전이거나 92일을 초과하면 400.
3. 지점 직원이 다른 지점을 조회하면 403, 잘못된 세션은 401, 없는 지점은 404. 이 순서대로 매핑한다.
4. 관리자 4001 `/dashboard/inventory`에서 지점과 객실 유형을 선택하면 일자 재고 표와 해당 객실 유형의 요금 범위가 표시된다.
5. `nav.ts`의 `재고·가격` 메뉴는 `HQ_ADMIN`에만 노출된다.
6. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다.

## 구현 범위

### 서버 (services/api)

- `inventory/InventoryQueryService`: SELECT만 사용한다. `room_type` JOIN `inventory_day`를 쓰고 없는 일자는 빈 행으로 내보내지 않는다(실제 있는 일자만). 객실 유형이 없으면 빈 목록을 반환한다.
- `hotel/HotelCatalogController`에 `GET /api/staff/hotels/{hotelId}/inventory`를 추가한다. 기존 카탈로그 컨트롤러와 같은 권한·지점 확인 순서를 쓴다.
- 응답은 `InventoryView`: `hotelId`·`roomTypes`(객실 유형별 `roomTypeId`·`name`·`days`).

### 관리자 (SDTPL_ADM)

- `staff-api.ts`에 `getStaffInventory`·`InventoryView` 타입을 추가한다. 기존 `getRoomTypeCatalog`와 같은 에러 패턴을 따른다.
- `components/hotel-admin/inventory-viewer.tsx`: 지점 선택기, 객실 유형 선택기, 기본 14일 기간, 일자 재고 표. 각 행에 날짜·잔여·판매 중·확정·총량을 보여주고 `remaining`이 0이면 매진 표시를 한다.
- `app/(dashboard)/dashboard/inventory/page.tsx`.
- `nav.ts`의 `catalogGroup`에 `재고·가격`을 추가한다.

### 테스트

- `InventoryQueryIntegrationTest`: 본사 200(remaining 계산 포함), 지점 403, 잘못된 세션 401, 없는 지점 404, 기간 검증 400, 92일 초과 400, 객실 유형이 없는 지점은 빈 목록.
- `e2e/inventory-viewer.spec.ts`: 본사 메뉴 노출, 객실 유형 선택 후 재고 표 표시, 매진 표시, 지점 직원에게 메뉴가 보이지 않음.

## 미구현 예정 항목

- 재고 조정(일자 capacity 변경), 판매 중지 구간 설정, 재고 일괄 업로드. 쓰기 동작은 다음 작업에서 다룬다.
- 가격(요금) 변경. 1번 메뉴의 쓰기 범위와 겹친다.
- 재고 부족 알림·자동 가격 조정. 운영 범위가 아니다.
- 영문 전환. 한국어 단일 언어다.
