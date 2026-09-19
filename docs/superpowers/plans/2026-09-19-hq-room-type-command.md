# 본사 지점·객실 유형 관리 쓰기 동작

> 상태: 구현 완료. 최종 갱신: 2026-09-19.

## 배경

- `admin-menu-roadmap.md` 1번의 읽기 전용 카탈로그는 완료됐다. 이제 같은 메뉴의 쓰기 동작을 붙인다.
- 쓰기는 가격·재고·예약 확정에 직접 닿는다. 핵심 원칙대로 권한은 Spring Boot에 두고 관리자 UI는 서버가 검증한 결과만 표시한다.
- 전체 범위(지점 생성·수정·판매 중지, 객실 유형·요금제 등록·수정, 일자별 요금 변경) 중 **객실 유형 추가**만 먼저 한다. 범위를 최소로 잡고 재고·가격 변경으로 번지는 위험을 줄인다.

## 완료 기준

1. `POST /api/staff/hotels/{hotelId}/room-types`가 본사 세션으로 이름·최대 인원을 받아 객실 유형을 만든다. **완료.**
2. `HQ_ADMIN`만 호출 가능. 지점 직원 403, 잘못된 세션 401, 없는 지점 404. **완료.**
3. 이름은 1~100자, 최대 인원은 1 이상 20 이하. 위반은 400. **완료.**
4. 멱원 키(`Idempotency-Key` 헤더)로 같은 요청의 중복 생성을 막는다. **완료. 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 객실 유형을 돌려준다.**
5. 관리자 4001 `/dashboard/hotels`의 `호텔 및 객실` 화면에서 객실 유형 추가 대화상자로 이름·최대 인원을 입력한다. **완료.**
6. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다. **완료.**

## 구현 범위

### 서버 (services/api)

- `hotel/RoomTypeCommandService`: `create(token, hotelId, idempotencyKey, request)`. `requireHeadquarters`로 본사만 허용하고, `hotel` 행을 `for update`로 잡아 지점 단위 쓰기를 직렬화한다.
- 멱원은 두 갈래로 확인한다. 같은 `idempotency_key`가 있으면 저장된 객실 유형을 그대로 돌려준다. 키는 달라도 `staff_id`·`hotel_id`·본문 해시가 같으면 같은 결과를 돌려준다. 지문은 SHA-256이다.
- `hotel/HotelCatalogController`에 POST를 추가했다. 생성은 201, 멱원 재호출은 200에 `created=false`를 붙였다. 기존 GET과 같은 권한·지점 확인 순서를 쓴다.
- `RoomTypeCreateRequest`가 이름·인원 검증을 담당한다. 공백 이름, 100자 초과, 1~20 범위 밖 인원은 400이다.
- V46 `room_type_command` 테이블이 멱원 키와 지문을 보관한다.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`에 `createRoomType`·`CreateRoomTypeRequest`·`CreatedRoomType` 타입을 추가했다. 403·409·기타 실패 안내를 나눴다.
- `components/hotel-admin/hotel-catalog.tsx`에 객실 유형 추가 버튼과 대화상자를 추가했다. `HQ_ADMIN`만 버튼이 보이고, 성공하면 카탈로그를 새로고침한다. 실패하면 대화상자를 닫지 않고 서버 안내를 보여준다.
- 객실 유형 이름·최대 인원 입력 검증은 서버가 최종 판단한다. UI는 보조 안내만 한다.
- 표 행에 `data-room-type-id`를 넣어 라이브 검증에서 생성된 객실 유형을 식별할 수 있게 했다.

### 테스트

- `RoomTypeCommandIntegrationTest` 11종: 본사 201, 지점 403, 잘못된 세션 401, 없는 지점 404, 공백 이름 400, 101자 이름 400, 인원 0·21 400, 멱원 키 누락 400, 같은 키 재호출 같은 ID, 다른 키 같은 내용 재호출 같은 ID, 다른 직원은 별도 생성.
- `e2e/hotel-catalog.spec.ts` 3종 추가: 생성·새로고침, 서버 검증 실패 시 대화상자 유지, 지점 직원에게 버튼 미노출.

## 검증 결과

- API `mvnw -Dtest=RoomTypeCommandIntegrationTest,HotelCatalogQueryIntegrationTest,InventoryQueryIntegrationTest test` **27건** 종료 코드 0.
- API `mvnw compile`·`test-compile` 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `hotel-catalog` **8건**, `inventory-mobile` 1건, `inventory-viewer` 6건, `admin-navigation` 2건 종료 코드 0.
- 라이브: API 재빌드 뒤 V46 `success=t`, health `UP`. 본사 세션으로 생성 201, 같은 키 재호출 200 같은 ID, 새 키 같은 내용 200 같은 ID, 공백·0인·21인·키 누락 400, 없는 지점 404, 지점 직원 403, 세션 없음 401. 관리자 4001 브라우저에서 `객실 유형 3종` → 추가 → `4종` 전환과 새로고침 후 4종 유지를 확인했다. 검증용 객실 유형과 멱원 기록은 모두 삭제해 원래 3종으로 복원했다.

## 미구현 예정 항목

- 지점 생성·수정·판매 중지. 지점 자체를 다루는 동작은 별도 작업이다.
- 객실 유형 수정·삭제, 요금제 등록·수정, 일자별 요금 변경. 요금 변경은 2번 메뉴와 겹친다.
- 재고(`inventory_day`) 조정·판매 중지 구간 설정. 2번 메뉴의 쓰기 범위다.
- 새 객실 유형에 대한 초기 재고·요금 생성. 객실 유형만 만들고 `inventory_day`·`rate_plan`은 채우지 않으므로 예약 대상은 아니다.
- 영문 전환. 한국어 단일 언어다.
