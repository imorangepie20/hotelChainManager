# 본사 재고·가격 - 객실 유형별 일자 총량 조정

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.

## 변경 요약

- `admin-menu-roadmap.md` 2번 `재고·가격`의 첫 쓰기 동작을 구현했다. 본사가 객실 유형별 일자 재고를 볼 수만 있었고, 객실 유형을 추가하며 심은 총량(기본 8실 × 90일)을 바꿀 수단이 없었다. 매진 처리를 하려면 객실 유형을 직접 DB로 건드려야 했다.
- `PATCH /api/staff/hotels/{hotelId}/inventory`가 객실 유형의 일자 총량을 바꾼다. 요청 하나에 여러 일자를 넣을 수 있고, 한 트랜잭션에 묶어서 일부만 바뀌는 일이 없게 한다.
- **총량을 내릴 때는 서버가 강하게 검증한다.** 해당 일자의 확정(`confirmed`)·보류(`held`) 건수 아래로 내리면 409 `INVENTORY_CAPACITY_CONFLICT`로 거부하고 재고를 바꾸지 않는다. 재고는 음수가 될 수 없기 때문이다. 총량을 올리는 것은 어떤 예약도 새 한도를 초과하지 않으므로 검사 없이 허용한다.
- 총량 0은 판매 중지와 같다. 확정·보류가 없는 일자만 0으로 내릴 수 있다.
- 시드되지 않은 일자는 `inventory_day` 행이 없다. 총량을 정할 수 없으므로 404 `INVENTORY_DAY_NOT_FOUND`로 거부한다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려준다. 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·`hotel_id`·`room_type_id`·일자·총량의 SHA-256 지문이 같아 같은 결과를 돌려준다. `room_type` 행을 `for update`로 잡아 동시 쓰기를 직렬화한다.
- 재호출은 요청했던 일자만 돌려준다. 전체 일자를 내보내면 다른 날짜의 재고가 바뀐 뒤 재호출 응답이 달라져 멱원이 깨진다.
- 관리자 `/dashboard/inventory`의 그리드에 `총량 조정` 열과 대화상자를 추가했다. 본사만 버튼이 보이고, 성공하면 재고를 새로고침한다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- 가격·재고·예약 확정의 권한은 계속 Spring Boot에 있다. 관리자 UI는 서버가 검증한 결과만 표시한다.

## 서비스별 변경

### services/api

- `db/migration/V54__inventory_command_idempotency.sql`(신규): additive. `inventory_command` 테이블이 멱원 키와 요청 지문을 보관한다. `hotel_id`·`staff_id`·`idempotency_key`에 고유 인덱스를 건다. 기존 표를 변경하지 않는다.
- `inventory/InventoryAdjustRequest`(신규): `roomTypeId`·`adjustments`(`DayAdjustment` = `stayDate` + `capacity`). 총량은 0~1,000이고 위반은 400이다. 날짜나 객실 유형이 비어 있어도 400이다.
- `inventory/InventoryAdjustResponse`(신규): `hotelId`·`roomTypeId`·`days`(`capacity`·`held`·`confirmed`·`remaining`)·`created`.
- `inventory/InventoryCommandService`(신규): 조정 본문. `countConflictingDays`가 내리는 일자의 확정·보류 합계를 검사하고, `applyAdjustment`가 `inventory_day`의 `capacity`를 바꾼 뒤 결과를 다시 읽는다. 멱원은 `findAdjusted`가 같은 키·같은 지문의 이전 결과를 재생한다.
- `inventory/InventoryCapacityConflictException`(신규): 409로 매핑.
- `inventory/InventoryDayNotFoundException`(신규): 404로 매핑.
- `inventory/HotelInventoryController`: `PATCH /{hotelId}/inventory` 핸들러 추가. 새 결과는 201, 재호출은 200이다.
- `web/ApiExceptionHandler`: `INVENTORY_CAPACITY_CONFLICT` 409·`INVENTORY_DAY_NOT_FOUND` 404 매핑 추가.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `InventoryAdjustInput`·`InventoryAdjustResult` 타입 추가. `adjustStaffInventory` 추가. `inventoryAdjustFailureMessage`가 403·404(`INVENTORY_DAY_NOT_FOUND` 구분)·409를 한국어 안내로 매핑한다.
- `src/components/hotel-admin/inventory-viewer.tsx`: 그리드에 `총량 조정` 열과 버튼, 대화상자를 추가했다. 총량 입력은 `type="text"` + `inputMode="numeric"`을 썼다. 브라우저가 `max` 초과로 폼 제출을 막으면 서버 검증 응답이 도달하지 않으므로(공통 정책·객실 유형 화면에서 이미 같은 문제가 있었다) 범위 판단은 서버에 뒀다. `parseCapacity` 헬퍼가 공백·숫자 외 문자를 정리한다. 조정 대상은 그리드에 표시된 전체 숙박일이다.
- `e2e/inventory-viewer.spec.ts`: 6→10건. 총량 조정 성공·409 충돌 안내·지점 직원 버튼 숨김·390px 가로 넘침 확인을 추가했다.

### 설계 변경

- 계획 없이 바로 구현했다. 로드맵 2번의 "조정"이 뚜렷한 다음 단계였기 때문이다.
- 조정 대상을 "그리드에 표시된 전체 숙박일"로 정했다. 90일분을 전부 보여주면 90일을 한 번에 덮는 것이 되므로, 본사가 기간을 좁히려면 이미 있는 시작일·종료일 필터로 그리드 범위를 먼저 줄여야 한다. 별도 체크박스를 두면 90개의 체크박스를 다뤄야 해서 기존 필터를 재사용했다.
- 총량 0을 "판매 중지"로 해석했다. 로드맵의 "판매 중지 구간 설정"은 재고 쓰기와 별도 상태를 두지 않고 이 조정으로 같은 효과를 낸다.

## 검증

- API **전체 522건**(실패 1, 건너뜀 3)이다. 실패 1건은 `TossSettlementIntegrationTest.resumes_failed_page_without_deleting_previous_snapshot`이다. 작업 트리의 변경을 전부 stash로 빼고 HEAD에서 따로 실행하면 **2건 모두 통과**하므로 이 변경과 무관한 비결정적(테스트 순서 의존) 실패다. 재고 쓰기 직접 16건은 종료 코드 0이다: 상승 201, 확정 건수 위로 하향 201, 확정 2건 아래 1실로 하향 409 `INVENTORY_CAPACITY_CONFLICT`, 보류 4건 아래 5실로 하향 409, 확정 0으로 둔 일자의 0실 201(판매 중지), 음수·1,001 400, 멱원 키 누락 400, 같은 키 재호출 200 `created=false` 및 `inventory_command` 1건, 새 키 같은 내용 200, 2일 조건 201, 시드 없는 일자 404 `INVENTORY_DAY_NOT_FOUND`, 지점 직원 403, 세션 없음 401, 없는 지점 404, 다른 지점 유형 404, 빈 adjustments 400.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 변경 파일 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `inventory-viewer` **10건**·`hotel-catalog` **13건**이 종료 코드 0이다. 390×844에서 가로 넘침이 없음을 확인했다.
- 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다.

## 미검증 항목

- 라이브 PostgreSQL 개발 DB에서의 마이그레이션(V54) 적용과 `PATCH` 호출. compose `api` 재빌드도 하지 않았다.
- Vite production build는 실행하지 않았다. `tsc --noEmit`과 Playwright 브라우저 검증으로 대체했다.
- 두 본사 관리자가 같은 객실 유형의 같은 일자를 동시에 바꿀 때의 경합. `room_type` 행을 `for update`로 잡고 트랜잭션을 묶었지만 동시 쓰기를 직접 시도하지는 않았다.
- 조정 대상이 "그리드에 표시된 전체 숙박일"이라는 점에서 30일 프리셋으로 범위를 늘린 뒤 조정하면 30일이 한 번에 덮어씌워지는 위험을 브라우저에서 직접 확인하지 않았다. 대화상자 설명에 표시 일수가 나오므로 확인은 가능하다.
- 영문 전환. 한국어 단일 언어다.
- 재고 조정이 고객 웹의 가용성에 어떻게 나타나는지. `GET /api/availability`의 `remaining`이 같은 `inventory_day`에서 계산되지만 이 변경에서 고객 웹을 따로 실행하지는 않았다.
- 일괄 업로드(CSV)·판매 중지 구간 설정은 이번 범위가 아니다.
