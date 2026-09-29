# Toss 라이브 정산 대사 사전 감사

최종 갱신: 2026-09-30

## 결론

현재 Zorin 배포 환경에는 대사할 실제 라이브 거래가 없어 검증을 차단했다. 결제·환불·정산 실행을 임의로 만들지 않았고, Toss 외부 API도 호출하지 않았다.

## 읽기 전용 확인 결과

- API 환경은 `PAYMENT_PROVIDER=fake`, checkout·정산 비활성 상태다.
- 라이브 secret과 MID는 설정되지 않았다. 실제 값, token, 원문 환경 파일은 출력하지 않았다.
- PostgreSQL `READ ONLY` transaction으로 확인한 결제 거래, provider attempt, adjustment, 환불 명령, 정산 run, snapshot, reconciliation은 모두 0건이다.
- 관리자 정산 GET 경로는 조건부 bean이 로드되지 않아 404다.
- API·admin·web·postgres·concierge는 healthy이고 tunnel은 실행 중이다. 서비스 장애가 아니라 결제/정산 기능이 의도적으로 비활성인 상태다.

## 차단 조건

다음 조건 중 하나라도 남으면 실제 대사를 완료로 판단하지 않는다.

1. `toss-live`가 아니거나 정산 기능이 비활성이다.
2. 라이브 client key와 secret key의 환경 접두가 맞지 않거나, 두 키가 같거나, MID가 없거나, customer origin이 HTTPS가 아니다. 값 자체는 감사 로그에 남기지 않고 존재·분류·비동일·HTTPS 여부만 확인한다.
3. 기존 `TOSS_LIVE` 승인 또는 환불 거래가 없다.
4. 거래의 정산 기록이 아직 Toss 자료에 나타나지 않았다. Toss 공식 계약상 정산 기록은 결제 다음 날부터 조회된다.
5. 승인된 운영 과정으로 만들어진 run/snapshot/reconciliation이 없다. 감사 작업에서는 POST run 생성·retry를 호출하지 않는다.
6. test 환경의 빈 정산 응답만 있다. Toss 공식 계약상 test 환경에서는 정산 기록이 항상 없는 것으로 조회되므로 라이브 대사 증거가 아니다.
7. 취소 정산의 음수 `amount`·`fee`·`supplyAmount`·`vat`·`payOutAmount`를 공식 응답 계약대로 파싱하는 회귀 테스트가 통과하지 않았다. 현재 client는 음수 fee/supplyAmount/vat를 `MALFORMED_RESPONSE`로 거부하므로 취소가 포함된 라이브 page는 snapshot·reconciliation 전에 run이 실패할 수 있다. 이 회귀는 승인 거래 한정으로 우회할 수 없는 절대 차단 조건이며, 통과 전에는 Toss 라이브 정산 GET 자체를 호출하지 않는다.
8. `paidOutDate` 조회, 상태/ID 불일치 표현, 성공 run의 `PENDING` 재대사 계약이 확정되지 않았다. 7번을 먼저 해소한 뒤 이 세 항목은 구현하거나, 실제 검증 범위를 승인 거래의 지원 필드와 금액·수수료 산식으로 명시적으로 제한하는 선택이 가능하다.

공식 근거는 [정산 조회 API](https://docs.tosspayments.com/reference)와 [테스트·라이브 환경 차이](https://docs.tosspayments.com/guides/v2/get-started/environment)를 따른다.

## 현재 대사 계약의 한계

- 취소가 없는 승인 정산은 금액을 `AMOUNT_MISMATCH`, 수수료·지급액 산식을 `FEE_MISMATCH`로 분류할 수 있다. 취소 정산은 현재 음수 필드 파싱 제한 때문에 이 분류 단계까지 도달하지 못할 수 있다.
- Toss Settlement에는 별도 settlement ID나 최상위 status가 없다. 공급자 거래 식별자는 `paymentKey + transactionKey`다.
- 현재 구현은 exact key가 맞지 않으면 `MISSING_INTERNAL`과 `PENDING/MISSING_PROVIDER`로 나눠 기록한다. 전용 `ID_MISMATCH` 또는 `STATUS_MISMATCH`는 없다.
- `paidOutDate`는 snapshot에 저장되지만 HQ 조회 API·화면에 노출되지 않아 지급일 불일치를 현재 인터페이스만으로 검증할 수 없다.
- `PENDING` 대사는 성공한 run 안에서 자동 재검증되지 않는다. delay 경과 후 동일 기간의 새 run이 필요하며, FAILED run retry와 의미가 다르다.
- 기존 snapshot은 자연키 충돌 때 갱신되지 않는다. Toss가 금액·지급일을 후행 정정한 경우 새 run도 과거 snapshot을 다시 사용할 위험이 있다.

따라서 라이브 거래가 생겨도 현재 구현만으로는 요청한 금액·상태·거래 ID·정산일·취소·재시도 결과 전체를 한 번에 증명할 수 없다. 실제 대사 전에 취소 정산 음수 필드 지원과 회귀 테스트를 반드시 완료한다. 그 뒤 지급일 조회 계약, 상태/ID 불일치 표현, PENDING 재대사 방식은 구현하거나 승인 거래 한정 검증 범위를 명시적으로 승인받는다.

## 재검증 절차

1. 승인된 실제 라이브 거래가 자연 발생한 뒤 임의 결제·환불 없이 대상 `soldDate`를 정한다. 최소 다음 날 이후이며, `MISSING_PROVIDER` 확정은 설정된 delay window가 지난 뒤 수행한다.
2. 환경값을 노출하지 않고 `provider=toss-live`, 정산 활성, client/secret key의 live 접두 분류, 두 키 비동일, MID 존재, HTTPS customer origin 여부만 boolean으로 확인한다.
3. DB `READ ONLY` transaction으로 대상 기간의 성공 승인·환불 건수와 금액 합계만 확인한다. 거래 ID는 로그에 출력하지 않고 필요하면 일회성 salt의 HMAC으로만 대응시킨다.
4. 기존 승인 절차로 만들어진 정산 run이 있을 때만 GET 조회를 사용한다. 감사자가 `POST /runs` 또는 `POST /retry`를 호출하지 않는다.
5. 취소 정산 음수 필드 회귀 테스트가 통과한 뒤에만 Toss `GET /v1/settlements`를 호출한다. `dateType=soldDate`, 1부터 시작하는 page를 사용하고 마지막 짧은 page까지 읽는다. 공식 응답이 최대 60초 걸릴 수 있으므로 request timeout은 60초 이상이어야 하며 현재 client의 65초를 유지한다. secret/MID/원문 응답/식별자는 출력하지 않고 HTTP 상태, 건수, 금액·수수료·지급액 합계, 취소 건수만 기록한다.
6. 외부 응답, 내부 결제·환불, 저장 snapshot을 `paymentKey + transactionKey`로 메모리에서 대조한다. 보고서에는 비식별 키와 금액/status/date 차이만 남긴다.
7. FAILED run의 retry는 승인된 운영자가 수행한 경우에만 전후 attempt/error/final status를 읽는다. `PENDING`은 retry하지 않고 delay 경과 후 승인된 새 run 결과를 읽는다.
8. 대사 후에도 payment transaction, refund command, reservation, inventory의 행·금액·상태가 전후 동일한지 다시 읽어 조회 전용 원칙을 증명한다.

## 미수행 항목

- 실제 Toss `GET /v1/settlements`
- 정산 run 생성 또는 retry
- 결제·환불·정산·예약·재고 상태 변경
- 라이브 거래별 금액·상태·거래 ID·지급일 대사
- 취소 정산 음수 금액·수수료·공급가·부가세·지급액 파싱과 snapshot/reconciliation 생성

실제 거래와 승인된 run이 생길 때까지 이 항목들은 미검증으로 유지한다.
