# 본사 객실 유형 추가

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-19.

## 변경 이유

- `admin-menu-roadmap.md` 1번 `지점·객실 유형`은 읽기 전용 카탈로그만 구현돼 있었다. 본사가 객실 유형을 추가할 수단이 없어서 카탈로그를 봐도 늘어나지 않았다.
- 전체 쓰기 범위(지점 생성·수정·판매 중지, 객실 유형·요금제 등록·수정, 일자별 요금 변경) 중 **객실 유형 추가**만 먼저 했다. 재고·가격으로 번지는 위험을 줄이기 위해 범위를 최소로 잡았다.
- 이 동작은 가격·재고·예약 확정에 닿는 첫 쓰기 API다. 핵심 원칙대로 권한은 Spring Boot에 두고 관리자 UI는 서버가 검증한 결과만 표시한다.

## 구현 범위

### 서버 (services/api)

- `hotel/RoomTypeCreateRequest`: 이름 1~100자, 최대 인원 1~20을 검증한다. 공백·초과·범위 밖은 `IllegalArgumentException`으로 400이 된다.
- `hotel/RoomTypeCreateResponse`: `roomTypeId`·`hotelId`·`name`·`maxOccupancy`·`created`. 멱원 재호출은 `created=false`.
- `hotel/RoomTypeCommandService`: `create(token, hotelId, idempotencyKey, request)`. `requireHeadquarters`로 본사만 허용하고 `hotel` 행을 `for update`로 잡아 지점 단위 쓰기를 직렬화한다.
  - 멱원 확인 순서: 같은 `idempotency_key` → 같은 `request_hash`(직원·지점·본문 SHA-256). 둘 중 하나가 같으면 저장된 객실 유형을 다시 읽어 돌려준다.
  - `Idempotency-Key`가 없거나 100자를 넘으면 400이다.
- `hotel/HotelCatalogController`: POST `/{hotelId}/room-types`를 추가했다. 생성은 201, 멱원 재호출은 200. 기존 GET과 같은 권한·지점 확인 순서를 쓴다.
- `V46__room_type_command_idempotency.sql`: `room_type_command` 테이블. `(hotel_id, staff_id, idempotency_key)` UNIQUE, 지문·객실 유형 인덱스. additive이고 기존 테이블을 변경하지 않는다.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`의 `createRoomType`·`CreateRoomTypeRequest`·`CreatedRoomType`. 403·409·기타 실패 안내를 나눴다.
- `components/hotel-admin/hotel-catalog.tsx`: 객실 유형 추가 버튼과 대화상자. `HQ_ADMIN`만 버튼이 보인다. 성공하면 카탈로그를 새로고침하고, 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다. 매 요청마다 새 멱원 키를 발급해 중복 생성을 막는다.
- 표 행에 `data-room-type-id`를 넣어 라이브 검증에서 생성된 객실 유형을 식별할 수 있게 했다.

### 테스트

- `RoomTypeCommandIntegrationTest` 11종: 본사 201, 지점 403, 잘못된 세션 401, 없는 지점 404, 공백 이름 400, 101자 이름 400, 인원 0·21 400, 멱원 키 누락 400, 같은 키 재호출 같은 ID, 다른 키 같은 내용 재호출 같은 ID, 다른 직원은 별도 생성.
- `e2e/hotel-catalog.spec.ts` 3종 추가: 생성·새로고침, 서버 검증 실패 시 대화상자 유지, 지점 직원에게 버튼 미노출.

## 자동 검증

- API `mvnw -Dtest=RoomTypeCommandIntegrationTest,HotelCatalogQueryIntegrationTest,InventoryQueryIntegrationTest test` **27건** 종료 코드 0.
- API `mvnw compile`·`test-compile` 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 `hotel-catalog.tsx`와 같은 패턴).
- Playwright: `hotel-catalog` 8건(기존 5 + 추가 3), `inventory-mobile` 1건, `inventory-viewer` 6건, `admin-navigation` 2건 **17건** 종료 코드 0.
- 라이브: API 이미지 재빌드 뒤 V46 `success=t`, health `UP`. 본사 세션으로 생성 201, 같은 키 재호출 200 같은 ID, 새 키 같은 내용 200 같은 ID, 공백·0인·21인·키 누락 400, 없는 지점 404, 지점 직원 403, 세션 없음 401. 관리자 4001 브라우저에서 본사 로그인 뒤 `객실 유형 3종` → 추가 → `4종` 전환과 새로고침 후 4종 유지를 확인했다.
- 검증용 객실 유형 4건과 멱원 기록 4건을 모두 삭제해 속초 객실 유형을 원래 3종으로 복원했다. `room_type_command` 행 수 0.

## 미검증 항목

- 객실 유형 수정·삭제. 아직 API가 없다.
- 요금제 등록·수정, 일자별 요금 변경. 2번 메뉴의 쓰기 범위와 겹친다.
- 새 객실 유형의 초기 재고·요금 생성. 객실 유형만 만들고 `inventory_day`·`rate_plan`은 채우지 않으므로 예약·검색 대상이 아니다.
- 지점 생성·수정·판매 중지. 지점 자체를 다루는 동작은 별도 작업이다.
- 동시 요청의 실제 경합(두 본사 관리자가 동시에 같은 내용을 보낼 때 한 쪽만 201). 지점 행 잠금으로 직렬화하지만 부하 상황에서의 측정은 하지 않았다.
- 영문 전환. 메뉴·추가 대화상자·검증 안내는 한국어 단일 언어다.
