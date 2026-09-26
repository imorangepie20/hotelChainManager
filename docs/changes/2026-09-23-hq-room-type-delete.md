# 본사 객실 유형 관리 - 객실 유형 삭제 (2026-09-23)

> 상태: 작업 트리에만 있고 커밋하지 않은 상태.

## 변경 이유

`admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 남은 항목 중 "객실 유형 삭제"를
구현했다. 본사가 객실 유형을 만들고 수정할 수 있었지만 **지울 수단이 없었다.**
잘못 만든 유형이나 더 이상 팔지 않는 유형이 카탈로그·재고·보고서 화면에
계속 나타났다. [설계서](../plans/2026-09-23-hq-room-type-delete.md)를 먼저 기록했다.

`2026-09-20-hq-room-type-update.md`·`2026-09-22-hq-hotel-create.md`·
`2026-09-22-hq-hotel-activation.md` 세 기록이 모두 "삭제는 외래키가 걸려 있어
안전한 삭제 조건을 따로 설계해야 한다"고 미뤄둔 항목이다. 이 작업이 그 조건을 정했다.

## 서버 변경

- `DELETE /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 객실 유형을 지운다.
  `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400,
  없는 유형·다른 지점의 유형은 404다.
- **진행 중인 판매·예약·배정이 있으면 409 `ROOM_TYPE_DELETION_CONFLICT`로 거부하고
  아무것도 지우지 않는다.** 이 정책은 `PATCH` 수정들이 취한 것과 같은 기준이다.
  충돌 4가지를 모두 센다.
  1. 진행 중인 예약 (`reservation.status = 'CONFIRMED'`). `CHECKED_IN`·`CHECKED_OUT`·
     `CANCELLED`·`NO_SHOW`·`EXPIRED`는 지나간 상태이므로 재고를 잡지 않는다.
  2. 진행 중인 예약 변경 요청. 종료 상태(`COMPLETED`·`REJECTED`·`CANCELLED`·`EXPIRED`)가
     아닌 요청은 아직 승인·정산 중이다.
  3. 보류 중인 재고 (`inventory_day.held > 0`). 보류는 만료되기 전까지 실재고를 잡는다.
  4. 배정된 실제 객실 (`physical_room`). `reservation_room_assignment`가
     `physical_room`을 거쳐 유형을 참조한다.
- **에러 메시지가 어느 조건이 막았는지 알려준다.** "진행 중인 예약 2건, 배정된
  실제 객실 1실이 있어야 지울 수 있습니다."처럼 본사가 다음에 할 일을 정할 수 있다.
- 지우는 것: `room_type`·`inventory_day`·`website_page_room_type` 연결.
- **연결만 끊고 남기는 것: `rate_plan`·`reservation`.** 종료된 예약이
  `reservation.rate_plan_id`·`reservation.room_type_id`를 NOT NULL로 참조하므로
  **행을 지울 수 없다.** 지우면 과거 예약의 근거가 사라진다. 그래서
  `update ... set room_type_id = null`로 참조만 끊는다.
- **남은 요금제·예약은 고객 검색에 나타나지 않는다.** `AvailabilityService`·
  `InventoryQueryService`·`HotelCatalogQueryService`가 모두 `room_type`에서 시작해서
  join하므로 유형이 없으면 함께 빠진다. 이것이 행을 남겨도 화면에 안 나오는 이유다.
- **감사 이력·종료된 예약·예약 변경 요청은 보존한다.** 과거 운영 기록이므로
  삭제가 이것들을 건드리지 않는다. 이것이 유형을 바로 지우지 못하고 조건을 둔 이유다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `deleted=false`로
  같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도
  `staff_id`·지점·유형의 SHA-256 지문이 같아 같은 결과를 돌려준다.
- 응답은 `RoomTypeDeletionResponse(hotelId, roomTypeId, name, deleted, remainingRoomTypes)`다.
  `remainingRoomTypes`는 삭제 뒤 남은 유형 수다. 0이면 지점이 빈 상태가 돼서
  고객 검색에 나타나지 않는다.

## 마이그레이션 변경

- V61 additive 마이그레이션이 `room_type_command.deleted_room_type_name`을 추가하고
  `room_type_command`·`rate_plan`·`reservation`의 `room_type_id` NOT NULL을 해제한다.
  삭제가 참조를 끊기 때문이다.
- `room_type_command (hotel_id, staff_id, kind, idempotency_key) WHERE room_type_id IS NULL`
  부분 유일 인덱스를 추가한다. 삭제 기록은 `room_type_id`가 비어 있어 기존
  CREATE·UPDATE 기록과 섞이지 않는다.
- **기존 행은 모두 `room_type_id`가 있으므로 이 변경이 빈 값을 만들지 않는다.**
  라이브 DB의 3개 지점·4개 유형은 영향을 받지 않는다.

## 설계 중 바뀐 점

1. **처음에는 `rate_plan`·`rate_day`를 함께 지우려 했다.** "요금은 유형 없이 의미가
   없다"라고 판단했다. 테스트가 `reservation_rate_plan_id_fkey` 위반을 잡아냈다.
   **종료된 예약이 요금제를 NOT NULL로 참조하므로 지울 수 없었다.** 그래서 남기고
   참조만 끊는 것으로 바꿨다. 설계서의 이 점을 고쳤다.
2. **처음에는 멱원 행을 삭제 앞에 쓰려 했다.** `room_type_command`가 `room_type`을
   참조하므로 유형을 지우면 외래키 위반이 났다. 삭제 뒤에 쓰는 것으로 바꿨다.
3. **`room_type_id`가 NOT NULL이어서 null을 넣을 수 없었다.** V61이 NOT NULL을
   해제했다.
4. **멱원 재호출이 404를 냈다.** 유형이 이미 지워졌으면 `loadRoomType`가 null을
   돌려줘서 "없는 유형"으로 착각했다. `alreadyHandled` 검사를 유형 조회 앞으로 옮겼다.

## 관리자 UI 변경

- `호텔 및 객실` (`/dashboard/hotels`)의 객실 유형 표에 `삭제` 열과 버튼을 추가했다.
- 확인 대화상자가 무엇을 지우는지, 진행 중인 예약이 있으면 거부된다는 것을
  미리 알려준다. **본사가 고객에게 미리 알리지 않은 채 확정 예약이 사라지는 것을
  막기 위해 "종료된 예약과 이력은 보존하고 고객 검색에서만 빠진다"고 적었다.**
- 삭제는 매번 새 멱원 키를 쓴다 (중지·재개 버튼과 같은 패턴).
- 서버가 409를 내면 **대화상자를 닫지 않고** 이유를 보여준다. 본사가 어떤 예약을
  먼저 처리해야 하는지 알 수 있다.
- `staff-api.ts`의 `deleteRoomType`를 추가했다. 403·404·409 메시지를 구분한다.

## 검증 결과

- API `mvnw -Dtest=RoomTypeDeletionIntegrationTest` **17건**이 종료 코드 0이다.
  삭제·충돌 4종(확정 예약·진행 변경 요청·보류·배정 객실)·종료 예약 보존·
  고객 가용성에서 빠짐·직원 카탈로그에서 빠짐·CMS 페이지 연결 해제·
  멱원 두 갈래·403·401·400·404·다른 지점 404를 다룬다.
- 인접 9개 suite **185건**이 종료 코드 0다 (객실 유형 27·재고 16·요금 20·
  지점 생성 13·지점 수정 14·지점 판매 중지 14·감사 16·운영 통계 18·직원 계정 47).
  V61이 NOT NULL을 해제해도 기존 동작이 유지됨을 확인했다.
- 예약·가용성·카탈로그 조회 suite **88건**도 종료 코드 0다 (예약 6·취소 12·
  가용성·재고 조회·카탈로그 조회·고객 요청 62·체류 변경 7·인원 변경 8·
  승인 3). `reservation.room_type_id` NOT NULL 해제가 예약 흐름에 영향을 주지
  않음을 확인했다.
- 관리자 `tsc --noEmit`이 종료 코드 0이고, `eslint`는 경고 3건만 낸다
  (`react-hooks/set-state-in-effect`, 기존 컴포넌트와 같은 패턴).
- Playwright `hotel-catalog` **24건**(기존 22 + 신규 2)이 종료 코드 0이다.
  새 검증은 삭제→유형이 표에서 사라짐·남은 유형 수 안내, 409 안내가 표시되고
  대화상자가 닫히지 않음이다. 인접 5개 suite **61건**도 종료 코드 0이다.

## 발견·수정한 결함

1. `room_type_command`가 `room_type`을 외래키로 참조하면서 `room_type_id`가
   NOT NULL이었다. 삭제 멱원 기록은 유형 행이 없으므로 **두 가지 모두** 바꿔야 했다.
   이것이 V61 마이그레이션이 된 이유다. 처음 설계는 "새 마이그레이션이 필요 없다"고
   했으나 틀렸다. 설계서를 고쳤다.
2. **멱원 재호출이 유형 조회 뒤에 검사돼서 404를 냈다.** 유형이 이미 지워졌으면
   재호출도 "없는 유형"으로 보인다. `alreadyHandled`를 조회 앞으로 옮기고,
   `replayResponse`가 `deleted_room_type_name`에서 이름을 읽어 돌려준다.
3. **`reservation.rate_plan_id`가 NOT NULL이라 요금제를 지울 수 없었다.**
   이것이 "요금을 유형과 함께 지운다"는 설계를 바꿨다. 종료된 예약의 근거를
   보존하려면 요금제가 남아 있어야 한다.

## 미검증 항목

- **라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다.** V61이 라이브에
  적용되지 않았으므로 실제 삭제 흐름은 사용자가 확인한다.
- **삭제한 유형의 CMS 페이지가 여전히 빈 객실 목록을 보여줄 수 있다.**
  `website_page` 자체는 두고 `website_page_room_type` 연결만 끊는다.
  CMS에서 페이지를 따로 정리해야 한다. 연결이 없는 페이지가 고객 화면에서
  어떻게 보이는지는 확인하지 않았다.
- **AI 예약 컨시어지의 `/chat` 컨텍스트가 삭제한 유형을 추천하지 않는지 확인하지
  않았다.** AI는 `room_type`을 거쳐서 가용성을 읽으므로 빠질 것으로 예상하지만
  컨시어지 컨텍스트 빌드를 직접 검증하지는 않았다.
- **감사 이력(7번 메뉴)에 객실 유형 삭제가 기록되지 않는다.** `room_type_command`는
  멱원용 표이지 감사 표가 아니다. V29~V37 감사 표와 통합할 때 다룬다.
- **`rate_plan`·`reservation`의 `room_type_id IS NULL` 행이 늘어난다.**
  본사가 유형을 지울 때마다 생기고, 지우지 않으면 생기지 않는다. 운영 통계의
  `applyReservations`가 `join room_type`을 쓰므로 이 예약은 집계에서 빠진다.
  이것이 의도된 동작(과거 예약은 이미 취소·노쇼 상태)이지만, NULL 행의
  장기적인 양은 사용자가 지켜봐야 한다.
