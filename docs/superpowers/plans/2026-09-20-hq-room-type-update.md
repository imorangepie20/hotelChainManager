# 본사 객실 유형 - 이름·최대 인원 수정

> 상태: 구현·검증 완료. 최종 갱신: 2026-09-20. [변경 기록](../../changes/2026-09-20-hq-room-type-update.md)

## 배경

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`에서 읽기 전용 카탈로그·객실 유형 추가·초기 재고·요금 시드는 끝났다. 로드맵이 남은 다음 항목은 "객실 유형 수정·삭제"다.
- 본사가 객실 유형 이름이나 최대 인원을 바꿀 수단이 전혀 없다. 시드가 만든 `기본 요금제` 같은 오타나, 오픈 후 실제 객실 리모델 기준(스탠다드 2인 → 3인)을 반영하려면 DB를 직접 만져야 한다.
- 수정은 생성보다 안전한 쓰기 동작이 아니다. `max_occupancy`는 예약 가능 조건(`rt.max_occupancy * rooms >= partySize`)에 직결되므로, 값을 올리면 새 조건의 예약이 추가로 허용되고, 내리면 진행 중인 예약과 충돌할 수 있다.
- `room_type`은 `reservation`·`reservation_change_request`·`physical_room`·`inventory_day`·`rate_plan`·`website_page_room_type`이 참조한다. 그래서 삭제는 이번 범위에서 빼고, 이름·최대 인원 수정만 다룬다.

## 완료 기준

1. `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 본사 세션으로 객실 유형의 이름·최대 인원을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다.
2. 이름은 1~100자, 최대 인원은 1 이상 20 이하. 위반은 400이고 멱원 키 누락도 400이다.
3. 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 결과를 돌려준다.
4. **최대 인원을 내릴 때는 서버가 강하게 검증한다.** 현재 진행 중인 예약(`PENDING_PAYMENT`·`CONFIRMED`) 중 새 인원을 초과하는 예약이 하나라도 있으면 409로 거부한다. 내린 값이 예약 가능 조건에 영향을 주기 때문이다.
5. 최대 인원을 올리는 것은 제한 없이 허용한다. 이미 확정된 예약의 인원이 새 한도를 초과할 수 없기 때문이다.
6. 없는 지점은 404, 없는 객실 유형은 404, 다른 지점의 객실 유형도 404다.
7. 카탈로그 `GET /api/staff/hotels/{hotelId}/room-types`와 고객 `GET /api/availability`가 바뀐 값을 즉시 반영한다.
8. 관리자 `/dashboard/hotels`의 객실 유형 표에 수정 버튼과 수정 대화상자를 추가한다. 서버 검증 실패 시 대화상자를 닫지 않는다.
9. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다.

## 설계 결정

### 수정과 삭제의 분리

- 삭제는 `reservation`·`reservation_change_request`·`website_page_room_type`의 외래키가 걸려 있어, 진행 중인 예약이 있으면 `RESTRICT`로 막히고, 없더라도 과거 예약의 이력이 사라진다. 안전한 삭제 조건(확정 예약 0건 + 예약 변경 요청 0건 + CMS 참조 0건)을 검증하는 설계가 따로 필요하다. 이번에는 다루지 않고, 수정만 먼저 만든다.
- 이름 수정은 진행 중인 예약에 영향을 주지 않는다. 예약은 `room_type_id`를 참조하고 이름을 저장하지 않으므로, 카탈로그·고객 화면 표시만 바뀐다.

### 멱원과 요청 지문

- `room_type_command`의 `(hotel_id, staff_id, kind, idempotency_key)` UNIQUE를 그대로 재사용한다. `kind`에 `UPDATE`를 추가하고, `room_type_command`에 수정 내용을 담는 컬럼을 추가하지 않는다 — 수정은 새로운 행을 만드는 게 아니라 기존 행을 바꾸는 것이므로, 커맨드 표에는 "이 요청을 처리했다"는 사실만 기록한다.
- 재호출이 같은 결과를 내려주려면 수정 뒤의 값을 읽어서 돌려줘야 한다. 그래서 멱원 키로 커맨드 표를 찾고, 있으면 저장된 `room_type`의 현재 값을 돌려준다. 새 키로 같은 내용이면 `request_hash`가 같아 같은 결과를 돌려준다.
- 수정의 요청 지문은 `staff_id|hotel_id|roomTypeId|name|maxOccupancy`의 SHA-256이다. 생성의 지문에 `roomTypeId`가 추가된 형태다.

### 동시 수정

- `room_type` 행을 `for update`로 잡아 동시 수정을 직렬화한다. 동시에 같은 유형을 다른 값으로 바꾸면 마지막이 이기는 것을 막지는 않지만, 한 트랜잭션이 읽고 쓰는 동안 다른 쓰기가 끼어들지 않게 한다.

## 구현 범위

### services/api

- V52 additive 마이그레이션: `room_type_command`의 `kind` CHECK에 `UPDATE`를 추가하고, `previous_name`·`previous_max_occupancy` 컬럼을 추가한다. 기존 행은 모두 `kind='CREATE'`이므로 영향이 없다. UNIQUE 제약은 그대로 둔다.
- `hotel/RoomTypeUpdateRequest`(신규): `name`·`maxOccupancy`, `validate()`.
- `hotel/RoomTypeUpdateResponse`(신규): `roomTypeId`·`hotelId`·`name`·`maxOccupancy`·`created`.
- `hotel/RoomTypeUpdateService`(신규): 본사 권한·멱원 키·객체 존재 확인 뒤 최대 인원 내림 충돌 검사, `room_type` 행 `for update`, `room_type_command` 기록.
- `hotel/HotelCatalogController`: `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`.
- `hotel/HotelCatalogQueryService`: 변경 없음. 수정 뒤 카탈로그를 새로고침하면 자동 반영된다.

### SDTPL_ADM

- `staff-api.ts`: `updateRoomType`·`UpdatedRoomType` 타입. 403·404·409 안내 분리.
- `components/hotel-admin/hotel-catalog.tsx`: 객실 유형 표에 수정 버튼, 수정 대화상자(이름·최대 인원). 서버 검증 실패 시 닫지 않는다.
- `e2e/hotel-catalog.spec.ts`: 수정·반영·멱원·검증 실패·권한·최대 인원 충돌 시나리오 추가.

### 테스트

- `RoomTypeCommandIntegrationTest` 확장: 수정 200, 같은 키 재호출 200 `created=false`, 새 키 같은 내용 200, 없는 유형 404, 다른 지점 404, 공백·0인·21인·키 누락 400, 지점 직원 403, 세션 없음 401, 최대 인원 내림 충돌 409, 카탈로그·가용성 반영.

## 미구현 예정 항목

- 객실 유형 삭제. 진행 중인 예약·변경 요청·CMS 참조가 0건일 때만 허용하는 안전 삭제는 다음 항목이다.
- 요금제 등록·수정, 일자별 요금 변경. 시드된 단일 금액을 덮어쓰는 동선은 그다음이다.
- 지점 생성·수정·판매 중지.
- `max_occupancy` 내림이 진행 중인 예약 변경 요청(`PENDING`·`QUOTE_ISSUED`)에 미치는 영향. 예약 변경은 이번 범위 밖이다.
- 영문 전환. 한국어 단일 언어다.
