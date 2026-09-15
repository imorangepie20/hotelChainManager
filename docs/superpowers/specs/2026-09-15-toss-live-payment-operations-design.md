# Toss Payments 운영 결제·정산·알림 설계

작성일: 2026-09-15

## 목적

현재 `toss-test`로 검증한 신규 예약 결제, 예약 변경 추가 결제, 부분·전체 환불을 Toss Payments 운영 환경에 안전하게 연결한다. 운영 결제와 함께 토스 정산 자료와 내부 거래를 대사하고, 직원이 발급한 예약 변경 결제 링크를 AWS SES 이메일과 Solapi SMS로 자동 전달한다.

가격·재고·예약 확정·환불 성공 판정의 권한은 계속 Spring Boot에 두며, 외부 callback과 webhook 본문은 성공 권한이 아니다.

## 확정한 범위

- 결제 provider: Toss Payments
- 결제 수단: 즉시 승인되는 국내 카드와 간편결제
- 고객 알림: AWS SES 이메일과 Solapi SMS 모두
- 알림 대상: 직원 승인 흐름에서 발급한 예약 변경 추가 결제 링크
- 정산: Toss `GET /v1/settlements`의 라이브 자료를 내부 거래·환불·수수료·순정산액과 대사

## 제외 범위

- 가상계좌, 계좌이체, 휴대폰 결제, 상품권, 해외 결제
- 정기 결제, 브랜드페이, 에스크로, 지급대행
- 토스 수동 정산 요청과 회계 전표 자동 분개
- 마케팅 메시지, 대량 발송, 고객 응대 캠페인
- 실제 라이브 과금·SMS 발송·환불을 사용자의 건별 확인 없이 실행하는 것

## 선택한 구조

기존 모듈형 단일 Spring Boot 서버에 provider adapter를 유지하고 `toss-test`와 `toss-live`를 명시적으로 분리한다. 결제 미시서비스는 추가하지 않는다. 승인·조회·환불의 핵심 멱등성과 `UNKNOWN` 처리를 공유하고 키, MID, 표시 문구, DB provider snapshot만 환경별로 격리한다.

이 구조는 단일 `toss` 모드에서 키 prefix로 환경을 추론하는 방식보다 설정 실수를 빠르게 차단한다. 별도 결제 서비스보다 현재 MVP의 운영 복잡도도 낮다.

## 설정과 시작 시 검증

- `PAYMENT_PROVIDER=fake|toss-test|toss-live`
- `RESERVATION_CHANGE_GATEWAY=disabled|fake|toss-test|toss-live`
- `PAYMENT_CHECKOUT_ENABLED=false|true`: 신규 결제와 추가 결제 checkout 생성만 제어한다. 이미 시작된 승인 조회·환불·조정 worker는 계속 동작한다.
- `TOSS_PAYMENTS_CLIENT_KEY`, `TOSS_PAYMENTS_SECRET_KEY`, `TOSS_PAYMENTS_MERCHANT_ACCOUNT`
- `PAYMENT_CUSTOMER_ORIGIN`: `toss-live`에서는 HTTPS와 허용된 고객 host를 요구한다.
- SES: `NOTIFICATION_EMAIL_PROVIDER`, `AWS_SES_REGION`, `NOTIFICATION_EMAIL_FROM`
- Solapi: `NOTIFICATION_SMS_PROVIDER`, `SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SOLAPI_SENDER_NUMBER`
- 알림 링크 암호화: `NOTIFICATION_PAYLOAD_KEY_ID`, `NOTIFICATION_PAYLOAD_KEY`

`toss-test`는 `test_gck_/test_gsk_` 또는 `test_ck_/test_sk_` 한 세트를, `toss-live`는 대응하는 `live_` 한 세트를 요구한다. 클라이언트·시크릿 환경이 섞이거나 결제 provider와 예약 변경 gateway가 다른 토스 환경이면 서버 시작을 거부한다. 운영 시크릿은 설정 파일이나 Git에 넣지 않고 배포 환경의 secret manager로 주입한다. 토스 API 키 접근 IP 정책을 운영 체크리스트에 포함한다.

## 결제·환불 흐름

1. 서버가 예약·변경 요청·금액·통화·MID·환경을 잠그고 checkout order를 저장한다.
2. 고객 화면은 서버가 반환한 orderId·금액·클라이언트 키로 Toss SDK를 연다.
3. success URL의 paymentKey·orderId·amount는 성공 증거가 아니다. 서버가 저장값과 비교하고 고정 멱등키로 Toss 승인 API를 호출한다.
4. `DONE`, orderId, paymentKey, KRW 금액, MID를 모두 검증한 뒤에만 `ORIGINAL_CHARGE` 또는 `CHANGE_CHARGE`를 저장하고 예약·재고를 확정한다.
5. 환불은 저장된 동일 provider·MID·paymentKey의 환불 가능 잔액 안에서만 시작한다. 토스 cancel 응답의 transactionKey·금액·사유를 특정 command와 비교한 뒤 누적 환불액을 한 번만 증가시킨다.
6. timeout, 5xx, 응답 손상은 `UNKNOWN`으로 보존하고 재결제하지 않는다. 저장 paymentKey/orderId로 조회한 뒤에만 성공·실패를 확정한다. 멱등키 보장 기간을 넘긴 불명확 거래는 자동으로 새 키를 생성하지 않고 본사 조정 대상으로 남긴다.

`payment_provider_attempt`, `payment_adjustment_attempt`, `payment_transaction`의 provider snapshot은 `TOSS_TEST`와 `TOSS_LIVE`를 구분한다. 기존 `TOSS_TEST`와 `FAKE` 행은 바꾸지 않는 additive migration을 사용한다.

## webhook 경계

Toss 일반 결제 webhook에는 지급대행 `payout.changed`와 같은 범용 HMAC 서명이 제공되지 않는다. 그 서명 규칙을 카드 `PAYMENT_STATUS_CHANGED`에 잘못 적용하지 않는다.

- webhook은 상태 변경 command가 아니라 저장된 주문 재조회 힌트다.
- 본문 크기, content type, 허용 event type, orderId 형식, 알려진 주문을 검증한다.
- `tosspayments-webhook-transmission-id`와 orderId를 저장해 중복을 제거하고, ingress와 애플리케이션에 속도 제한을 둔다.
- 미지 주문은 항상 무해하게 무시한다. 알려진 주문도 본문 상태로 결제·환불·예약을 바꾸지 않고 서버 시크릿을 사용한 Toss 조회 결과만 반영한다.
- 국내 일반 결제 취소 webhook이 항상 오지 않는다는 전제로 환불 command 직접 응답과 조회를 권위 경로로 유지한다.

## 정산 대사

`TossSettlementClient`는 라이브 시크릿으로 `GET /v1/settlements` 조회만 수행한다. `soldDate`를 기준으로 하루 단위로 페이징하고, 조회가 최대 60초 걸릴 수 있는 공식 계약에 맞춰 결제 승인 API와 다른 타임아웃과 실행 풀을 사용한다.

1. 매일 전일부터 정산 지연 창까지 중복 조회하고, `(MID, paymentKey, transactionKey, soldDate)`를 고유 키로 immutable provider snapshot을 upsert한다.
2. 승인·추가 결제·환불 원장과 토스 매출액, 취소액, 결제 수수료, 부가세, 지급 예정액·일자를 비교한다.
3. 결과를 `MATCHED`, `AMOUNT_MISMATCH`, `FEE_MISMATCH`, `MISSING_INTERNAL`, `MISSING_PROVIDER`, `PENDING` 중 하나로 저장한다.
4. 대사는 예약·재고·환불액을 자동으로 고치지 않는다. 불일치는 본사 전용 조회와 감사 이력에 남기고, 기존 조정 API로만 해소한다.
5. 테스트 환경의 정산 조회는 빈 결과를 반환하므로 실제 수수료·지급액 대사 완료는 라이브 거래 이후에만 판정한다.

본사 API는 기간별 실행, 실행 상태, 불일치 목록과 상세를 제공한다. 첫 UI는 기존 `SDTPL_ADM/` 테이블·상태 컴포넌트를 재사용한 읽기 전용 불일치 목록으로 제한한다.

## 고객 링크 자동 전달

자동 전달은 직원이 추가 결제 링크를 새로 발급했고 DB transaction이 commit된 뒤에만 시작한다. 고객이 예약 상세에서 스스로 변경을 시작한 경우는 같은 브라우저에 바로 결제 세션을 만드므로 자동 알림을 중복 생성하지 않는다.

- `customer_notification_outbox`는 시도별 `EMAIL`, `SMS` 행을 분리하고 `(payment_attempt_id, channel, generation)`을 고유 키로 사용한다.
- 이메일과 SMS 중 하나가 실패해도 결제, 예약, 다른 채널을 rollback하지 않는다.
- 실패는 지수 backoff로 제한된 횟수만 재시도하고 최종 `FAILED`를 본사 재전송 대상으로 남긴다. provider message ID와 안전한 error code만 감사한다.
- 이메일·전화번호는 전송 시점의 예약 연락처를 사용하고, 이력에는 마스킹과 hash만 남긴다.
- 기존 public token은 hash만 DB에 저장되므로 worker 재시도에 필요한 링크 원문은 AES-GCM으로 암호화한 후 outbox에 저장한다. 평문 key ID·nonce·ciphertext만 두고 복호화 키는 secret manager에서 주입한다.
- 링크 암호문은 두 채널이 모두 성공하거나 링크가 만료된 후 지정 보존 기간 내에 제거한다. 키 회전 중에는 key ID로 구 키 복호화를 일시 유지한다.
- 메시지는 예약번호 끝 4자리, 결제 링크 만료 시각, HTTPS 링크만 포함하고 전체 결제 수단·개인정보를 포함하지 않는다.

SES sandbox에서는 검증된 발신자와 수신자만 사용한다. 운영 전환 전에 도메인 인증, SES production access, bounce·complaint 처리를 준비한다. Solapi 실제 SMS는 계정, 등록 발신번호, 잔액과 사용자의 건별 발송 확인 전에 호출하지 않는다.

## 관리 API와 UI

- 본사: 정산 대사 실행·실행 이력·불일치 목록·상세 조회
- 본사와 예약 지점 직원: 해당 예약 결제 링크의 채널별 전송 상태 조회와 최종 실패 재전송
- 재전송은 새 멱등키와 generation을 요구하고, 만료·폐기·이미 결제된 링크는 거부한다.
- 기존 `SDTPL_ADM/` 테이블, 상태 badge, dialog, toast를 재사용하고 본사·지점 권한은 API에서 다시 검증한다.

## 데이터 변경

기존 migration checksum은 바꾸지 않고 다음을 additive migration으로 추가한다.

- 토스 주문·거래·환불 provider의 `TOSS_LIVE` 허용과 환경 snapshot
- webhook 전송 ID, event type, orderId, 요청·처리 시각과 대상 환경
- 정산 실행, immutable provider settlement snapshot, 내부 대사 결과와 조정 감사
- 채널별 알림 outbox, 시도 횟수, lease, 다음 시도 시각, provider message ID, 마스킹 수신자, 수신자 hash, 암호화 링크 payload

정산 자료와 알림 감사는 예약 삭제 cascade에 의존하지 않고 운영 보존 정책을 따른다. 암호화 링크 payload만 전송 종료·만료 후 단기 파기한다.

## 오류·경합 처리

- checkout 차단 flag는 신규 외부 부작용만 막고 진행 중 command를 삭제하지 않는다.
- 승인·환불·webhook 재조회·정산·알림 worker는 서로 다른 outbox/lease를 사용한다.
- 느린 Toss 호출이 DB 잠금을 보유하지 않도록 claim, transaction 밖 HTTP, 결과 반영을 분리한다.
- 동일 주문의 callback·webhook·polling이 경합해도 같은 provider event와 멱등키를 중복 반영하지 않는다.
- 알림 provider timeout은 배송 실패를 즉시 확정하지 않는다. provider message ID 조회가 가능하면 조회하고, 불가능하면 같은 멱등 키를 유지한 제한 재시도로 중복 안내 가능성을 최소화한다.
- 정산 API의 429·5xx·timeout은 기존 snapshot을 삭제하지 않고 실행을 실패로 남긴 뒤 backoff한다.

## 검증 전략과 완료 기준

### 자동 검증

- 설정 계약: test/live 키 혼용, HTTP 운영 origin, MID 누락, provider/gateway 불일치, checkout flag를 시작 시 거부한다.
- Toss HTTP 계약: 승인·조회·부분 환불·정산 페이징, MID·금액·통화·거래 키 위변조, 4xx·429·5xx·timeout·손상 응답 mapping을 검증한다.
- PostgreSQL 통합: 신규 결제→예약 확정, 추가 결제→변경 적용, 부분 환불→후속 환불 가능 거래 선택, 전체 취소, `UNKNOWN`, 경합, provider 분리를 검증한다.
- webhook: 미지 주문, 중복 전송 ID, 역순 상태, 과대 본문, 재조회 실패를 검증하고 본문만으로 예약이 바뀌지 않음을 확인한다.
- 정산: 페이징, 재실행 upsert, 각 불일치 분류, 느린 응답, 부분 실패 후 재개를 검증한다.
- 알림: AES-GCM 위변조 거부, 시도별 멱등성, 채널 격리, 제한 재시도, 만료·완료 후 전송 거부, 권한별 상태 조회·재전송을 검증한다.
- 고객·관리자 웹의 관련 순수 계약, TypeScript, production build와 변경한 경로의 키보드·모바일 동작을 검증한다.

### 외부 환경 검증

1. Toss 테스트 키로 카드·간편결제 신규 승인, 예약 변경 추가 결제, 부분 환불, 전체 취소 순서를 실행하고 DB·재고·Toss 조회를 대사한다.
2. 공개 staging HTTPS URL에 Toss webhook을 등록하고 외부 전달, 중복 재전송, 미지 주문 무해성과 서버 재조회를 확인한다.
3. SES sandbox는 검증된 수신자 1건과 mailbox simulator로 전송·bounce 계약을 확인한다.
4. Solapi는 공급자 계정·발신번호·잔액을 확인한 뒤 사용자가 승인한 수신번호로 1건만 전송한다.
5. 라이브 키는 checkout flag를 끄고 설정 유효성·조회 경로부터 확인한다. 실제 과금과 환불은 사용자가 금액·결제 수단·시간을 확인한 단일 시나리오로만 실행한다.
6. 정산 조회는 테스트 환경에서 검증할 수 없다. 실제 라이브 거래가 정산 자료에 나타난 다음 날 이후에 수수료·지급 예정액·지급일을 내부 snapshot과 대사해야 완료다.

## 배포와 롤백

1. additive migration과 설정 검증을 배포하고 모든 외부 부작용 flag를 끄다.
2. `toss-test`와 목 provider 알림으로 자동 검증을 통과한다.
3. staging HTTPS webhook과 SES sandbox, 승인된 Solapi 1건을 검증한다.
4. `toss-live`의 키·MID·origin을 검증하되 checkout은 계속 차단한다.
5. 사용자가 승인한 단일 운영 결제·환불을 완료한 뒤 점진적으로 checkout을 개방한다.

롤백은 `PAYMENT_CHECKOUT_ENABLED=false`로 신규 checkout만 즉시 차단한다. 이미 시작된 결제·환불·`UNKNOWN`·webhook 재조회·정산·알림 행은 삭제하거나 fake로 바꾸지 않고 같은 provider에서 완료·조정한다. 거래·정산·알림 감사 테이블은 rollback에서 삭제하지 않는다.

## 구현 분할

한 번에 운영 부작용을 열지 않도록 다음 세 단계를 독립 배포 가능한 계획으로 나눈다.

1. Toss live 환경 분리, checkout kill switch, webhook 강화, 기존 결제·환불 회귀
2. 정산 조회 snapshot·대사·본사 읽기 전용 운영 화면
3. AES-GCM 링크 outbox, SES·Solapi adapter, 채널별 상태·재전송

각 단계는 별도 검증 기록과 rollback 조건을 갖고, 앞 단계의 기존 provider 회귀를 유지한다.

## 공식 근거

- [Toss API 키](https://docs.tosspayments.com/reference/using-api/api-keys): test/live 키 분리, 클라이언트·시크릿 한 세트, 시크릿 비공개, API 키 접근 IP 정책
- [Toss 결제 흐름](https://docs.tosspayments.com/guides/v2/get-started/payment-flow): success URL 값과 서버 저장 주문·금액 검증 후 승인
- [Toss webhook 이벤트](https://docs.tosspayments.com/reference/using-api/webhook-events): 일반 결제 webhook과 서명 제공 이벤트의 차이, 국내 일반 결제 취소 webhook 제약
- [Toss 정산 조회](https://docs.tosspayments.com/reference): `GET /v1/settlements`, 페이징, 최대 60초 응답, 수수료·지급 정보
- [Toss 환경](https://docs.tosspayments.com/guides/v2/get-started/environment): 테스트 환경의 정산 조회 제약
- [AWS SES sandbox](https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html): 검증된 수신자·할당량 제약과 production access
