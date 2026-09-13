# 예약 변경 차액 정산·승인 설계

상태: 사용자와 합의한 구현 전 설계. 특정 PG사 선정과 운영 배포 승인은 포함하지 않는다.

## 목적

체크인 전 확정 예약의 날짜·객실 유형 변경에서 발생하는 추가 결제 또는 부분 환불을 안전하게 처리한다. 지점 직원은 절대 차액 100,000원 이하를 직접 처리하고, 이를 초과하면 본사 관리자의 승인을 받아야 한다. 예약·일별 재고·가격의 최종 권한은 계속 Spring Boot에 있으며, 외부 결제 성공과 내부 적용 사이의 실패를 재시도하거나 명시적인 조정 대상으로 남긴다.

기존 예약은 승인과 정산이 끝날 때까지 유효한 상태로 유지한다. 승인 대기 중에는 대상 재고를 점유하지 않는다. 추가 결제는 직원이 저장 카드에 임의 청구하지 않고 고객용 일회성 결제 링크로 진행한다. 부분 환불은 승인 완료 후 기존 결제 거래의 원 결제수단으로 반환한다.

## 범위

### 포함

- `BRANCH_STAFF`의 100,000원 직접 처리 한도와 `HQ_ADMIN` 승인
- 승인 대기, 재견적, 대상 재고 임시 확보, 추가 결제 링크, 부분 환불, 최종 예약 변경
- 멱등 PG 명령·웹훅, 만료 작업, 실패 재시도와 수동 조정 상태
- 기존 예약 관리 상세의 요청 진행 상태와 본사 승인 대기 목록
- 감사 이력, additive migration, 기능 플래그 기반 전환과 롤백

### 제외

- 특정 PG 계약·수수료·정산 주기와 운영 키 관리
- 고객이 직접 예약 조건을 편집하는 화면
- 카드 외 계좌 환불, 현장 현금 정산, 복수 통화와 분할 결제
- 체크인 당일 예외 승인, 배정 객실 자동 해제·재배정
- 객실 수·투숙 인원·취소 정책 변경에 따른 별도 요금
- 결제 링크의 이메일·문자 자동 발송

## 확정 정책

- 승인 기준은 추가 결제와 부분 환불 모두 절대 차액으로 계산한다.
- 지점 직원은 절대 차액이 100,000원 이하일 때 자동 승인된다. 100,001원 이상은 본사 승인이 필요하다.
- 본사 관리자가 직접 만든 요청은 금액 제한 없이 승인된 요청으로 기록한다.
- 본사 승인은 대상 체크인·체크아웃·객실 유형·요금제·정산 방향과 승인 가능한 최대 절대 차액에 묶는다.
- 지점 자동 승인의 최대 절대 차액은 100,000원이다. 본사 승인과 본사 직접 요청은 화면에서 명시적으로 확인한 절대 차액을 승인 한도로 기록한다.
- 재견적이 같은 정산 방향이고 승인 한도 이하이면 승인을 유지한다. 대상 조건이나 정산 방향이 바뀌거나 한도를 넘으면 승인을 무효화한다.
- 승인 요청은 생성 후 24시간 또는 지점 현지 체크인일 00:00 중 먼저 도달하는 시점에 만료된다.
- 결제 링크와 대상 재고 확보는 기본 15분이며 서버 설정으로만 조정한다. 결제가 시작된 뒤 PG 결과가 불명확하면 자동 해제하지 않고 조정 대상으로 전환한다.
- 한 예약에는 정산 진행 단계의 변경 요청을 하나만 허용한다.

## 책임 경계

`ReservationChangeSettlementService`는 상태 전이와 서버 정책을 소유한다. 기존 `StaffReservationStayChangeService`의 견적·수용 인원·재고 계산과 최종 예약 변경 규칙은 공통 도메인 함수로 추출해 재사용한다. 관리자 Next.js는 서버가 반환한 상태와 가능한 action만 표시하며 승인 여부, 가격, 재고 또는 환불액을 자체 계산하지 않는다.

`PaymentAdjustmentGateway`는 PG 중립 계약이다. 추가 결제 링크 생성, 거래 상태 조회, 부분 환불 요청을 멱등 키와 함께 제공한다. 외부 호출은 DB 트랜잭션에서 직접 실행하지 않고 outbox worker가 수행한다. PG 웹훅은 서명과 event 식별자를 검증하고 원문 비밀값을 저장하지 않는다.

## 데이터 모델

### `reservation_change_request`

- 요청 ID, 예약 ID, 기준 예약 revision
- 기존·목표 체크인·체크아웃, 객실 유형, 요금제, 객실 수와 인원 snapshot
- 상태, 정산 방향, 현재 견적 ID, 승인 한도, 요청자·지점
- 승인 요청 만료, 결제·재고 확보 만료, 생성·수정 시각
- 오류 코드와 사용자용 오류 요약, 낙관적 version

진행 상태의 예약 ID에 부분 unique index를 두어 중복 정산 요청을 막는다. 종료 상태는 `COMPLETED`, `REJECTED`, `CANCELLED`, `EXPIRED`다. `RECONCILIATION_REQUIRED`는 종료가 아니라 운영자 확인 전 동결 상태다.

### `reservation_change_quote`와 `reservation_change_quote_night`

견적은 수정하지 않고 revision마다 새로 만든다. 기존 총액, 신규 총액, 차액, 통화, 객실 수, 생성 시각과 일별 요금을 보존한다. 승인과 정산 시 사용한 견적 ID를 각각 참조해 어떤 가격에 의사결정했는지 추적한다.

### `reservation_change_approval`

자동 승인·본사 승인·반려·무효화를 모두 append-only로 저장한다. 결정자, 역할, 결정 시각, 견적 ID, 정산 방향, 최대 절대 차액과 사유를 기록한다. 지점 직원의 자동 승인은 결정자를 요청자로 남긴다.

### `reservation_change_hold_day`

요청이 의존하는 객실 유형·숙박일·수량·만료·상태를 저장한다. 실제 `inventory_day.held`를 증가시킨 순증가 구간은 `NEW_HOLD`, 기존 예약의 확정 재고로 보장되는 동일 객실 유형 겹침 구간은 `EXISTING_CONFIRMED`로 구분한다. `EXISTING_CONFIRMED`는 `held`를 중복 증가시키지 않는다.

### `payment_adjustment_attempt`

정산 방향, 금액, 통화, 멱등 키, gateway 거래·event 식별자, 상태와 오류를 저장한다. 같은 변경 요청과 멱등 키 조합은 하나만 존재한다. PG event 식별자도 고유해야 한다.

부분 환불은 실제 원 결제 거래를 식별해야 하므로 실제 PG 도입 단계에서 provider 중립 `payment_transaction`을 함께 둔다. 예약, gateway provider·merchant account·거래 식별자, 승인·매입 금액, 누적 환불액과 통화를 저장하고 카드번호·CVC는 저장하지 않는다. `payment_adjustment_attempt`는 원 거래를 참조하며 누적 환불액이 원 매입액을 넘을 수 없다. 테스트 결제 예약은 fake gateway 환경에서만 정산하고, 운영 환경에서 실제 원 거래가 없는 예약은 `PAYMENT_TRANSACTION_NOT_SETTLEABLE`로 거부한다.

### outbox와 감사

outbox는 결제 링크 생성·부분 환불·상태 재조회를 전달하며 claim, attempt, 재시도 시각과 terminal 상태를 갖는다. `reservation_change_event`는 요청 생성부터 완료·조정까지 모든 상태 전이를 append-only로 기록한다.

기존 V33 `reservation_stay_change`에는 nullable unique `change_request_id`를 추가한다. 신규 흐름 완료 시 V33 요약 감사도 함께 기록해 기존 조회와 운영 보고의 호환성을 유지한다.

예약에는 `operation_revision BIGINT NOT NULL DEFAULT 0`을 additive하게 추가한다. 날짜·객실 유형·객실 수·인원·배정·체크인·취소처럼 정산 유효성에 영향을 주는 성공 mutation은 revision을 증가시킨다. 이름·이메일 정정은 정산 조건을 바꾸지 않으므로 revision을 증가시키지 않는다.

## 상태 모델

- `PENDING_APPROVAL`: 본사 승인을 기다리며 예약과 재고는 변경하지 않는다.
- `APPROVED`: 자동 또는 본사 승인을 받았으며 정산 전 재견적이 필요하다.
- `AWAITING_PAYMENT`: 대상 재고를 확보했고 고객 결제 링크가 유효하다.
- `REFUND_PENDING`: 대상 재고를 확보했고 부분 환불 결과를 기다린다.
- `READY_TO_APPLY`: 차액이 없거나 PG 성공이 확인되어 최종 적용할 수 있다.
- `APPLYING`: apply worker가 요청을 claim해 최종 트랜잭션을 실행 중이다. 짧은 lease가 만료되면 같은 요청을 다시 claim할 수 있다.
- `COMPLETED`: 예약·재고·일별 가격·감사 반영이 끝났다.
- `REJECTED`, `CANCELLED`, `EXPIRED`: 정산 없이 종료됐고 신규 hold는 반환됐다.
- `RECONCILIATION_REQUIRED`: PG 결과가 불명확하거나 PG 성공 후 내부 적용이 반복 실패해 자동 진행을 중단했다.

서버가 반환한 action 외의 전이는 409로 거부한다. 모든 mutation은 요청 version과 `Idempotency-Key`를 요구한다. 같은 키·같은 요청은 기존 결과를 반환하고, 같은 키의 다른 요청이나 다른 직원 사용은 충돌한다.

## 주요 흐름

### 요청과 승인

1. 직원이 기존 미리보기로 대상 조건과 최신 가격을 조회한다.
2. 직원은 대상 조건과 화면에서 확인한 예상 총액을 요청 생성 API에 보낸다. 서버는 예약의 상태·현지 날짜·미배정·지점 권한·수용 인원·가격·재고를 다시 확인하고, 금액이 일치할 때만 불변 견적과 요청을 생성한다. 금액이 달라졌으면 `PRICE_CHANGED`와 새 미리보기를 요구한다.
3. 본사 요청 또는 절대 차액 100,000원 이하는 `APPROVED`, 지점 직원의 초과 요청은 `PENDING_APPROVAL`이 된다.
4. 본사 관리자는 대상 조건, 일별 가격, 차액, 요청 지점을 확인해 승인하거나 사유와 함께 반려한다.
5. 승인 단계에서는 `inventory_day`를 변경하지 않는다. 예약 revision이 바뀌면 요청은 더 진행할 수 없고 새 견적·요청이 필요하다.

### 승인 후 재견적과 재고 확보

1. 서버가 승인된 대상 조건으로 가격·수용 인원·재고를 다시 계산한다.
2. 같은 방향에서 승인 한도 이하이면 승인을 유지한다. 지점 자동 승인 요청의 새 차액이 100,000원 이하이면 그대로 자동 승인하며, 이를 넘거나 본사 승인 한도를 넘으면 `PENDING_APPROVAL`로 되돌리고 무효화 event를 남긴다.
3. 예약 행, 변경 요청 행, 기존·대상 `inventory_day`를 정렬된 순서로 잠근다.
4. 동일 객실 유형의 겹치는 날짜는 기존 confirmed를 자기 재고로 인정한다. 그 외 대상 날짜만 `held`를 증가시키고 `reservation_change_hold_day`를 기록한다.
5. 모든 숙박일을 확보하지 못하면 전체를 롤백하고 기존 예약을 유지한다.

### 추가 결제

1. 직원 브라우저가 32바이트 난수 공개 token을 만들고 결제 링크 발급 API에 보낸다. hold 확보 트랜잭션은 token의 SHA-256 해시와 결제 session 생성 outbox만 저장한다.
2. worker가 PG 결제 session을 만들고 gateway 식별자와 hosted URL을 저장한다. 서버는 직원에게 `https://고객웹/reservation-change-payment#token` 형태의 로컬 고객 URL을 반환하며 예약 관리 token이나 개인정보를 넣지 않는다. 같은 멱등 키와 같은 공개 token의 재시도는 같은 URL을 반환한다. 결제가 시작되지 않은 상태에서 새 링크를 발급하면 이전 token을 폐기한다.
3. 고객 웹은 fragment token을 `X-Reservation-Change-Token` 헤더로 자사 API에 한 번 교환해 짧은 HttpOnly·SameSite 세션 쿠키를 받고 URL fragment를 즉시 제거한다. 이후 예약 번호 일부, 변경 일정과 추가 금액을 확인하고 PG 결제를 수행한다. 이 교환으로 새로고침 복원과 URL·접근 로그의 token 노출 방지를 함께 보장한다.
4. 검증된 성공 웹훅은 멱등하게 `READY_TO_APPLY`로 전환한다. 실패 또는 결제 시작 전 만료는 신규 hold를 반환하고 `EXPIRED` 또는 `CANCELLED`로 끝낸다.

### 부분 환불

1. hold 확보 트랜잭션과 함께 기존 결제 거래에 대한 부분 환불 outbox를 기록한다.
2. worker는 요청 ID 기반 멱등 키로 환불하고 gateway 응답·event를 저장한다.
3. 성공이 확인되면 `READY_TO_APPLY`, 명시적 실패면 신규 hold를 반환하고 기존 예약을 유지한다.
4. 결과가 불명확하면 자동 만료로 hold를 풀지 않고 `RECONCILIATION_REQUIRED`로 전환한다.

### 최종 적용

1. apply worker는 별도 짧은 트랜잭션에서 `READY_TO_APPLY` 요청을 `APPLYING`으로 claim하고 lease를 남긴다. 이후 예약 행 → 변경 요청 행 → 객실 유형 ID와 숙박일로 정렬한 재고 행 순서로 잠근다. outbox와 apply claim은 본 작업 트랜잭션 전에 끝내 이 잠금 순서를 침범하지 않는다.
2. 기준 예약 revision, 미배정·체크인 전 상태, 유효한 hold, 최신 견적과 성공 정산을 재검증한다.
3. 대상의 `NEW_HOLD`는 held를 줄이고 confirmed를 늘린다. 기존 예약 범위의 confirmed를 반환하되 동일 유형 겹침은 순효과가 정확히 한 객실 수가 되도록 날짜별 delta로 적용한다.
4. 예약 조건·총액과 `reservation_night`를 교체하고 operation revision을 증가시킨다.
5. V33 요약 감사, 변경 event와 `COMPLETED` 상태를 같은 트랜잭션에 기록한다.

DB 적용이 일시적으로 실패하면 동일 요청으로 재시도한다. PG 성공이 확인된 상태에서 재시도 한도를 넘기거나 불변식 위반이 발견되면 `RECONCILIATION_REQUIRED`로 전환하고 hold를 임의 해제하지 않는다.

## 동시성 및 다른 예약 작업과의 관계

- 모든 예약 mutation은 예약 행을 먼저 잠그고 이후 재고 행을 정렬해 잠근다.
- 승인 대기 중에는 취소·인원 변경·객실 배정을 허용하지만 같은 트랜잭션에서 operation revision을 증가시키고 기존 요청을 사유 `RESERVATION_CHANGED`의 `EXPIRED`로 전환한다. 따라서 부분 unique index가 새 요청을 막지 않는다.
- `AWAITING_PAYMENT`, `REFUND_PENDING`, `READY_TO_APPLY`, `APPLYING`, `RECONCILIATION_REQUIRED`에서는 취소·인원·객실·숙박 변경을 409로 차단한다. 이름·이메일 정정은 허용한다.
- 체크인 또는 노쇼 전이는 활성 정산 요청이 있으면 차단하고 직원에게 먼저 요청 취소 또는 조정을 요구한다.
- 기존 직접 변경 API는 실제 정산 기능 플래그가 켜진 환경에서 `CHANGE_SETTLEMENT_REQUIRED`를 반환한다. 최종 적용은 내부 서비스만 호출할 수 있다.

## API 경계

- `POST /api/staff/reservations/{id}/change-requests`: 대상 조건과 예상 총액으로 서버 재검증 후 요청·첫 견적 생성
- `GET /api/staff/reservation-change-requests`: 지점 범위·상태별 목록
- `GET /api/staff/reservation-change-requests/{id}`: 견적, 승인, 정산, event 상세
- `POST /api/staff/reservation-change-requests/{id}/approve`: 본사 승인
- `POST /api/staff/reservation-change-requests/{id}/reject`: 사유를 포함한 본사 반려
- `POST /api/staff/reservation-change-requests/{id}/reprice`: 승인 후 재견적과 승인 유효성 확인
- `POST /api/staff/reservation-change-requests/{id}/payment-link`: 재고 확보와 링크 발급 시작
- `POST /api/staff/reservation-change-requests/{id}/refund`: 재고 확보와 부분 환불 시작
- `POST /api/staff/reservation-change-requests/{id}/cancel`: 정산 시작 전 요청 취소
- `POST /api/reservation-change-payments/session`: header token을 짧은 HttpOnly 고객 세션으로 교환
- `GET /api/reservation-change-payments/current`: 고객 세션의 최소 변경·금액 정보
- `POST /api/reservation-change-payments/current/checkout`: 유효한 gateway hosted session으로 이동할 일회성 checkout 시작
- PG webhook 경로는 provider adapter가 소유하며 서명 검증 뒤 내부 command로 변환한다.

읽기 응답에는 현재 상태에서 호출 가능한 action을 함께 반환한다. 본사만 모든 지점의 승인 대기를 보고 승인·반려할 수 있다. 지점 직원은 자기 지점 요청만 생성·조회·후속 처리할 수 있다.

## 오류와 운영 복구

- 재고·가격 변경은 각각 `SOLD_OUT`, `PRICE_CHANGED`로 반환하고 새 견적을 요구한다.
- 오래된 request version, 예약 revision과 잘못된 상태 전이는 409로 구분한다.
- PG 명시적 실패는 기존 예약 유지와 신규 hold 반환을 보장한다.
- 운영 환경에서 실제 원 결제 거래가 없는 예약과 원 매입액을 넘는 누적 환불은 정산을 시작하기 전에 거부한다.
- 중복·순서가 뒤바뀐 웹훅은 event 고유 키와 현재 상태로 무해하게 처리한다.
- PG 결과가 불명확하거나 성공 후 적용이 실패하면 자동으로 반대 금융 거래를 만들지 않는다. 본사 운영자가 gateway 조회 결과를 확인해 적용 재시도, hold 해제, 고객 환급 중 하나를 선택하고 사유를 남긴다.
- 로그에는 공개 token, 카드 정보, staff session, gateway 비밀값을 남기지 않는다.

## 관리자·고객 UI

기존 예약 상세의 숙박 변경 영역은 견적 후 `직접 처리 가능` 또는 `본사 승인 필요`를 표시한다. 요청 생성 후에는 중복 편집 폼 대신 상태, 만료, 담당 action과 타임라인을 보여준다.

본사 관리자는 예약 관리 화면의 `승인 대기` 필터에서 지점, 고객 표시명, 기존·목표 일정·객실, 일별 가격과 차액을 비교한다. 승인과 반려는 별도 확인 대화상자를 사용하고 반려 사유를 필수로 받는다. 지점 직원은 승인 후 재견적 결과를 확인한 뒤 추가 결제 링크 발급 또는 부분 환불을 명시적으로 실행한다.

고객 결제 링크 화면은 fragment token을 세션 쿠키로 교환하고 주소에서 제거한 뒤 변경 일정, 객실 유형·요금제, 추가 결제액, 만료 시각과 테스트/실제 결제 환경을 명확히 표시한다. 예약자 전체 이메일, 내부 직원 정보와 승인 사유는 노출하지 않는다. 링크 만료·결제 완료·실패를 새로고침 후에도 서버 상태로 표시한다.

기존 대화상자·달력·테이블·알림 컴포넌트를 재사용하고 키보드 포커스 복귀, 오류 연결, 390px 화면의 action 순서와 가로 overflow를 검증한다.

## 배포와 롤백

1. additive migration과 비활성 API·worker를 먼저 배포한다.
2. fake gateway 환경에서 outbox·웹훅·복구 경로를 검증한다.
3. PG sandbox 키와 webhook 서명을 설정하고 실제 sandbox 추가 결제·부분 환불을 확인한다.
4. 본사 계정부터 읽기·승인 UI를 열고 지점별로 신규 요청 생성을 활성화한다.
5. 신규 흐름이 열린 환경에서는 직접 숙박 변경 API를 차단해 우회 정산을 막는다.

롤백은 신규 요청 생성을 먼저 중단하고 worker를 drain한 뒤 이전 애플리케이션을 배포한다. 금융 거래가 시작된 요청과 `RECONCILIATION_REQUIRED` 요청은 삭제하거나 이전 직접 변경 API로 처리하지 않는다. 신규 테이블, reservation revision, V33 연결과 PG 감사는 남겨 둔다. 물리적 schema 제거는 모든 요청의 금융·재고 상태를 대조한 별도 승인 migration으로만 수행한다.

## 구현 분할

이 설계는 한 번에 배포하지 않고 다음 세 작업으로 나눈다.

1. **승인 기반**: 요청·견적·승인·event와 operation revision을 추가하고, 승인 전 재고 불변과 관리자 승인 UI까지 구현한다. 정산 기능 플래그는 닫아 둔다.
2. **정산 오케스트레이션**: 일자 hold, outbox, fake gateway, 고객 결제 링크, 만료·재시도·조정 목록과 최종 적용을 구현한다. 테스트 환경에서만 전체 상태 흐름을 연다.
3. **실제 PG 연결**: provider 선정과 별도 구현 계획을 승인받은 뒤 `payment_transaction`, adapter, webhook 서명, sandbox·운영 rollout을 진행한다.

첫 구현 계획은 1번과 2번을 각각 독립적으로 검증·중단할 수 있는 checkpoint로 작성한다. 3번은 실제 provider·계정·운영 정책이 정해지기 전에는 시작하지 않는다.

## 검증 기준

### 서버·DB

- 100,000원과 100,001원 경계, 추가 결제·환불 양방향, 본사 직접 요청
- 타 지점 접근, 본사 전용 승인·반려, 직원 포함 멱등 충돌
- 승인 전 `inventory_day` 불변과 24시간·체크인 cutoff 만료
- 재견적 하락 승인 유지, 한도 초과·방향·조건 변경 승인 무효화
- 마지막 객실 경합, 다박 일부 부족 전체 롤백, 동일 객실 유형 겹침의 자기 재고와 순증가 hold
- 결제·환불 성공·실패·중복·역순·지연 웹훅, 링크·hold 만료
- PG 성공 후 DB 적용 재시도, 재시도 한도 이후 조정 상태와 감사
- 활성 정산 중 취소·인원·객실·체크인 차단과 이름·이메일 정정 허용
- V33 최종 감사, 일별 가격, 예약 total, 재고 delta와 operation revision의 원자성

PostgreSQL 통합 테스트는 실제 Flyway migration과 행 잠금을 사용한다. 마지막 객실과 webhook 경합은 별도 connection으로 동시에 실행한다. gateway 계약 테스트는 fake adapter에서 시작하고 provider sandbox suite를 별도로 둔다.

### UI

- 지점 직접 처리·본사 승인 필요 표시와 요청 후 중복 실행 방지
- 본사 승인 대기 필터, 일별 가격 비교, 승인·필수 사유 반려
- 승인 무효화·가격 변경·품절·PG 실패·만료·조정 필요 안내
- 고객 결제 링크의 금액·만료·성공·실패 새로고침 복원
- 응답 유실 뒤 동일 멱등 키 재시도와 완료 후 예약 목록·상세 갱신
- Chromium 데스크톱과 390×844, 키보드 열기·닫기·포커스 복귀·오류 접근성

### 운영 검증

- API·worker readiness와 migration 적용
- PG sandbox 추가 결제 및 부분 환불의 원 거래 연결·웹훅 서명·중복 전달
- 결제 시작 전 만료, 성공 웹훅 지연과 worker 재시작 복구
- DB의 결제 attempt·change event·V33 감사와 PG dashboard 금액 대조

## 완료 조건

- 승인 없이 100,001원 이상의 지점 변경을 확정할 수 없다.
- PG가 실패하거나 결과가 불명확할 때 기존 예약이 조용히 변경되지 않는다.
- 성공한 추가 결제·부분 환불은 정확히 한 번의 예약·재고 변경과 연결된다.
- 자동 복구할 수 없는 외부·내부 불일치는 삭제되지 않고 본사 조정 목록에 남는다.
- 실제 정산 모드에서 현재 직접 숙박 변경 경로로 우회할 수 없다.
- 구현·테스트·운영 검증 결과와 미검증 PG 운영 범위를 한국어 변경 기록에 남긴다.
