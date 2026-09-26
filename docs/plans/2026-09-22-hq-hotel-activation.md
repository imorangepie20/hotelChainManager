# 본사 지점 관리 - 지점 판매 중지·재개 (2026-09-22)

> 범위: `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`의 남은 항목 중 "지점 판매 중지".
> 상태: 구현 완료. 작업 트리에만 있고 커밋하지 않은 상태.

## 변경 이유

본사가 리모델 공사나 브랜드 정비로 한 지점의 예약 접수를 멈출 수단이 없었다. `hotel` 표에 판매 상태 칼럼이 없었으므로, 점검 기간인 지점도 고객 `GET /api/hotels`·`GET /api/availability`에 계속 나타나서 고객이 예약을 시도하게 됐다. 임시 방편으로 재고를 0으로 내리는 것은 가능하지만, **재고 0은 "매진"이지 "판매하지 않는다"가 아니므로** 본사가 의도를 가지고 중지·재개할 수 있는 상태가 필요했다.

`2026-09-22-hq-hotel-update.md`의 미검증 항목 중 하나였다.

## 완료 기준

1. 중지한 지점이 고객 `GET /api/hotels`·`GET /api/availability`에서 빠진다.
2. **중지가 확정 예약을 취소하지 않는다.** 본사가 고객에게 알리지 않은 채 환불 의무가 생기는 것을 막는다.
3. 멱원이 두 갈래로 동작한다. 같은 키 재호출은 `changed=false`, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다.
4. 권한은 서버에 있다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다.
5. 직원은 중지한 지점도 본다. 다시 판매하려면 상태를 알아야 하므로 고객 화면과 다르다.
6. additive 마이그레이션이 기존 행의 동작을 변경하지 않는다.

## 설계

### 마이그레이션

V60 `V60__hotel_active.sql`이 `hotel.active BOOLEAN NOT NULL DEFAULT TRUE`를 추가한다. 기존 3개 지점은 모두 활성이므로 기존 동작을 변경하지 않는다.

### 서버

`PATCH /api/staff/hotels/{hotelId}/active` (본문 `{active: boolean}`):
- `HotelActivationService.setActive`가 `access.requireHeadquarters(token)`로 본사만 허용한다.
- `hotel` 행을 `select ... for update`로 잡아 동시 전환을 직렬화한다.
- `hotel_command`(`kind='ACTIVATE'`)에서 `(hotel_id, staff_id, kind, idempotency_key)`로 같은 키 재호출을, `(hotel_id, staff_id, kind, request_hash)`로 같은 내용의 다른 키 재시도를 찾는다. V59의 `kind` 설계를 그대로 쓴다.
- **이미 같은 상태면 DB를 건드리지 않고 멱원 기록만 남긴다.** 재시도가 200으로 같은 결과를 돌려주게 한다.
- 응답은 `HotelActivationResponse(hotelId, name, region, timezone, active, changed)`다.

고객 화면:
- `HotelController.list()` (고객 `GET /api/hotels`)가 `where active`로 걸러준다.
- `AvailabilityService`가 지점을 조회해 `active`가 아니면 빈 오퍼를 내려준다. 예약할 수 없다는 것이지 오류가 아니다.

### 관리자 UI

`호텔 및 객실` (`/dashboard/hotels`)의 지점 표에 `판매` 열과 `판매 중지`·`판매 재개` 버튼을 추가한다. `getStaffHotels` (`GET /api/staff/hotels`)는 중지한 지점도 내려주므로 본사는 상태를 본다.

### 명시하지 않은 동작

- **중지는 신규 판매에만 적용한다.** 확정 예약·진행 중인 변경 요청은 그대로 둔다.
- **중지한 지점의 객실 유형에 본사가 여전히 요금·재고를 바꿀 수 있다.** 본사는 점검 기간에도 가격·재고를 정비해야 하므로다. 중지가 쓰기 권한에 영향을 주지 않는다.
- **판매 재개는 재고를 다시 심지 않는다.** 중지는 `inventory_day`·`rate_day`를 건드리지 않으므로 재개하면 중지 전과 같은 재고가 돌아온다.

## 검증 계획

- `mvnw -Dtest=HotelActivationIntegrationTest` — 중지·재개·고객 화면에서 빠짐·빈 오퍼·멱원 두 갈래·no-op·확정 예약 보존·403·401·400·404·직원 목록 포함.
- 인접 suite: 객실 유형·재고·요금·고객 요청·감사·운영 통계·직원 계정·지점 생성·지점 수정.
- 관리자 `tsc --noEmit`·`eslint`·Playwright `hotel-catalog`·인접 6개 suite.
- **라이브 DB·compose 재빌드·브라우저 확인은 이번 범위에서 실행하지 않는다.** V60이 라이브에 적용되지 않았으므로 실제 흐름은 사용자가 확인한다.

## 다음 작업

- `admin-menu-roadmap.md` 1번 남은 항목: **객실 유형 삭제**. `reservation`·`reservation_change_request`·`website_page_room_type` 외래키가 걸려 있어 안전한 삭제 조건을 따로 설계해야 한다.
- 2번 남은 항목: **판매 중지 구간 설정**, **재고 일괄 업로드(CSV)**.
