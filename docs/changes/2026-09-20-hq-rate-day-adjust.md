# 본사 재고·가격 - 객실 유형별 일자 요금 개별 변경

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.

## 변경 요약

- `admin-menu-roadmap.md` 1번·2번 메뉴에 공통으로 남은 "일자별 요금 개별 변경"을 구현했다. 본사가 객실 유형의 기본 요금을 바꿀 수는 있었지만, **전체 일자를 같은 폭으로 옮기는 방식뿐**이어서 주말·휴일·계절 차등을 직접 만들거나 특정 날짜의 가격만 바꿀 수단이 없었다.
- `GET /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates`가 기본 요금제의 일자별 금액을 돌려준다. SELECT만 사용한다. `from`·`to` 필터(최대 92일)를 지원한다.
- `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates`가 날짜별 금액을 덮어쓴다. 한 트랜잭션에 묶어 일부만 바뀌는 일이 없게 한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 지점·다른 지점의 유형·없는 유형은 404다.
- **새 가격을 만들지 않는다.** `rate_day` 행이 없는 날짜는 404 `RATE_DAY_NOT_FOUND`로 거부한다. 객실 유형을 만들 때 90일분만 심으므로 그 범위를 벗어난 날짜의 가격은 이 API로 정할 수 없다. 요금제가 아예 없는 유형은 404 `ROOM_TYPE_RATE_PLAN_NOT_FOUND`다.
- 금액은 0원 이상 10,000,000원 이하이고 위반은 400이다. 한 요청에 92일을 넘기면 400, 멱원 키가 없어도 400이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·지점·유형·요금제·일자·금액의 SHA-256 지문이 같아 같은 결과를 돌려준다. `room_type` 행을 `for update`로 잡아 동시 쓰기를 직렬화한다.
- **재호출은 요청했던 일자만 돌려준다.** 전체 일자를 내보내면 다른 날짜의 가격이 바뀐 뒤 재호출 응답이 달라져 멱원이 깨진다.
- **이미 확정된 예약의 금액은 건드리지 않는다.** 예약은 결제 시점의 금액을 스냅샷으로 가지므로, 이후의 가격 변경은 새 예약에만 적용된다.
- 관리자 `/dashboard/inventory`의 그리드에 `요금 조정` 열과 대화상자를 추가했다. 대화상자를 열면 서버에서 현재 일자별 요금을 읽어와 칸별로 미리 채운다. 본사는 바꾸려는 날짜의 금액만 고치고, **바뀐 날짜만 서버에 보낸다.** 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- 가격·재고·예약 확정의 권한은 계속 Spring Boot에 있다. 관리자 UI는 서버가 검증한 결과만 표시한다.

## 서비스별 변경

### services/api

- `db/migration/V55__rate_command_idempotency.sql`(신규): additive. `rate_command` 테이블이 멱원 키와 요청 지문을 보관한다. `hotel_id`·`staff_id`·`idempotency_key`에 고유 인덱스를 건다. 기존 표를 변경하지 않는다.
- `hotel/RoomTypeRatesView`(신규): `hotelId`·`roomTypeId`·`ratePlanId`·`ratePlanName`·`days`(`RateDay` = `stayDate` + `amountKrw`).
- `hotel/RateAdjustRequest`(신규): `roomTypeId`·`adjustments`(`DayRate` = `stayDate` + `amountKrw`). `validate()`가 금액 0~10,000,000원·일자 92일 이하·빈 adjustments·null 날짜를 검증한다. `withRoomTypeId`가 경로 변수의 유형을 본문에 덮어쓴다.
- `hotel/RateAdjustResponse`(신규): `hotelId`·`roomTypeId`·`ratePlanId`·`days`·`created`.
- `hotel/RateQueryService`(신규): 일자별 요금 SELECT. 기본 요금제 선택 기준은 `RoomTypeRatePlanService`와 같다. `from`·`to` 기본값은 오늘부터 92일, 역순이면 다시 잡고, 92일 초과는 400이다.
- `hotel/RateCommandService`(신규): 요금 덮어쓰기 본문. `applyRate`가 `rate_day`의 `amount_krw`를 바꾸고, 0행이면 `RateDayNotFoundException`을 던진다. `dedupe`가 같은 날짜가 두 번 들어오면 마지막 값이 이기도록 합친다. `findAdjusted`가 같은 키 재호출과 같은 내용의 다른 키 재시도를 모두 잡는다. `requestHash`가 `staff_id`·`hotel_id`·`room_type_id`·`rate_plan_id`·일자·금액의 지문을 만든다.
- `hotel/RateDayNotFoundException`(신규): 404로 매핑.
- `hotel/HotelCatalogController`: `GET .../rates`·`PATCH .../rates` 핸들러 추가. PATCH는 경로의 `roomTypeId`를 본문에 덮어쓴 뒤 서비스에 넘긴다. 새 결과는 201, 재호출은 200이다.
- `web/ApiExceptionHandler`: `RATE_DAY_NOT_FOUND` 404 매핑 추가.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `RoomTypeRateDay`·`RoomTypeRates`·`RateAdjustInput`·`RateAdjustResult` 타입 추가. `getRoomTypeRates`·`adjustRoomTypeRates` 추가. `rateReadFailureMessage`가 `ROOM_TYPE_RATE_PLAN_NOT_FOUND` 코드를 구분해 한국어 안내로 매핑하고, `rateAdjustFailureMessage`가 `RATE_DAY_NOT_FOUND`·`ROOM_TYPE_RATE_PLAN_NOT_FOUND`·403·404를 매핑한다.
- `src/components/hotel-admin/inventory-viewer.tsx`: 그리드에 `요금 조정` 열과 `Wallet` 단추, 대화상자를 추가했다. 대화상자를 열면 `getRoomTypeRates`로 현재 일자별 요금을 읽어 칸별로 미리 채운다. `collectRateAdjustments`가 현재값과 다른 날짜만 골라내고, 제출 단추에 `N일 변경`으로 바뀐 날짜 수를 표시한다. 금액 입력은 `type="text"` + `inputMode="numeric"`을 썼다. 브라우저가 `max` 초과로 폼 제출을 막으면 서버 검증 응답이 도달하지 않으므로(공통 정책·객실 유형·재고 화면에서 이미 같은 문제가 있었다) 범위 판단은 서버에 뒀다. 서버 검증 실패 시 대화상자를 닫지 않는다.
- `e2e/inventory-viewer.spec.ts`: 10→14건. 단일 날짜 요금 변경(현재값 미리 채움·`1일 변경` 단추·완료 안내)·`RATE_DAY_NOT_FOUND` 안내·`ROOM_TYPE_RATE_PLAN_NOT_FOUND` 안내·지점 직원 단추 숨김을 추가했다.

### 설계 변경

- 계획 없이 바로 구현했다. 로드맵 1번·2번의 "일자별 요금 개별 변경"이 뚜렷한 다음 단계였기 때문이다.
- 요금 변경을 "새 가격을 만드는" 것이 아니라 "이미 판매 중인 가격을 덮어쓰는" 것으로 정했다. 시드 범위 밖의 날짜에 가격을 새로 만들면 재고와 요금이 맞지 않는 구간이 생길 수 있다. 그래서 `rate_day` 행이 없으면 404로 거부한다.
- 경로 변수의 `roomTypeId`를 본문보다 우선하기로 정했다. 본문과 경로가 달라도 본문을 믿지 않고 경로를 따라서, 한 지점의 유형을 바꾸려다 다른 지점의 유형을 건드리는 일을 막는다.
- 조회(`GET`)는 카탈로그·재고 조회와 같이 자기 지점까지 허용하고, 쓰기(`PATCH`)는 `HQ_ADMIN` 전용으로 정했다. 지점 직원도 자기 지점의 가격을 보는 것은 의미가 있지만, 가격을 정하는 것은 본사 권한이기 때문이다.
- UI에서 **바뀐 날짜만 서버에 보내기로** 정했다. 바뀌지 않은 날짜까지 보내면 멱원 지문이 의미 없이 커지고, 서버가 404로 거부할 수 있는 날짜가 늘어난다.

## 검증

- API 전체 suite **556건**(실패 0, 건너뜀 3)이 종료 코드 0이다. 그중 요금 직접 **20건**은: 단일 날짜 변경 201·`rate_day` 반영, 일자별 다른 금액 2일 동시 변경 201, 음수·100,000,001원 400·기존 금액 유지, 요금 행이 없는 날짜 404 `RATE_DAY_NOT_FOUND`·기존 금액 유지, 요금제 없는 유형 404 `ROOM_TYPE_RATE_PLAN_NOT_FOUND`, 빈 adjustments 400, 멱원 키 누락 400, 같은 키 재호출 200 `created=false`·`rate_command` 1건, 새 키 같은 내용 200 `created=false`, 지점 직원 403·기존 금액 유지, 세션 없음 401, 없는 지점 404, 다른 지점 유형 404, 조회 200 `ratePlanName`·5일·주말 차등 금액, 조회 날짜 범위 2일, 200일 범위 400, 지점 직원 타 지점 조회 403, 지점 직원 자기 지점 조회 200. 인접 suite(재고 쓰기 16·객실 유형 쓰기 27·카탈로그 7·직원 35)도 종료 코드 0이다.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 변경 파일 종료 코드 0(경고 3건: `react-hooks/set-state-in-effect` 2건은 기존 패턴, `no-unused-vars` 1건은 제거함).
- Playwright `inventory-viewer` **14건**, 인접 suite `hotel-catalog`·`staff-accounts`·`admin-navigation` **28건**이 종료 코드 0이다.
- 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다.

## 미검증 항목

- 라이브 PostgreSQL 개발 DB에서의 V55 마이그레이션 적용과 `PATCH`·`GET` 호출. compose `api` 재빌드도 하지 않았다.
- Vite production build는 실행하지 않았다. `tsc --noEmit`과 Playwright 브라우저 검증으로 대체했다.
- 두 본사 관리자가 같은 객실 유형의 같은 날짜를 동시에 바꿀 때의 경합. `room_type` 행을 `for update`로 잡고 트랜잭션을 묶었지만 동시 쓰기를 직접 시도하지는 않았다.
- 390px 모바일에서 새 `요금 조정` 열과 대화상자가 넘치지 않는지. 기존 390px 검사는 `총량 조정` 열까지 기준이며, 요금 열이 추가되면서 가로 스크롽이 늘어난다.
- 영문 전환. 한국어 단일 언어다.
- 요금을 바꾼 뒤 고객 웹의 `GET /api/availability` 금액에 반영되는지. 고객 웹은 이 변경에 닫혀 있고, 응답의 `total`이 같은 `rate_day`에서 계산되는 것을 확인하지 않았다.
- 요금 변경이 진행 중인 예약 변경 요청의 견적에 미치는 영향. 견적은 요청 시점의 `rate_day`에서 계산한다.
- 두 번째 요금제 등록·수정, 일괄 업로드(CSV), 판매 중지 구간 설정은 이번 범위가 아니다.
