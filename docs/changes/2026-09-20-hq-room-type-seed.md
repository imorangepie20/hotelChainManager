# 본사 객실 유형 - 기본 요금제·일자 재고 시드

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.
> 구현 계획: [본사 지점·객실 유형 - 요금제·일자 재고 시드](../superpowers/plans/2026-09-20-hq-room-type-seed.md)

## 변경 요약

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`에서 남은 첫 항목인 "새 객실 유형의 초기 재고·요금 생성"을 구현했다.
- `RoomTypeCommandService.create`는 `room_type` 행만 만들었다. `rate_plan`·`rate_day`·`inventory_day`가 없으면 고객 `GET /api/availability`가 그 유형을 빼버려서, 본사가 추가한 객실 유형은 즉시 판매되지 않았다. 라이브 DB에서 검증용으로 만든 `스탠다드 트윈`이 이 상태(`plans=0`)였다.
- 이제 `POST /api/staff/hotels/{hotelId}/room-types`가 기본 요금제 1개와 `rate_day`·`inventory_day` 일자분을 객실 유형과 **같은 트랜잭션**에 심는다. 실패하면 유형 행도 롤백돼서 "유형은 있는데 가격·재고가 없어 예약 불가" 상태가 생기지 않는다.
- 본사가 객실 유형을 만든 직후 고객 가용성 응답에 그 유형이 잔여 객실·가격과 함께 나타난다.
- 시드 시작일은 지점 현지 시간대 기준 오늘이다. `Clock`이 UTC이고 지점이 `Asia/Seoul`이면 UTC 자정이 한국 시간 전일 09:00가 돼서 `LocalDate.now(clock)`가 하루 앞선다. `clock.withZone(hotelZone)`으로 보정했다.
- 멱원 재시도가 같은 일자를 두 번 만들지 않는다. `ON CONFLICT DO NOTHING`으로 막는다.
- `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401, 없는 지점은 404다. 이 부분은 기존 동작을 그대로 둔다.

## 서비스별 변경

### services/api

- `db/migration/V51__room_type_seed_defaults.sql`(신규): additive. `room_type`에 `seed_completed_at TIMESTAMPTZ`를 추가한다. V2가 만든 3종 시드는 `NULL`이고, 본사가 만든 유형만 시드 완료 시각이 들어간다. 기존 행·제약을 변경하지 않는다.
- `hotel/RoomTypeSeedProperties`(신규, `@ConfigurationProperties("hotel.seed")`): `defaultRateKrw`(기본 100,000)·`rateDays`(기본 90)·`inventoryCapacity`(기본 8). 생성자에서 0 이하를 거부한다.
- `hotel/HotelSeedConfiguration`(신규): `@EnableConfigurationProperties`로 속성을 등록한다.
- `hotel/RoomTypeCommandService`: `seedDefaults`가 요금제 1개(`기본 요금제`, `breakfast_included=false`, `policy_version=FLEX-2026-01`)와 일자별 요금·재고를 심는다. `hotelTimezone(hotelId)`이 지점 시간대를 읽어 시드 시작일을 보정한다. `loadExisting`이 시드 요약을 같이 돌려준다.
- `hotel/RoomTypeCreateResponse`: `SeedSummary`(`ratePlanId`·`ratePlanName`·`pricedDays`·`defaultRateKrw`·`inventoryCapacity`·`created`)와 `SeedBatch`·`RateDayRow`·`InventoryDayRow` 중첩 레코드를 추가했다.
- `application.yml`: `hotel.seed.*` 환경 변수(`HOTEL_SEED_DEFAULT_RATE_KRW`·`HOTEL_SEED_RATE_DAYS`·`HOTEL_SEED_INVENTORY_CAPACITY`) 기본값.
- `src/test/resources/application.yml`: 시드 속성의 테스트 기본값. 기본값이 없으면 리터럴 `${HOTEL_SEED_*}` 플레이스홀더가 빈 문자열로 바인딩돼서 컨텍스트 로딩이 실패한다.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `CreatedRoomTypeSeed` 타입을 추가하고 `CreatedRoomType`에 `seed`를 넣었다.
- `src/components/hotel-admin/hotel-catalog.tsx`: 생성 성공 시 시드 안내(`90일분`·`100,000원`·`8실`)를 `role="status"`로 보여준다. 멱원 재호출(`created=false`)이면 안내를 넘긴다.
- `e2e/hotel-catalog.spec.ts`: 시드 안내를 검증하는 시나리오를 추가하고 기존 POST 모킹 응답에 `seed` 본문을 넣었다.

### 설계 변경

- 계획은 시드 시작일을 "생성일(UTC) 자정"으로 쓴다고 했다. 실제로는 UTC 시계와 한국 시간대가 하루 어긋나서 오늘 도착 검색이 빈 결과를 돌려받는다는 걸 테스트가 잡아냈고, 지점 현지 시간대 기준 오늘로 바꿨다.
- 계획은 V51에 설정 표를 만들지 않고 `application.yml`만 쓴다고 했다. 그대로 따랐고, `seed_completed_at`만 추가했다.

## 검증

- API **전체 494건**(실패 0, 건너뜀 3) 종료 코드 0. 그중 시드 직접 15건: 생성 응답의 `pricedDays=90`·`defaultRateKrw=100000`·`inventoryCapacity=8`, 같은 키 재호출로 일자 중복 없음, 새 키 같은 내용으로 같은 `rate_plan_id`, 시드 시작일·종료일 범위, 시드 금액이 단일 값이고 `held`·`confirmed`가 0, 없는 지점 404, 지점 직원 403, 세션 없음 401.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 내 파일 종료 코드 0(경고 8건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `hotel-catalog` 9건(시드 안내 포함)·`policies` 11건·`staff-accounts` 10건, 인접 `audit`·`inventory-viewer`·`inventory-mobile`·`ai-operations`·`operations-report`·`admin-navigation` 33건 종료 코드 0.
- 라이브: compose `api` 재빌드 뒤 health 200. 본사 세션으로 `POST /api/staff/hotels/{hotelId}/room-types` 201 `seed.pricedDays=90 defaultRateKrw=100000 inventoryCapacity=8`, 같은 키 재호출 200 `created=false`로 같은 `roomTypeId`·같은 `ratePlanId`. 고객 `GET /api/availability`(오늘→+3일, 성인 4) 응답에 새 유형이 `remaining=8`·`total=300000`으로 포함됐다.
- 검증용 객실 유형은 `rate_day` 90건·`inventory_day` 90건·`rate_plan` 1건·`room_type_command` 1건·`room_type` 1건을 삭제해 라이브를 원래 4종으로 복원했다.
- 전체 Playwright 회귀는 177 passed, 종료 코드 1이다. 실패 1건은 이전 변경 기록에서 이미 알려진 toss SDK 로딩·세션 타이밍 영역이며 이번 변경과 무관하다.

## 미검증 항목

- Vite production build는 실행하지 않았다. `tsc --noEmit`과 Playwright 브라우저 검증으로 대체했다.
- 390px 모바일에서 시드 안내가 넘치지 않는지 확인하지 않았다. `break-words`를 넣었지만 브라우저 회귀에 포함시키지 않았다.
- 시드 금액·일수·재고 환경 변수를 0 이하로 설정했을 때 애플리케이션이 시작을 거부하는지 라이브에서 확인하지 않았다. 단위 속성 검증(생성자)만 믿는다.
- 주말·계절 가격, 두 번째 요금제, 일자별 요금 변경·판매 중지 구간은 범위 밖이다. 시드된 단일 금액을 덮어쓰는 동작은 1번 메뉴의 다음 항목이다.
- 영문 전환. 한국어 단일 언어다.
