# Task 4 예약 목록·상세·취소·변경 진행 상태 작업 기록

## 설계와 완료 기준

- `/reservations`는 `BookingSessionStore.listReservationAccess()`에 저장된 같은 브라우저의 예약 접근 정보만 읽고, 각 예약을 독립적으로 조회한다. 토큰이 만료되었거나 접근이 거부된 항목은 예약 존재 여부를 드러내지 않고 목록에서 제외한다.
- `/reservations/:id`는 URL의 예약 ID만으로 개인정보를 조회하지 않는다. 같은 브라우저 세션의 일치하는 관리 토큰이 있을 때만 상세를 불러온다.
- 상태 표기는 `결제 대기`, `예약 확정`, `변경 처리 중`, `취소 처리 중`, `취소 완료`, `만료`로 번역한다. 결제 성공·환불 금액·취소 가능 여부는 프런트에서 계산하지 않는다.
- 취소 전에는 서버의 정책 기반 미리보기에서 받은 마감 시각과 예상 환불액을 표시하고, 별도 확인 대화상자에서 실행한다. 닫기·Escape 뒤에는 취소 버튼으로 포커스를 돌린다.
- 진행 중인 변경은 기존 HttpOnly 고객 변경 세션의 `/current` 응답이 현재 상세 예약 ID의 suffix와 일치할 때만 표시한다. `AWAITING_PAYMENT`일 때만 추가 결제 재개 행동을 보인다.

## 변경

- `ReservationManagementPage`를 추가하고 App의 한국어·영문 예약 목록/상세 경로에 연결했다.
- 목록은 저장된 접근 토큰별 `GET /api/reservations/{id}` 호출을 병렬로 수행하고 실패 항목을 제외한다. 상세는 일정·객실 수·서버 결제 상태·총액·저장된 취소 정책을 표시한다.
- 고객 취소 미리보기 API가 없었던 점을 보완해 `GET /api/reservations/{id}/cancellation-preview`를 추가했다. 기존 `CancellationService`의 관리 토큰 검증, 저장 정책, 마감 계산과 서버 환불액을 재사용하며 상태를 변경하지 않는다. 취소 실행은 기존 `POST /cancel`을 그대로 사용한다.
- 변경 결제 응답의 실제 서버 필드명 `checkIn`/`checkOut`으로 클라이언트 타입과 기존 변경 결제 화면을 맞췄다.
- 상태 변환과 현재 예약에 연결된 추가 결제 재개 조건의 순수 계약, 그리고 목록·무권한·취소 대화상자·종료 상태·변경 세션을 다루는 브라우저 계약을 추가했다.

## RED 증거

```powershell
cd apps/web
node --experimental-strip-types src/lib/reservation-management-state.test.ts
```

`reservation-management-state.ts`를 만들기 전 실행했으며 `ERR_MODULE_NOT_FOUND`로 실패했다. 이후 최소 상태 변환 구현으로 GREEN을 확인했다.

## 검증

```powershell
cd apps/web
node --experimental-strip-types src/lib/reservation-management-state.test.ts
node --experimental-strip-types src/lib/payment-api-contract.test.ts
pnpm exec tsc -b
pnpm run build

cd ../../services/api
.\mvnw.cmd -q -DskipTests compile

git diff --check
```

모두 exit code 0으로 통과했다. 고객 production build는 2,915개 모듈을 변환했다.

## 사용자 브라우저 검증 항목

사용자 지시에 따라 Playwright, 실제 브라우저·서버·DB·토스/환불은 실행하지 않았다.

1. 같은 브라우저에서 확정 예약을 만든 뒤 `/reservations` 새로고침에서 카드가 남고, 다른 관리 토큰의 예약은 보이지 않는지 확인한다.
2. 상세에서 서버의 취소 마감·예상 환불액을 확인한 뒤 취소 대화상자를 열고, Escape와 `돌아가기` 뒤 취소 버튼으로 포커스가 복귀하는지 확인한다.
3. 취소 실행 뒤 서버 상태가 `취소 완료`로 다시 표시되고, 취소 버튼이 사라지는지 확인한다.
4. 활성 고객 변경 링크를 연 같은 브라우저에서만 대상 일정·차액·만료와 `추가 결제 계속하기`가 표시되는지 확인한다.
5. 390×844에서 가로 스크롤 없이 목록·상세·대화상자와 하단 행동을 키보드로 조작할 수 있는지 확인한다.

## 미검증·제한

- 실제 고객 예약 조회 응답은 객실 유형명과 결제 상태를 아직 포함하지 않는다. 화면은 서버가 이 선택 필드를 제공할 때 표시하고, 없는 경우 각각 안전한 예약 식별자와 `서버 확인 필요`를 표시한다.
- 고객 변경 세션 응답은 전체 예약 ID 대신 마지막 8자리만 제공한다. 상세 화면은 이 suffix가 현재 예약 ID와 일치할 때만 카드를 표시하며, Task 5의 변경 결제 응답 확장 시 전체 예약 ID를 제공하면 더 직접적으로 검증할 수 있다.
- 브라우저, Playwright, 라이브 API/DB, 토스 결제와 실제 환불은 사용자 소유 검증 범위로 남아 있다.

## 수정 라운드 1

- 고객 변경 결제 세션 응답에 전체 `reservationId`를 추가하고, 고객 상세는 suffix가 아닌 UUID 완전 일치일 때만 변경 진행 카드와 추가 결제 행동을 표시한다. 같은 마지막 8자리를 가진 다른 예약을 거부하는 순수·브라우저 계약을 추가했다.
- 고객 취소 미리보기 응답에 저장 정책의 `timezone`을 포함했다. 취소 마감은 브라우저 지역 설정 대신 이 timezone을 명시한 `Intl.DateTimeFormat`으로 표시한다.
- 예약 관리 화면은 `/en/reservations`에서 영문 상태·버튼·빈 상태·대화상자·통화·날짜 형식과 `/en` 경로를 사용한다.
- 취소 대화상자는 Tab/Shift+Tab 순환, Escape 닫기와 실행 실패 `role="alert"` 포커스를 제공한다. 오류가 대화상자 뒤에 숨지 않는다.
- 기존 격리 PostgreSQL 통합 테스트에 고객 취소 미리보기의 정상 토큰·잘못된 토큰·마감 경계·상태/재고/취소 이력 비변경 계약을 추가했다. 사용자 지시에 따라 이 통합 테스트는 실행하지 않았다.

### 수정 라운드 1 검증

```powershell
cd apps/web
node --experimental-strip-types src/lib/reservation-management-state.test.ts
pnpm exec tsc -b
pnpm run build

cd ../../services/api
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q -DskipTests test-compile
```

모두 exit code 0으로 통과했다. Playwright, 브라우저, 라이브 서버·DB와 PG/환불은 실행하지 않았다.
