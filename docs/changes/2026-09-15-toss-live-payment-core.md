# Toss Payments 운영 결제 코어

## 변경 이유

기존 `toss-test` 신규 예약 결제, 예약 변경 추가 결제와 환불 흐름을 운영 키와 혼용하지 않고 `toss-live`로 전환할 수 있는 기반이 필요했다. 실제 과금은 열지 않은 채 환경 계약, 신규 checkout 차단, provider 원장 분리와 공개 webhook의 권한 경계를 먼저 강화했다.

## 구현 범위

- `payment.provider`는 `fake`, `toss-test`, `toss-live`를 명시적으로 구분한다. Toss 거래 원장의 provider snapshot은 기존 `TOSS_TEST`와 신규 `TOSS_LIVE`를 유지한다.
- 테스트 키와 라이브 키 계열, MID, 고객 origin을 시작 시 검증한다. 라이브는 HTTPS origin만 허용한다.
- HTTP client도 생성된 `TossPaymentEnvironment`를 요청 시 재검증해 라이브 설정을 테스트 전용 검증기로 되돌리지 않는다.
- `PAYMENT_CHECKOUT_ENABLED`의 기본값은 `false`다. 라이브에서 꺼져 있으면 새로운 예약 결제와 예약 변경 추가 결제 시도만 거부한다.
- 이미 생성된 checkout 재열기, 승인 확인, 조회, 환불, 취소와 변경 적용 worker는 kill switch와 무관하게 같은 provider에서 계속 처리한다.
- 고객 웹은 라이브 client key와 결제위젯을 지원하고 테스트 배너·문구를 라이브에서 노출하지 않는다.
- V43은 webhook 조회 키를 `(provider, order_id)`로 분리하고 `(provider, transmission_id)`가 유일한 immutable 수신 기록을 추가한다.
- webhook은 최대 64 KiB, `PAYMENT_STATUS_CHANGED`, transmission ID와 order ID만 수용한다. 유효한 미지 주문은 202로 무해하게 무시하며 알려진 주문도 본문 상태가 아니라 서버 시크릿 Toss 재조회 결과만 반영한다.
- 애플리케이션 인스턴스별 webhook 초당 한도는 `TOSS_WEBHOOK_MAX_PER_SECOND`로 설정하며 기본값은 120이다.

## 운영 안전 경계

- 일반 결제 webhook에 별도 범용 HMAC 서명이 있다고 가정하지 않는다. webhook 본문의 상태, 금액, paymentKey와 취소 데이터는 예약 상태 변경 권한이 아니다.
- 롤백은 `PAYMENT_CHECKOUT_ENABLED=false`로 신규 외부 결제 시작만 차단한다. 이미 시작된 거래, 환불, `UNKNOWN`, webhook 조회 행은 삭제하거나 fake provider로 바꾸지 않는다.
- 결제위젯의 국내 카드·간편결제 수단 허용 목록은 Toss 상점/위젯 설정에서 별도로 제한해야 한다.
- 운영 키, 실제 MID, 공개 webhook 등록, 실결제와 실환불은 이번 변경에 포함하거나 저장소에 기록하지 않았다.

## 자동 검증

- 백엔드 집중 게이트: 환경 설정, HTTP 계약, checkout 정책, provider 안전성, 라이브 차단, 신규 예약 결제, 예약 변경 결제·환불·webhook, 취소 회귀 등 13개 클래스 141건을 통과했다.
- 고객 웹: Toss client, 결제위젯, 결제 복구, 예약 변경 결제 상태 계약 4개와 TypeScript/Vite production build를 통과했다.
- Flyway: PostgreSQL 테스트 DB에서 V43을 포함한 43개 migration validation을 통과했다.
- 추가 검사: API compile, `git diff --check`를 수행한다.

## 미검증 및 배포 전 확인

- 실제 Toss 라이브 키·MID·HTTPS 고객 origin으로 애플리케이션 시작
- 공개 HTTPS webhook 등록과 외부 전달·중복 재전송
- 로드밸런서/API gateway의 IP·경로별 ingress 속도 제한
- Toss 상점/결제위젯의 국내 카드·간편결제 전용 설정
- 사용자가 승인한 단일 소액 운영 승인·부분 환불·전체 취소와 DB/Toss 조회 대사
- 고객 브라우저의 데스크톱·모바일·키보드 실제 상호작용
- Toss 정산 snapshot·대사 및 SES/Solapi 링크 자동 발송은 후속 단계다.
