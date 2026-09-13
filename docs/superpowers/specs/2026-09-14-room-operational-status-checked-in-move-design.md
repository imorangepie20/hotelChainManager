# 실제 객실 점검·판매 중지와 투숙 중 객실 이동 설계

상태: 사용자 승인 완료, 구현 계획 작성 전.

## 목적

지점 직원이 실제 객실의 점검 필요와 판매 중지를 청소 상태와 분리해 관리하고, 체크인한 고객을 같은 객실 유형의 안전한 객실로 옮길 수 있게 한다. 객실 유형별 일자 판매 재고와 실제 객실 번호의 책임은 계속 분리한다.

## 범위

- 실제 객실의 현재 운영 상태, 사유와 선택적 예상 복구 시각을 관리한다.
- 점검 필요·판매 중지 객실을 신규 배정과 배정 변경 후보에서 제외한다.
- 판매 중지 전에 현재 투숙 및 미래 확정 배정의 영향을 보여주고, 영향 배정이 있으면 상태 전환을 차단한다.
- `CHECKED_IN` 예약의 배정 객실 한 실을 같은 호텔·같은 객실 유형의 청결하고 운영 가능한 객실로 이동한다.
- 투숙 중 이동 직후 기존 객실을 청소 필요이면서 점검 필요한 상태로 전환한다.
- 모든 상태 전환과 객실 이동을 담당 직원·사유·시각과 함께 감사한다.

## 상태 모델

V7의 `physical_room.housekeeping_status`는 현재 `CLEAN`, `NEEDS_CLEANING`, `OUT_OF_SERVICE`를 한 컬럼에서 허용한다. 신규 migration은 이를 청소 상태와 운영 상태로 분리하며, 이후 `housekeeping_status`는 `CLEAN` 또는 `NEEDS_CLEANING`만 나타낸다. 별도 `operational_status`는 다음 값만 허용한다.

| 상태 | 의미 | 신규 배정 | 허용 조건 |
|---|---|---:|---|
| `AVAILABLE` | 운영 가능 | 가능 | `housekeeping_status=CLEAN`일 때만 복구 가능 |
| `INSPECTION_REQUIRED` | 점검 필요 | 불가 | 투숙 중에도 설정 가능 |
| `OUT_OF_SERVICE` | 판매 중지 | 불가 | 현재 투숙 또는 미래 확정 배정이 없어야 함 |

신규 migration은 기존 `housekeeping_status='OUT_OF_SERVICE'` 행을 `operational_status='OUT_OF_SERVICE'`, `housekeeping_status='NEEDS_CLEANING'`으로 보수적으로 이관하고 나머지 객실은 `AVAILABLE`로 초기화한다. 모든 객실의 운영 version은 `0`에서 시작한다. 운영 불가 상태의 사유는 필수이며, 이관 행은 `기존 판매 중지 상태 이관`을 사용한다. 예상 복구 시각은 선택이다. `AVAILABLE`로 복구하면 현재 사유와 예상 복구 시각을 비우되 감사 이력에는 이전 값을 보존한다.

청소 완료는 청소 상태만 `CLEAN`으로 바꾸며 운영 상태를 자동 복구하지 않는다. 점검 완료 또는 판매 재개는 직원이 명시적으로 실행한다. 객실 유형별 `inventory_night`는 실제 객실 상태 변경으로 자동 증감하지 않는다.

## 데이터 구조

`physical_room`에 다음 필드를 additive하게 추가한다.

- `operational_status`: `AVAILABLE`, `INSPECTION_REQUIRED`, `OUT_OF_SERVICE`
- `operational_reason`: 운영 불가 사유
- `expected_recovery_at`: 선택적 예상 복구 시각
- `operational_version`: 낙관적 동시성 version
- `operational_updated_at`: 최근 변경 시각

`physical_room_operational_event`는 객실, 이전·새 운영 상태, 사유, 예상 복구 시각, 직원, 멱등 키, 요청 지문과 생성 시각을 append-only로 저장한다. 객실과 멱등 키 조합은 유일하다.

`checked_in_room_move`는 예약, 기존·신규 실제 객실과 당시 객실 번호, 사유, 직원, 멱등 키, 요청 지문과 생성 시각을 저장한다. 예약과 멱등 키 조합은 유일하다. 현재 배정은 기존 `reservation_room_assignment`를 갱신하며 과거 이동은 감사 테이블에서 조회한다.

## 권한

- `BRANCH_STAFF`는 자기 지점 객실과 예약만 조회·변경한다.
- `HQ_ADMIN`은 모든 지점을 조회·변경한다.
- 별도 본사 승인은 두지 않는다.
- 서버가 세션, 지점, 예약 상태, 객실 유형과 현재 배정을 검증한다. UI의 비활성화는 권한 검증을 대신하지 않는다.

## API

### 객실 운영 조회

`GET /api/staff/hotels/{hotelId}/room-operations`

객실 번호·객실 유형, 청소 상태, 운영 상태와 version, 사유, 예상 복구 시각, 영향 배정 요약과 최근 운영 이벤트를 반환한다. 영향 배정은 현재 `CHECKED_IN` 또는 체크아웃이 지나지 않은 미래 `CONFIRMED` 예약만 포함한다.

### 객실 운영 상태 전환

`POST /api/staff/rooms/{roomId}/operational-transitions`

`Idempotency-Key` 헤더와 다음 body를 받는다.

- `targetStatus`
- `reason`
- `expectedRecoveryAt`
- `expectedVersion`

서버는 객실 행을 잠그고 version과 지점 권한을 확인한다. `OUT_OF_SERVICE`는 영향 배정이 있으면 `ROOM_HAS_ACTIVE_ASSIGNMENTS`로 거부하며 최신 영향 목록을 오류 응답에 포함한다. `AVAILABLE`은 청소 상태가 `CLEAN`이 아니면 `ROOM_NOT_CLEAN`으로 거부한다. 같은 직원·객실·키·요청의 재호출은 최초 결과를 반환하고 같은 키의 다른 요청은 `IDEMPOTENCY_CONFLICT`로 거부한다.

### 투숙 중 객실 이동 후보

`GET /api/staff/reservations/{reservationId}/checked-in-room-move-options`

현재 배정 객실과 같은 호텔·객실 유형이며 `CLEAN + AVAILABLE`이고 다른 활성 예약과 숙박 기간이 겹치지 않는 후보만 반환한다.

### 투숙 중 객실 이동

`POST /api/staff/reservations/{reservationId}/checked-in-room-moves`

`Idempotency-Key` 헤더와 `currentPhysicalRoomId`, `newPhysicalRoomId`, `reason`을 받는다. 사유는 필수다.

## 트랜잭션과 경합

객실 운영 상태 전환은 대상 객실 행을 먼저 잠근다. 판매 중지 영향 조회는 잠금 안에서 다시 실행한다. 기존 배정·재배정 서비스도 객실을 잠근 뒤 `operational_status=AVAILABLE`을 재검증하므로 상태 전환과 새 배정이 경합해도 둘 다 성공하지 않는다.

투숙 중 이동은 예약 행을 잠근 뒤 두 객실을 UUID 순으로 잠근다. 다음 조건을 잠금 안에서 모두 재검증한다.

- 예약 상태가 `CHECKED_IN`이다.
- 기존 객실이 현재 예약에 배정돼 있다.
- 신규 객실은 같은 지점·객실 유형이며 현재 배정이 아니다.
- 신규 객실은 `CLEAN + AVAILABLE`이고 다른 활성 예약과 충돌하지 않는다.
- 같은 예약에서 동시에 수행되는 다른 이동이 현재 배정을 바꾸지 않았다.

성공하면 한 트랜잭션에서 현재 배정을 신규 객실로 바꾸고, 기존 객실을 `NEEDS_CLEANING + INSPECTION_REQUIRED`로 전환하며 운영 이벤트와 이동 감사를 기록한다. 어느 단계든 실패하면 모두 롤백한다. 이후 체크아웃은 현재 배정인 신규 객실만 `NEEDS_CLEANING`으로 전환한다.

## 관리자 UI

새 최상위 메뉴를 만들지 않고 `/dashboard/operations`의 오늘의 운영 화면에 객실 운영 영역을 추가한다.

- 점검 필요, 판매 중지, 예상 복구 시각 경과 건수를 요약한다.
- 객실 번호·유형·청소 상태·운영 상태·사유·예상 복구 시각을 필터 가능한 목록으로 표시한다.
- 기존 기능성 dialog와 alert dialog를 사용해 점검 요청, 판매 중지와 복구를 실행한다.
- 판매 중지 확인 전 영향 예약을 표시한다. 영향이 있으면 실행을 비활성화하고 예약 상세로 이동할 수 있게 한다.
- `CHECKED_IN` 예약 상세에는 배정 객실별 `객실 이동` 작업을 표시한다. 후보 선택과 필수 사유, 명시적 최종 확인 뒤 실행한다.
- 성공 시 객실 운영 목록과 예약 상세를 다시 조회한다. 응답 유실은 같은 선택과 키로 재시도하며 입력이 바뀌면 새 키를 발급한다.
- keyboard focus 복귀, Escape, 상태 안내의 live region, 390px 가로 넘침과 touch target을 검증한다.

## 오류 처리

- 오래된 version, 배정 변화와 동시 이동은 최신 상태를 다시 읽도록 안내한다.
- 후보가 다른 요청에 선점되면 현재 dialog를 유지하고 후보 목록을 갱신한다.
- 영향 배정이 생겨 판매 중지가 거부되면 최신 영향 목록을 즉시 표시한다.
- 서버 오류는 성공으로 간주하지 않으며 사용자의 입력과 멱등 키를 유지해 안전하게 재시도한다.

## 검증 기준

PostgreSQL 통합 테스트는 다음을 직접 검증한다.

- 운영 상태 전이와 감사, version 충돌, 멱등 재시도와 지점 권한
- 투숙·미래 확정 배정이 있는 판매 중지 차단과 영향 목록
- 점검 필요·판매 중지 객실의 신규 배정 및 체크인 전 재배정 후보 제외
- 청소 전 복구 거부, 청소 완료 후 명시적 복구, 자동 복구 부재
- 배정과 판매 중지의 경합에서 한 작업만 유효함
- 투숙 중 같은 유형 객실 이동의 배정 교체, 기존 객실 이중 상태와 감사
- 이동 실패의 전체 롤백, 같은 후보 동시 선점, 멱등성과 현재 배정 변경 경합
- 이동 후 체크아웃이 신규 객실만 청소 필요로 전환함
- 기존 배정·체크인·체크아웃·청소·체크인 전 재배정·예약 변경 회귀

관리자 Playwright는 지점 운영 상태 전환, 영향 배정 차단, 투숙 중 이동, 응답 유실 재시도, 기존 오늘의 운영 흐름을 검사한다. 데스크톱과 390×844 keyboard/mobile 동작, 변경 파일 lint, TypeScript와 production build를 통과해야 한다. 개발 API 재빌드와 migration, readiness, 실제 4001 읽기 화면을 확인하되 개발 예약 mutation은 별도 명시 승인 없이 실행하지 않는다.

## 배포와 롤백

schema는 감사 테이블과 운영 컬럼을 additive하게 추가하고, 기존 `housekeeping_status='OUT_OF_SERVICE'`만 보수적으로 두 상태로 이관한다. API를 먼저 배포한 뒤 관리자 UI를 배포한다. 이전 API로 롤백해도 새 컬럼과 감사 테이블은 남기며 `housekeeping_status`의 `CLEAN`·`NEEDS_CLEANING` 처리는 호환된다. 이전 API는 새 운영 상태를 배정 조건에 반영하지 않으므로 장기간 운영하지 않고, 롤백 시 신규 배정 API를 함께 제한한 뒤 복구 후 감사 이벤트를 기준으로 현재 상태를 재확인한다.

## 제외 범위

- 다른 객실 유형으로 이동하는 업그레이드·다운그레이드와 가격·결제 정산
- 객실 유형별 일자 판매 재고의 자동 차감·복구
- 자동 객실 재배정과 여러 객실 일괄 이동
- 날짜 구간 판매 중지와 예약된 자동 복구
- 고객 알림, 정비 작업지시, 외부 시설관리 시스템 연동
