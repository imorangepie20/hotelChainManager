# 본사 지점·객실 유형 - 요금제·일자 재고 시드

> 상태: 구현·검증 완료. 최종 갱신: 2026-09-20. [변경 기록](../../changes/2026-09-20-hq-room-type-seed.md)

## 배경

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`에서 읽기 전용 카탈로그와 객실 유형 추가(`POST /api/staff/hotels/{hotelId}/room-types`)는 끝났다. 로드맵이 남은 첫 항목은 "새 객실 유형의 초기 재고·요금 생성"이다.
- `RoomTypeCommandService.create`는 `room_type` 행만 만든다. 같은 호출이 `rate_plan`·`rate_day`·`inventory_day`를 만들지 않으므로, 본사가 추가한 객실 유형은 즉시 예약 가능한 상태가 되지 못한다. 실제 라이브 DB에서 2026-09-19 검증 때 만든 `스탠다드 트윈`이 이 상태다 — `plans=0`, 가격 범위·재고 없음.
- 기존 3종 시드는 요금제마다 90일분의 `rate_day`와 90일분의 `inventory_day`를 갖추고 있다. 본사가 만든 유형도 같은 규칙을 따라야 고객 검색·예약 흐름이 일관되게 동작한다.
- 객실 유형 생성은 이미 두 갈래 멱원(`Idempotency-Key` 재호출, `SHA-256` 요청 지문)과 지점 행 `for update` 직렬화를 갖췄다. 시드도 같은 트랜잭션에서 같은 패턴으로 동작해야 한다.

## 완료 기준

1. `POST /api/staff/hotels/{hotelId}/room-types`가 `room_type`과 함께 기본 요금제 1개, `rate_days` 일자별 요금, `inventory_day` 일자 재고를 한 트랜잭션에 만든다.
2. 본사가 새 유형을 만든 직후 고객 `GET /api/availability` 응답에 그 유형이 잔여 객실과 가격과 함께 나타난다.
3. 재시도 안전성: 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과(같은 유형·같은 요금제·같은 일자·같은 금액)를 돌려주고, 새 키로 같은 내용을 보내도 같은 결과를 돌려준다. 중복 행·중복 일자·중복 가격이 생기지 않는다.
4. 기본 요금 금액은 고정 상수가 아니라 환경 변수(`hotel.seed.default-rate-krw`, 기본 100,000)·일수(`hotel.seed.rate-days`, 기본 90)·재고(`hotel.seed.inventory-capacity`, 기본 8)로 조정 가능하다. 0 이하 값을 거부한다.
5. 일자별 가격은 주말(금·토 도착)과 주중을 구분하지 않고 단일 금액을 쓴다. 첫 버전에서는 본사가 일자별 요금 변경 UI(1번 메뉴의 다음 항목)에서 조정한다.
6. 기존 3종 유형의 `rate_plan`·`rate_day`·`inventory_day`와 충돌하지 않는다. V51은 additive하고 기존 표의 행·제약을 변경하지 않는다.
7. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401, 없는 지점은 404다. 이 부분은 기존 동작을 그대로 둔다.
8. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다.

## 설계 결정

### 무엇을 시드할 것인가

- 요금제 1개. 이름 `기본 요금제`, `breakfast_included=false`, `policy_version=FLEX-2026-01`로 기존 3종과 같은 정책 버전을 쓴다. 정책 버전이 다르면 `ReservationService`의 `policy_snapshot` 비교·예약 변경 견적이 의도치 않게 분기한다.
- `rate_day`는 **지점 현지 시간대 기준 오늘**부터 `N`일(`hotel.seed.rate-days`, 기본 90) 치. 고객 검색이 체크인일을 오늘부터 잡으므로 과거 일자는 의미가 없다. `Clock`이 UTC이고 지점이 `Asia/Seoul`이면 UTC 자정이 한국 시간 전일 09:00가 돼서 `LocalDate.now(clock)`가 하루 앞선다. `clock.withZone(hotelZone)`으로 보정해야 오늘 도착 검색이 빈 결과를 돌려받지 않는다.
- `inventory_day`도 같은 기간, 같은 일수. `capacity=8`(`hotel.seed.inventory-capacity`), `held=0`, `confirmed=0`으로 시작한다. `inventory_day_check`(`held + confirmed <= capacity`)가 위반되지 않는다.
- 주말 가격·계절 가격은 넣지 않는다. 본사가 일자별 요금 변경(1번 메뉴의 다음 항목)으로 덮어쓰는 동선만 둔다.

### 중복 방지

- `rate_day`·`inventory_day`는 PK가 `(rate_plan_id, stay_date)`·`(room_type_id, stay_date)`다. `INSERT ... ON CONFLICT DO NOTHING`으로 멱원 재시도가 두 번째 일자를 만들지 않게 한다.
- 재시도가 다른 금액으로 들어오면? 설계상 허용하지 않는다. 같은 `Idempotency-Key`면 `created=false`로 기존 결과를 돌려주고, 새 키 + 같은 유형 이름·인원이면 `request_hash`가 같아 기존 결과를 돌려준다. 금액은 첫 생성 시점의 환경 변수 값을 따르고, 이후 환경 변수를 바꿔도 이미 만든 유형에 영향을 주지 않는다. 이것이 시드가 갖는 "첫 값" 의미다.

### 트랜잭션과 잠금

- 이미 `@Transactional`이고 `hotel` 행 `for update`로 직렬화된다. 시드 insert는 같은 트랜잭션 안에서 실행되므로 실패하면 객실 유형 행도 롤백된다. 부분 생성(유형만 있고 가격 없음)이 다시 생기지 않는다.
- `room_type`에만 의존하는 `physical_room`은 시드 범위 밖이다. 기존 시드도 실제 객실 없이 재고만 가지고 있으므로 같은 규칙을 따른다.

## 구현 범위

### services/api

- `db/migration/V51__room_type_seed_defaults.sql`(신규): additive. 시드 동작에 필요한 설정을 담는 표는 만들지 않는다 — `application.yml`만으로 충분하다. 대신 `room_type`의 `seed_completed_at TIMESTAMPTZ`를 추가해 시드가 끝난 유형을 구분한다. 기존 행은 전부 `NULL`이고, V2 시드 유형은 `NULL`인 채로 둔다(이미 시드됐으므로). UNIQUE 인덱스는 추가하지 않는다.
- `hotel/RoomTypeCommandService.create`: 객실 유형 insert 뒤 `seedPlan`·`seedRateDays`·`seedInventoryDays`를 호출한다.
- `hotel/RoomTypeCreateResponse`: `ratePlanId`·`pricedDays`·`defaultRateKrw`·`inventoryCapacity`·`seedDays`를 추가한다. UI가 "가격·재고 없음"과 "시드 완료"를 구분할 수 있게 한다.
- `hotel/RoomTypeSeedProperties`(신규, `@ConfigurationProperties("hotel.seed")`): `defaultRateKrw`·`rateDays`·`inventoryCapacity`. 생성자에서 0 이하를 거부한다.
- `hotel/HotelCatalogQueryService`: 응답에 시드 상태(`seeded`·`seedDays`·`defaultRateKrw`)를 노출한다. 카탈로그가 "요금 없음"을 그대로 보여주되, 본사가 시드 여부를 알 수 있게 한다.
- `application.yml`: `hotel.seed.default-rate-krw`·`rate-days`·`inventory-capacity` 기본값.

### SDTPL_ADM

- `components/hotel-admin/hotel-catalog.tsx`: 객실 유형 카드에 요금 일수·기본 금액·재고 시드 상태 표시. 요금이 없는 유형은 "가격·재고 없음, 예약 불가" 안내.
- `e2e/hotel-catalog.spec.ts`: 시드가 만들어진 유형의 카드가 기본 금액·90일 범위를 보이는지 확인. 추가 대화상자는 그대로 둔다.

### 테스트

- `RoomTypeCommandIntegrationTest` 확장: 생성 응답의 `pricedDays=90`·`defaultRateKrw`, 같은 키 재호출으로 일자 중복 없음, 새 키 같은 내용으로 같은 `rate_plan_id`, 음수·0 설정값 거부, 고객 가용성 응답에 새 유형 포함.
- 기존 3종 시드 유형의 `rate_day`·`inventory_day` 행 수가 변하지 않는 회귀 확인.

## 미구현 예정 항목

- 일자별 요금 변경·판매 중지 구간(1번 메뉴의 다음 항목). 시드된 단일 금앹을 덮어쓰는 동작만 남긴다.
- 지점 생성·수정·판매 중지. 1번 메뉴의 마지막 항목이다.
- 요금제 추가(두 번째 요금제·조식 포함 여부). 첫 버전은 요금제 1개로 충분하다.
- 주말·계절 가격, IDEX·BAR 같은 동적 가격 전략. 핵심 원칙에서 매출 최적화가 제외 대상이다.
- 영문 전환. 한국어 단일 언어다.

