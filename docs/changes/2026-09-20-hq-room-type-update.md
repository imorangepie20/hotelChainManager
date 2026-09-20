# 본사 객실 유형 - 이름·최대 인원 수정

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.
> 구현 계획: [본사 객실 유형 - 이름·최대 인원 수정](../superpowers/plans/2026-09-20-hq-room-type-update.md)

## 변경 요약

- `admin-menu-roadmap.md` 1번 `지점·객실 유형 관리`에서 남은 다음 항목인 "객실 유형 수정·삭제" 중 수정을 구현했다.
- 본사가 객실 유형 이름이나 최대 인원을 바꿀 수단이 전혀 없었다. 시드가 만든 `기본 요금제` 같은 오타나, 리모델 후 실제 기준(스탠다드 2인 → 3인)을 반영하려면 DB를 직접 만져야 했다.
- `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 이름·최대 인원을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다.
- **최대 인원을 내릴 때는 서버가 강하게 검증한다.** 확정 예약 중 새 인원을 초과하는 예약이 있으면 409 `ROOM_TYPE_OCCUPANCY_CONFLICT`로 거부하고 값을 바꾸지 않는다. `max_occupancy`가 예약 가능 조건(`max_occupancy * rooms >= partySize`)에 직결되기 때문이다. 올리는 것은 제한 없이 허용한다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 결과를 돌려준다.
- 삭제는 이번 범위에서 뺐다. `reservation`·`reservation_change_request`·`website_page_room_type`의 외래키가 걸려 있어, 안전한 삭제 조건을 따로 설계해야 한다.

## 서비스별 변경

### services/api

- `db/migration/V52__room_type_update_idempotency.sql`(신규): additive. `room_type_command`에 `kind VARCHAR(32) NOT NULL DEFAULT 'CREATE'`·`previous_name`·`previous_max_occupancy`를 추가하고 `(hotel_id, staff_id, kind, idempotency_key)` UNIQUE 인덱스를 만든다. 기존 행은 모두 `kind='CREATE'`이므로 영향이 없다.
- `hotel/RoomTypeUpdateRequest`(신규): `name`·`maxOccupancy`, `validate()`. 이름 1~100자, 인원 1~20.
- `hotel/RoomTypeUpdateResponse`(신규): `roomTypeId`·`hotelId`·`name`·`maxOccupancy`·`created`.
- `hotel/RoomTypeOccupancyConflictException`(신규): 409로 매핑.
- `hotel/RoomTypeUpdateService`(신규): 본사 권한·멱원 키·객체 존재 확인 뒤 최대 인원 내림 충돌 검사, `room_type` 행 `for update`, `room_type_command` 기록.
- `hotel/HotelCatalogController`: `PATCH` 핸들러 추가.
- `web/ApiExceptionHandler`: `ROOM_TYPE_OCCUPANCY_CONFLICT` 409 매핑 추가.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `updateRoomType`·`UpdateRoomTypeInput`·`UpdatedRoomType` 타입. 403·404·409 안내 분리.
- `src/components/hotel-admin/hotel-catalog.tsx`: 객실 유형 표에 수정 열·버튼, 수정 대화상자(이름·최대 인원). 서버 검증 실패 시 대화상자를 닫지 않는다. 인원 입력은 `type="text"` + `inputMode="numeric"`으로 서버 검증 메시지가 도달하게 한다.
- `e2e/hotel-catalog.spec.ts` 4종 추가: 수정·반영, 충돌 시 대화상자 유지, 지점 직원에게 수정 버튼 미노출. 기존 `getByRole("cell")` 단정이 수정 버튼 셀과 충돌해 `exact: true`를 붙였다.

### 설계 변경

- 계획은 진행 중 상태를 `PENDING_PAYMENT`·`CONFIRMED`로 나눴다. 실제 DB를 확인하니 임시 확보 만료 전용 `PENDING_PAYMENT` 상태는 더 이상 쓰지 않고, 진행 중 상태는 `CONFIRMED` 하나뿐이다. 충돌 검사를 `CONFIRMED` 하나로 단순화했다.
- 계획은 `room_type_command`의 `kind` CHECK에 `UPDATE`를 "추가"한다고 했다. 실제로는 `kind` 컬럼 자체가 아직 없어서(V46이 UNIQUE 제약만 만들었다) V52에서 컬럼 추가부터 했다.

## 검증

- API **전체 500건**(실패 0, 건너뜀 3) 종료 코드 0. 그중 수정 직접 21건: 수정 200, 같은 키 재호출 200 `created=false`, 새 키 같은 내용 200, 수정 명령 1건만 존재, 공백·0인·키 누락 400, 지점 직원 403, 세션 없음 401, 없는 지점·다른 지점·없는 유형 404, 확정 예약 충돌 409 + 값 미변경, 올리기 200, 카탈로그 반영.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 내 파일 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `hotel-catalog` **12건** 종료 코드 0.
- 라이브: compose `api` 재빌드 뒤 health 200. 본사 세션으로 수정 200 `created=true`, 같은 키 재호출 200 `created=false`, 새 키 같은 내용 200 `created=false`, 확정 예약이 있는 유형의 인원을 1로 내리면 409. 검증용 수정 명령 2건은 삭제했고 바뀐 인원도 원래값으로 복원해 라이브가 원래 4종(스탠다드 시티 2·오션 스위트 3·스탠다드 트윈 2·패밀리 스위트 4)을 유지한다.

## 미검증 항목

- Vite production build는 실행하지 않았다. `tsc --noEmit`과 Playwright 브라우저 검증으로 대체했다.
- 관리자 4001 브라우저에서 수정 대화상자를 직접 열어보지는 않았다. Playwright 라우트 모킹으로 동작만 검증했다.
- 전체 Playwright 회귀는 이번 세션에 실행하지 않았다. `hotel-catalog` suite와 API 전체 회귀로 범위를 대신했다.
- 객실 유형 삭제, 요금제 등록·수정, 일자별 요금 변경, 지점 생성·수정·판매 중지는 범위 밖이다.
- `max_occupancy` 내림이 예약 변경 요청(`reservation_change_request`)에 미치는 영향. 예약 변경은 이번 범위 밖이다.
- 영문 전환. 한국어 단일 언어다.
