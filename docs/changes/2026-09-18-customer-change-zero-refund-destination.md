# 고객 예약 변경 0원·환불 동선 안내

> 상태: `0d5df51`로 커밋됨. 최종 갱신 2026-09-19.

## 변경 이유

고객이 직접 예약을 변경할 때 차액이 없거나 환불이 발생하면, 브라우저가 추가 결제 화면(`/reservation-change-payment`)으로 이동했다. 이 화면의 세션과 결제 버튼은 추가 결제 시도에만 발급되므로, 0원·환불 변경은 결제 수단이 없는 화면에 갇혀 진행 상태를 알기 어려웠다. 고객을 예약 상세로 보내고 서버 정산·적용 상태를 폴링하도록 수정했다.

## 구현 범위

- `reservation-change-state.ts`에 `changeSettlementDestination(status)`를 추가했다. `AWAITING_PAYMENT`만 `PAYMENT` 목적지로 하고, `READY_TO_APPLY`·`REFUND_PENDING`·그 외는 `DETAILS`로 구분한다.
- `ReservationChangePage`가 `changeAction` 대신 이 목적지 함수를 사용해 추가 결제 화면으로 보낼지 결정한다. 결제 화면으로 이동할 때만 `tossChangeStarted` 표시를 지운다.
- `reservation-change-payment-state.ts`에 `REFUND_PENDING`을 상태·폴링·화면 표시에 추가했다. `describeReservationChangePayment`의 `direction`으로 `REFUND`를 유지하고 `canCheckout`은 `false`다.
- `canRecoverChangePayment`가 `REFUND_PENDING`과 `COMPLETED`에서 결제 창 복구 버튼을 표시하지 않는다. 환불과 0원 변경은 결제 창을 떠난 적이 없으므로 이 동선의 복구 대상이 아니다.
- `shouldPollReservationChangePayment`가 `REFUND_PENDING`을 폴링 대상에 추가했다.
- `ReservationChangePaymentPage`가 `REFUND_PENDING`일 때 예약 상세·홈 링크를 표시하고, `paymentStatusText`에 한국어 '차액 환불을 처리 중입니다'·영어 'Refunding the price difference'를 추가했다.
- `reservation-change-copy.ts`에 고객 변경 시작 거부 코드 안내 6종을 추가하고, `ReservationChangePage`의 `message()`가 `ApiFailure.code`별로 안내를 선택한다. `RESERVATION_REVISION_CONFLICT`·`SOLD_OUT`·`RESERVATION_CHANGE_ACTIVE`·`CHANGE_SETTLEMENT_DISABLED`·`PAYMENT_PROVIDER_ACTION_UNSUPPORTED`·`PAYMENT_TRANSACTION_NOT_SETTLEABLE`.

## 서버 권한과 안전 경계

- 목적지 선택은 서버가 반환한 변경 상태의 의미값만 사용한다. 금액·재고·정산 방향·예약 확정 권한은 여전히 Spring Boot 응답에 있고, 화면은 안내만 한다.
- 0원·환불 변경은 추가 결제 화면의 세션 토큰을 발급받지 않는다. 목적지를 바꿨으므로 고객이 결제 링크 없는 화면에서 빈 세션으로 API를 호출하지 않는다.
- 환불 중 결제 버튼을 숨기는 것은 `describeReservationChangePayment`의 `canCheckout`과 서버 `AWAITING_PAYMENT` 상태에 의존한다. 클라이언트가 결제 가능 여부를 따로 판단하지 않는다.
- 거부 코드 매핑은 행동 안내만 제공한다. 원인 판단·재고·가격·정산 권한은 그대로 서버에 있다.

## 자동 검증

- 웹: `apps/web` 계약 테스트 25종. `reservation-change-payment-state.test.ts`에 `REFUND_PENDING` 표시·결제 차단·폴링·복구 버튼 비표시 단계가, `reservation-change-state.test.ts`에 목적지 함수 단계가, `reservation-change-copy.test.ts`가 영문 안내에 한글이 섞이지 않는지 단계가 추가됐다.
- 웹: `tsc -b` 타입 검사, Vite production build 종료 코드 0.
- API: `mvnw compile`과 `CustomerSelfServiceReservationChangeIntegrationTest` 6건(새 환불 대상 선택 테스트 포함)이 종료 코드 0. 테스트 DB(tmpfs PostgreSQL 55433)에서 42개 migration 적용 후 실행했고 종료 후 컨테이너를 중지했다.

## 사용자 브라우저 검증

- 0원 변경 시작 시 예약 상세로 이동하고 `변경 처리 중` 상태가 폴링으로 완료로 바뀌는지 확인.
- 환불 변경 시작 시 예약 상세에서 `환불 처리 중`과 환불 완료가 반영되는지 확인.
- 변경 시작 거부 안내(매진·가격 변경·진행 중 변경·정산 비활성화·결제 수단 미지원·환불 내역 부족)가 한국어·영어로 표시되는지 확인.
- 실제 Toss 운영 키, 부분 환불·추가 결제·webhook, 라이브 PG 정합성은 검증 범위가 아니다.

## 미검증 항목

- 전체 backend suite. `reservationchange` 집중 테스트와 컴파일만 실행했다.
- Playwright 브라우저·UI 회귀, 라이브 PostgreSQL 개발 DB, 운영 다중 인스턴스 부하.
- 환불 대상 선택(`lockRefundableTransaction`)과 이 동선 안내가 같이 동작하는 브라우저 흐름. 서버 단위 테스트와 프론트 계약 테스트만 각각 검증했다.
