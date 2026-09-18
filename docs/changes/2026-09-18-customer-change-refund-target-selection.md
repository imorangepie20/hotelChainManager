# 고객 예약 변경 환불 대상 거래 선택

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신 2026-09-18.

## 변경 이유

고객이 직접 예약을 변경해 차액 환불이 발생할 때, 환불 대상 거래를 항상 원결제(`ORIGINAL_CHARGE`)로 고정했다. 원결제가 이미 부분 환불돼 남은 금액이 환불액보다 적으면, 잔액이 충분한 나중 변경 추가 결제(`CHANGE_CHARGE`)가 있어도 `거래 정산 불가` 오류가 났다. 같은 예약에서 환불 가능한 거래를 잔액 기준으로 선택해 고객 변경이 막히지 않도록 수정했다.

## 구현 범위

- `ReservationChangeSettlementService`에 `lockRefundableTransaction(reservationId, refundAmount)`를 추가했다. `ORIGINAL_CHARGE`, `CHANGE_CHARGE` 후보 중 `captured_amount_krw - refunded_amount_krw >= 환불액`인 거래를 최신순으로 선택한다.
- 고객 정산 시작(`startCustomerSettlement`)과 직원 환불 시작(`startRefund`)이 기존 `lockSettleableOriginalTransaction` + 잔액 비교 분리를 이 호출 하나로 대체했다. 환불액 부족 후보는 쿼리에서 제외된다.
- 선택된 거래는 공급자·통화·상점·게이트웨이 일치 조건을 그대로 검증한다. `gatewayMode`가 `toss-test`면 `TOSS_TEST` 공급자, 상점 계정, 비어있지 않은 `gateway_transaction_id`가 필요하다.
- `toss_refund_command`는 여전히 활성 환불당 거래 1건만 허용하므로, 같은 거래에 대한 중복 환불은 DB 제약이 막는다.

## 서버 안전 경계

- 고객이 보낸 금액은 여전히 확정값으로 쓰지 않고, 요청 생성 시 예약과 재고를 잠그고 다시 계산한다.
- 활성 정산 공급자와 예약 거래가 일치하는지 `PaymentProviderSafety.requireCompatibleSettlement`로 먼저 확인한다. fake와 Toss 결제가 섞인 예약은 변경 정산을 시작할 수 없다.
- 후보 선택 쿼리가 `for update`로 거래 행을 잠그므로, 동시에 들어온 두 환불이 같은 거래 잔액을 중복으로 사용하지 않는다. 한쪽은 잔액 부족으로 제외된다.

## 자동 검증

- API: `CustomerSelfServiceReservationChangeIntegrationTest` 6건. 새 테스트 `laterRefundUsesAvailableChangeChargeAfterOriginalChargeWasPartiallyRefunded`는 원결제가 250,000원 부분 환불돼 110,000원만 남고 뒤이은 390,000원 변경 추가 결제가 있을 때, 100,000원 차액 환불이 해당 `CHANGE_CHARGE`를 대상으로 선택되고 상태가 `REFUND_PENDING`이 되는지 확인한다. 전체 6건 오류 0.
- 웹: `apps/web` 계약 테스트 25종, `tsc -b` 타입 검사, Vite production build 종료 코드 0.
- 컴파일: `mvnw compile`, `mvnw test-compile` 종료 코드 0.
- Flyway: 테스트 DB(tmpfs PostgreSQL 55433)에서 42개 migration 적용 후 위 테스트를 실행했다. 종료 후 `db-test` 컨테이너를 중지했다.

## 사용자 브라우저 검증

- 추가 결제 새로고침 복구: `tossChangeStarted` 표시가 있을 때 서버 상태 조회가 실패하면 표시를 지우고 추가 결제 버튼 경로를 유지한다. 결제 대기 중에는 `서버 상태 다시 확인` 버튼을 숨긴다. 이 동선의 브라우저 확인은 사용자가 수행한다.
- 실제 Toss 운영 키, 부분 환불·추가 결제·webhook, 라이브 PG 정합성은 검증 범위가 아니다.

## 미검증 항목

- 전체 backend suite. `reservationchange` 패키지 집중 테스트와 컴파일만 실행했다.
- Playwright 브라우저·UI 회귀, 라이브 PostgreSQL 개발 DB, 운영 다중 인스턴스 부하.
- 변경 추가 결제 뒤 다시 발생한 환불이 여러 번 누적될 때의 장기 원장 정합성. 현재는 활성 환불당 거래 1건 제약과 잔액 조건만 확인했다.
