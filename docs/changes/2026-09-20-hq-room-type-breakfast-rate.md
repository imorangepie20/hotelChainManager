# 관리자 호텔 및 객실 - 한글 깨짐 원인 파악과 조식·기본 요금 입력

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.

## 변경 요약

- 사용자가 "관리자 페이지 호텔 및 객실 페이지에서 한글이 깨졌다"고 보고했다. 코드 전체를 UTF-8 바이트 단위로 점검한 결과 **소스 파일에는 깨진 글자가 없었다**. 원인은 콘솔(Windows PowerShell) 코드 페이지였다. `Get-Content`/`git log` 출력이 한글을 표시할 수 없는 코드 페이지로 내려와서 터미널에서만 글자가 깨져 보였다. 파일은 수정하지 않았다.
  - 점검 근거: `Get-Content -Encoding Byte`로 BOM과 코드포인트를 직접 읽어 UTF-8 정상 여부를 확인했고, `node`로 코드포인트를 찍어 모든 한글이 정상 범위에 있는 것을 확인했다. 깨진 바이트를 가진 파일은 0건이었다.
- 같은 화면에서 **조식 포함 여부와 기본 요금 입력이 없었다**. 이것은 실제 결함이었다. 객실 유형을 추가할 때 조식 포함 여부가 항상 `false`로 고정됐고, 본사가 수정 화면에서 현재 조식 여부·요금을 볼 수도 없었다. 이 부분을 구현했다.
- `POST /api/staff/hotels/{hotelId}/room-types`가 `breakfastIncluded`·`defaultRateKrw`를 받아 기본 요금제와 90일분 일자 요금·재고를 심는다. 값은 `room_type.seed_breakfast_included`·`room_type.seed_default_rate_krw`에 보관돼서 본사가 입력한 값이 시드에 반영됐는지 확인할 수 있다.
- `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 조식 포함 여부와 기본 요금을 바꾼다. 둘 다 null이면 변경하지 않는다.
  - **조식 포함 여부는 서버가 강하게 검증한다.** 확정 예약이 현재 조식 조건으로 예약돼 있으면 409 `ROOM_TYPE_BREAKFAST_CONFLICT`로 거부하고 값을 바꾸지 않는다. 예약은 `rate_plan_id`만 가지므로 계약 내용이 달라지면 안 되기 때문이다.
  - 기본 요금을 바꾸면 일자별 금액 전체를 같은 폭으로 옮긴다. 주말·계절 차등은 그대로 두고 기준 금액만 옮긴다. 이미 확정된 예약의 금액은 변경하지 않는다.
  - 요금제가 없는 유형은 404 `ROOM_TYPE_RATE_PLAN_NOT_FOUND`로 거부한다.
- `GET /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/defaults`가 수정 화면에 미리 채울 기본 요금제의 현재값을 돌려준다. SELECT만 사용한다.
- 카탈로그 본문(`GET /api/staff/hotels/{hotelId}/room-types`)의 객실 유형에 `breakfastIncluded`·`defaultRateKrw`가 추가됐다. 기존 `요금제` 열과 `조식` 열은 그대로 두고, 본사가 수정 화면에서 현재값을 복원하는 데 쓴다.
- 멱원은 기존 두 갈래를 그대로 유지한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 결과를 돌려준다. 요청 지문에 `breakfastIncluded`·`defaultRateKrw`를 포함했다.
- 가격·재고·예약 확정의 권한은 계속 Spring Boot에 있다. 관리자 UI는 서버가 검증한 결과만 표시한다.

## 서비스별 변경

### services/api

- `db/migration/V53__room_type_seed_breakfast_and_rate.sql`(신규): additive. `room_type`에 `seed_breakfast_included BOOLEAN`·`seed_default_rate_krw INTEGER`를 추가하고 CHECK 제약을 건다. 기존 행은 모두 `NULL`이고, 본사가 만든 유형만 값이 들어간다. 기존 행·제약을 변경하지 않는다.
- `hotel/RoomTypeCreateRequest`: `breakfastIncluded`(Boolean)·`defaultRateKrw`(Integer) 추가. `validate()`가 요금 0~10,000,000원을 검증한다. `breakfastIncludedOrFalse()`는 null-safe 접근자(레코드 접근자와 반환형이 달라야 해서 이름을 따로 붙였다).
- `hotel/RoomTypeUpdateRequest`: 같은 두 필드 추가. null이면 "변경하지 않음"을 뜻한다.
- `hotel/RoomTypeCreateResponse`: `SeedSummary`에 `breakfastIncluded` 추가. `SeedBatch`에도 `breakfastIncluded`를 추가했다.
- `hotel/RoomTypeUpdateResponse`: `RatePlanSummary`(`ratePlanId`·`ratePlanName`·`breakfastIncluded`·`defaultRateKrw`·`pricedDays`)를 추가. 요청이 요금제를 바꿨든 안 바꿨든 현재값을 돌려줘서 멱원 재호출이 같은 응답을 내려준다.
- `hotel/RoomTypeRatePlanService`(신규): 기본 요금제 찾기·읽기·병합. `loadDefaults`·`updateDefaults`·`countBreakfastConflicts`. 호출자의 트랜잭션 안에서 동작한다.
- `hotel/RoomTypeBreakfastConflictException`(신규): 409로 매핑.
- `hotel/RoomTypeRatePlanNotFoundException`(신규): 404로 매핑.
- `hotel/RoomTypeCommandService`: 생성 시 본사가 입력한 조식·요금을 시드의 기준으로 쓴다. `requestHash`에 두 필드를 포함. `seedSummary`가 `RoomTypeRatePlanService`에서 기본 요금제를 읽는다.
- `hotel/RoomTypeUpdateService`: `applyRatePlanChanges`가 null이 아닌 필드만 바꾼다. `loadCurrent`가 멱원 재호출에 현재 요금제 상태를 같이 내려준다.
- `hotel/HotelCatalogQueryService`: 카탈로그 본문에 `breakfastIncluded`·`defaultRateKrw` 추가, `roomDefaults` 쿼리 추가.
- `hotel/RoomTypeCatalogView`: `breakfastIncluded`·`defaultRateKrw` 필드와 `RoomDefaults` 중첩 레코드 추가.
- `hotel/HotelCatalogController`: `GET .../defaults` 핸들러 추가.
- `web/ApiExceptionHandler`: `ROOM_TYPE_BREAKFAST_CONFLICT` 409·`ROOM_TYPE_RATE_PLAN_NOT_FOUND` 404 매핑 추가.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `CreateRoomTypeRequest`·`CreatedRoomTypeSeed`·`UpdatedRoomType`·`UpdateRoomTypeInput`·`RoomTypeCatalogEntry`·`RoomTypeDefaults` 타입 갱신. `getRoomTypeDefaults` 추가. `parseRateKrw` 헬퍼 추가(공백·쉼표·숫자 외 문자를 정리). `roomTypeUpdateFailureMessage`가 `ROOM_TYPE_BREAKFAST_CONFLICT` 코드를 한국어 안내로 매핑.
- `src/components/hotel-admin/hotel-catalog.tsx`: 추가·수정 대화상자에 `조식 포함` 체크상자와 `기본 요금 (원)` 입력을 넣었다. 요금 입력은 `type="text"` + `inputMode="numeric"`을 썼다. 브라우저가 `max` 초과로 폼 제출을 막으면 서버 검증 메시지가 도달하지 않으므로(정책 변경 화면에서 이미 같은 문제가 있었다) 범위 판단은 서버에 뒀다. 수정 대화상자를 열 때 `getRoomTypeDefaults`로 현재값을 미리 채운다.
- `e2e/hotel-catalog.spec.ts`: 13→15건. 조식 충돌 안내 시나리오를 추가하고, 모든 생성·수정 시나리오가 두 입력을 채운다.

### 설계 변경

- 계획 없이 바로 구현했다. 사용자 보고가 뚜렷한 결함(입력 부재)이었기 때문이다.
- 수정 요청에서 조식·요금을 "보냈을 때만 바꾼다"로 정했다. 객실 유형의 속성이 아니라 기본 요금제의 속성이라서, 이름·인원만 바꾸는 기존 호출이 요금까지 건드리지 않게 하려는 목적이다.
- 기본 요금을 "일자별 금액 전체를 같은 폭으로 옮기기"로 정했다. 차등 구조를 보존하면서 기준 금액만 옮기면 본사가 예상한 대로 가격이 움직인다. 모든 일자를 새 금액으로 덮어쓰면 주말·계절 차등이 사라진다.

## 검증

- API **전체 506건**(실패 0, 건너뜀 3)이 종료 코드 0이다. 그중 객실 유형 직접 27건(기존 21 + 신규 6): 조식·요금을 넣은 생성 201과 `seed.breakfastIncluded`·`seed.defaultRateKrw` 반영, 음수·초과 요금 400, 조식·기본 요금 수정 200과 `rate_plan`·`rate_day` 반영, 확정 예약이 있는 유형의 조식 변경 409 `ROOM_TYPE_BREAKFAST_CONFLICT`, 요금제 없는 유형의 요금 변경 404, `defaults` 조회 200. 카탈로그 7건도 종료 코드 0이다.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 변경 파일 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `hotel-catalog` **15건** 종료 코드 0. 전체 회귀 **182 passed**(종료 코드 0, 12.8분). 인접 `inventory-viewer`·`staff-accounts`·`admin-navigation` 18건도 종료 코드 0이다.
- 라이브: compose `api` 재빌드 뒤 health 정상. V53 마이그레이션 적용. 본사 세션으로 `POST` 201 `seed.breakfastIncluded=true defaultRateKrw=95000 pricedDays=90 inventoryCapacity=8`, `PATCH` 200 `ratePlan.defaultRateKrw=110000`, 같은 키 재호출 200 `created=false ratePlan.defaultRateKrw=110000`, 음수·100,000,000원 400, `defaults` 200. 고객 `GET /api/availability`(오늘→+3일, 성인 3) 응답에 새 유형이 `remaining=8 total=330000 breakfastIncluded=true`로 포함됐다. 요금제가 없는 기존 유형(`트윈 스페셜`, plans=0)은 조식·요금 변경이 404 `ROOM_TYPE_RATE_PLAN_NOT_FOUND`로 거부됐다.
- 라이브 복구: 검증용으로 만든 `트윈 스페셜` 1종과 의존 행(`rate_day` 90·`inventory_day` 90·`rate_plan` 1·`room_type_command` 3)을 삭제해 라이브를 원래 4종으로 복원했다. `GET` 카탈로그 `totalCount=4`로 확인.
- 한글 깨짐: 소스 파일 0건. `.tmp/hangul-check.cjs`로 코드포인트를 점검했다. 터미널 코드 페이지가 원인이므로 소스를 바꾸지 않았다.

## 미검증 항목

- Vite production build는 실행하지 않았다. `tsc --noEmit`과 Playwright 브라우저 검증으로 대체했다.
- 390px 모바일에서 새 입력이 넘치지 않는지 확인하지 않았다. 대화상자가 좁은 화면에서 세로로 쌓이는지만 봤다.
- 두 본사 관리자가 같은 객실 유형의 조식·요금을 동시에 바꿀 때의 경합. `room_type` 행을 `for update`로 잡고 트랜잭션을 묶었지만 동시 쓰기를 직접 시도하지는 않았다.
- 영문 전환. 한국어 단일 언어다.
- 조식 포함 여부가 바뀐 뒤 고객 웹의 객실 상세·예약 화면에 반영되는지. 고객 웹은 이 변경에 닫혀 있고, `GET /api/availability`의 `breakfastIncluded` 필드만 확인했다.
