# 본사 객실 유형 관리 - 객실 유형 삭제 (2026-09-23)

> 범위: `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 남은 항목 중 "객실 유형 삭제".
> 상태: 구현 완료. 작업 트리에만 있고 커밋하지 않은 상태.

## 변경 이유

본사가 객실 유형을 만들고 수정할 수 있지만 **지울 수단이 없었다.** 잘못 만든 유형이나 더 이상
팔지 않는 유형이 카탈로그·재고·보고서 화면에 계속 나타난다.

`2026-09-20-hq-room-type-update.md`·`2026-09-22-hq-hotel-create.md`·`2026-09-22-hq-hotel-activation.md`
세 기록 모두 "삭제는 외래키가 걸려 있어 안전한 삭제 조건을 따로 설계해야 한다"고 미뤄둔 항목이다.
이 작업이 그 조건을 정한다.

## 삭제를 허용하는 조건

객실 유형을 지우면 가격·재고·예약 가능 여부가 바뀐다. 그래서 **진행 중인 판매·예약이 하나라도
있으면 지우지 않고 409로 거부**한다. 이 정책이 `PATCH` 수정들이 취한 것과 같은 기준이다
(인원 충돌 → `ROOM_TYPE_OCCUPANCY_CONFLICT`, 조식 충돌 → `ROOM_TYPE_BREAKFAST_CONFLICT`,
재고 충돌 → `INVENTORY_CAPACITY_CONFLICT`).

삭제는 아래 네 가지가 **모두** 0건일 때만 허용한다.

1. 진행 중인 예약: `reservation.status = 'CONFIRMED'`.
   `CHECKED_IN`·`CHECKED_OUT`·`CANCELLED`·`NO_SHOW`·`EXPIRED`는 지나간 상태이므로
   재고를 더 이상 잡지 않는다. 과거 숙박 이력을 보존해야 하므로 **이 예약들을 지우지 않는다.**
2. 진행 중인 예약 변경 요청: `reservation_change_request` 중 종료 상태가 아닌 것.
   `COMPLETED`·`REJECTED`·`CANCELLED`·`EXPIRED`가 아닌 요청은 아직 승인·정산 중이므로
   `previous_room_type_id`·`target_room_type_id`가 유형을 계속 참조해야 한다.
3. 보류 중인 재고 확보: `inventory_day.held > 0`.
   보류는 만료되기 전까지 실재고를 잡고 있다.
4. 배정된 실제 객실: `physical_room` 행.
   `reservation_room_assignment`가 `physical_room`을 거쳐 유형을 참조하므로 유형을
   먼저 지울 수 없다. 객실 배정 화면이 유형별로 객실을 보여주므로 유형이 있어야 한다.

`confirmed`는 (1)의 확정 예약이 이미 막고 있으므로 따로 검사하지 않는다.

## 삭제가 지우는 것과 남기는 것

지우는 것:
- `room_type` 행.
- `inventory_day`. 유형이 없으면 판매할 수 없으므로 지운다.
- `website_page_room_type` 연결. 유형이 사라지면 콘텐츠 페이지가 가리키는 대상이 없다.
  페이지 자체는 두고 연결만 끊는다.

연결만 끊고 남기는 것:
- **`rate_plan`·`rate_day`.** 종료된 예약이 `reservation.rate_plan_id`를 NOT NULL로
  참조하므로 **요금제 행을 지울 수 없다.** 지우면 과거 예약의 근거가 사라진다.
  그래서 `update rate_plan set room_type_id = null`로 연결만 끊는다.
  **남은 요금제는 고객 검색에 나타나지 않는다.** `AvailabilityService`·
  `InventoryQueryService`가 `room_type`을 거쳐서 join하므로 유형이 없으면
  함께 빠진다. 본사 카탈로그(`HotelCatalogQueryService`)도 `room_type`에서
  시작하므로 마찬가지다.
- **`reservation` 행.** 같은 이유로 `update reservation set room_type_id = null`로
  참조만 끊고 행은 보존한다.

남기는 것:
- **종료된 예약·예약 변경 요청·감사 이력.** `reservation`·`reservation_change_request`·
  `reservation_stay_change_audit` 등은 과거 운영 기록이므로 **반드시 보존**한다.
  이것이 유형을 바로 지우지 못하고 조건을 두는 이유다.
- `room_type_command`·`rate_command`·`inventory_command` 멱원 기록.
  삭제에 쓰는 멱원 키를 포함해 그대로 둔다. 멱원 표는 idempotency를 위한 것이지
  유형의 현재 상태가 아니다.

### 마이그레이션

V61 `V61__room_type_delete_idempotency.sql`:
- `room_type_command.deleted_room_type_name VARCHAR(100)` 추가.
- `room_type_command.room_type_id`의 NOT NULL 해제. V46이 NOT NULL로 만들었는데,
  **삭제 기록은 유형 행이 없으므로** 값을 가질 수 없다. 기존 CREATE·UPDATE 기록은
  모두 값을 가지고 있어 이 변경이 빈 값을 만들지 않는다.
- `rate_plan.room_type_id`·`reservation.room_type_id`의 NOT NULL 해제.
  삭제가 연결을 끊기 때문이다.
- `room_type_command (hotel_id, staff_id, kind, idempotency_key) WHERE room_type_id IS NULL`
  부분 유일 인덱스 추가. 삭제 기록은 `room_type_id`가 비어 있어 기존
  CREATE·UPDATE 기록과 섞이지 않는다.
- **additive다.** 기존 행은 모두 `room_type_id`가 있으므로 열 추가와 NOT NULL 해제가
  기존 동작을 변경하지 않는다.

### 멱원 기록 순서

`room_type_command`는 `room_type`을 외래키로 참조하므로 **멱원 행은 유형 행을 지운
뒤에** 만들어진다. 삭제 기록은 `room_type_id`를 비우고 `deleted_room_type_name`으로
무엇을 지웠는지 보관한다. 트랜잭션이 묶여 있어 멱원 insert가 실패하면 삭제도
함께 롤백된다.

**멱원 재호출은 유형 조회 앞에서 검사한다.** 유형이 이미 지워졌으면 `loadRoomType`가
null을 돌려줘서 재호출이 404로 착각하게 된다. `alreadyHandled`를 먼저 부르고,
기록이 있으면 `replayResponse`가 `deleted_room_type_name`에서 이름을 읽어 돌려준다.

## 설계

### 서버

`DELETE /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`:
- `RoomTypeDeletionService.delete`가 `access.requireHeadquarters(token)`로 본사만 허용한다.
- 경로의 `hotelId`와 `roomTypeId` 쌍으로 찾는다. 다른 지점의 유형은 404다.
- `room_type` 행을 `select id from room_type where id = ? and hotel_id = ? for update`로
  잡아 동시 삭제·수정을 직렬화한다.
- 충돌 카운트가 0건이 아니면 `RoomTypeDeletionConflictException`으로 409를 내고
  **아무것도 지우지 않는다.** 지우다 말 수 없으므로 모든 삭제는 충돌 검사 **뒤에** 한다.
- 멱원은 다른 쓰기 동작과 같은 두 갈래로 동작한다. `room_type_command`의 `kind='DELETE'`에
  `(hotel_id, staff_id, kind, idempotency_key)` 부분 유일 인덱스로 같은 키 재호출을,
  `(hotel_id, staff_id, kind, request_hash)`로 응답 유실 뒤 같은 내용의 재시도를 찾는다.
  **V46·V52가 `kind` 칼럼을 가지고 있어 V61이 인덱스 하나와 열 하나를 추가한다.**
- 멱원 행은 유형 행을 지은 뒤에 만들어진다. `room_type_command`가 `room_type`을
  참조하기 때문이다. 자세한 것은 아래 '멱원 기록 순서'를 본다.

### 응답

`RoomTypeDeletionResponse(hotelId, roomTypeId, name, deleted, remainingRoomTypes)`:
- 삭제 성공은 200, 멱원 재호출도 200에 `deleted=false`로 같은 결과를 돌려준다.
- `remainingRoomTypes`는 삭제 뒤 남은 유형 수다. 본사가 빈 지점이 된 것을 바로 알 수 있다.

### 관리자 UI

`호텔 및 객실` (`/dashboard/hotels`)의 객실 유형 표에 `삭제` 버튼을 추가한다.
확인 대화상자를 한 번 거치고, 서버가 409를 내면 이유를 대화상자에 보여주고 닫지 않는다.
삭제·재호출은 매번 새 멱원 키를 쓴다 (중지·재개 버튼과 같은 패턴).

## 완료 기준

1. 진행 중인 예약·변경 요청·보류·배정 객실이 있으면 **409**를 내고 아무것도 지우지 않는다.
2. 종료된 예약·변경 요청은 삭제 후에도 **그대로** 있다.
3. 삭제 뒤 고객 `GET /api/availability`·`GET /api/hotels` 객실 목록·직원 카탈로그에
   그 유형이 나타나지 않는다.
4. 멱원이 두 갈래로 동작한다. 같은 키 재호출은 `deleted=false`, 응답 유실 뒤
   새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다.
5. 권한은 서버에 있다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401,
   멱원 키 누락은 400, 없는 유형·다른 지점의 유형은 404다.
6. additive 마이그레이션이 기존 행의 동작을 변경하지 않는다. 열 추가와
   NOT NULL 해제는 기존 행이 모두 값을 가지고 있어 빈 값을 만들지 않는다.

## 검증 계획

- `RoomTypeDeletionIntegrationTest` (신규): 충돌 4종(확정 예약·진행 변경 요청·보류·배정 객실)·
  종료 예약 보존·빈 유형 삭제·고객 가용성·직원 카탈로그·멱원 두 갈래·
  403·401·400·404·다른 지점 404.
- 인접 suite: 객실 유형 쓰기·재고·요금·지점 생성·지점 수정·지점 판매 중지·감사·운영 통계·직원 계정.
- 관리자 `tsc --noEmit`·`eslint`·Playwright `hotel-catalog`·인접 suite.
- **라이브 DB·compose 재빌드·브라우저 확인은 이번 범위에서 실행하지 않는다.**
  새 마이그레이션이 없으므로 라이브 재배포 없이 검증할 수 있다.

## 다음 작업

- `admin-menu-roadmap.md` 1번 남은 항목: **요금제 등록·수정(두 번째 요금제)**.
- 2번 남은 항목: **판매 중지 구간 설정**, **재고 일괄 업로드(CSV)**.
