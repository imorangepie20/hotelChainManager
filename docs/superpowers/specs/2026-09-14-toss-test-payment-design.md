# 토스페이먼츠 테스트 결제 연결 설계

상태: 2026-09-14 사용자가 토스 테스트 모드의 신규 예약 결제·추가 결제·부분 환불·실패 및 멱등 검증 범위를 승인했다. 이 문서는 구현 전 검토 대상이며 실제 과금·운영 배포·PG 계약은 승인 범위가 아니다.

## 목적과 대안

현재 내부 `FAKE` 결과 생성 대신 토스페이먼츠 테스트 결제창과 승인·조회·취소 API를 연결한다. 가격·재고·예약 확정의 최종 권한은 Spring Boot가 계속 가진다.

- 선택한 방식: 토스 테스트 결제창을 고객 웹에서 열고 Spring Boot가 금액·주문·거래를 검증해 승인한다. 원 거래를 저장하므로 추가 결제와 부분 환불까지 직접 검증할 수 있다.
- 내부 fake 유지: 비밀키 없이 빠른 회귀에 유용하지만 실제 PG 연동 검증이 아니므로 자동화 테스트 전용으로 남긴다.
- 복수 PG 중계 서비스 도입: 다른 PG 추가에는 유리하지만 이번 단일 테스트 연동에 새 중계 계정과 계약을 추가하지 않는다.

## 범위와 분할

하나의 provider 연결 작업을 다음 순서로 구현하고 각각 검증한다.

1. 테스트 전용 설정, 토스 HTTP adapter와 신규 예약 결제·조회·예약 확정.
2. 기존 예약 변경 승인·hold·outbox에 토스 추가 결제와 원 거래 부분 환불 연결.
3. 토스 거래가 있는 예약의 취소 안전성, 고객 결과 화면과 장애·멱등 회귀, 실제 sandbox 거래 증거 확보.

포함: KRW 카드 테스트 결제, 서버 승인, 거래 저장, 추가 결제, 원 결제 부분 환불, 취소·실패·응답 유실·동시 요청, 재고 만료 경합, 모바일·키보드 UI, 감사와 기능 플래그 기반 롤백.

제외: 라이브 키, 실제 과금, 운영 계약·수수료 회계 대사, 가상계좌 입금·빌링·해외결제, 이메일·문자 자동 발송, 운영 공개 서버·터널 생성. 테스트 계정 생성과 약관 동의는 사용자에게 맡긴다.

## 현재 구현에서 바꿀 경계

- `TestPaymentService`는 성공을 내부에서 만들고 `FAKE/LOCAL` 원 거래를 저장한다. 토스 결과를 이 서비스의 `SUCCESS` 인자로 우회 전달하지 않는다. 재고 확정 도메인 규칙만 필요한 만큼 공통화한다.
- `ReservationChangeSettlementService`는 추가 결제 provider와 merchant를 `FAKE/LOCAL`로 고정한다. 거래별 provider·테스트 환경·merchant 식별자를 기록하고 환불 adapter와 일치하는지 검증한다.
- `PaymentAdjustmentGateway.createCheckout`의 hosted URL 계약은 토스 SDK 인증→서버 승인 흐름과 다르다. 자사 checkout URL과 서버 발급 주문 정보를 제공하고 승인 command를 분리한다. 결제창을 열었다는 사실을 결제 성공으로 해석하지 않는다.
- `CancellationService`는 현재 `TestRefundGateway`를 DB 트랜잭션 안에서 호출한다. 토스 예약을 해당 가상 환불 경로에서 차단하고, 토스 취소 command를 DB 잠금 밖에서 수행한 후 검증된 결과만 내부에 적용한다.
- 기존 개발 `FAKE` 예약은 토스 원 거래로 변환하지 않는다. 저장된 provider로 실행 경로를 구분하고 신규 provider가 과거 가상 거래 ID로 토스 API를 호출하지 못하게 한다.

## 설정·비밀값과 실행 환경

- 서버 설정은 `PAYMENT_PROVIDER=disabled|fake|toss-test`, `TOSS_PAYMENTS_CLIENT_KEY`, `TOSS_PAYMENTS_SECRET_KEY`, `TOSS_PAYMENTS_MERCHANT_ACCOUNT`, `PAYMENT_CUSTOMER_ORIGIN`으로 명시한다. 예약 변경 gateway 설정은 신규 결제 provider와 검증된 조합에서만 활성화한다.
- `toss-test`는 두 키의 `test_` prefix, merchant 설정과 고정 토스 HTTPS API origin을 시작 시 검증한다. `live_` 키 또는 누락 설정이면 fail-closed하며 fake로 자동 대체하지 않는다.
- 클라이언트 키만 서버 checkout 응답으로 제공한다. 시크릿은 Git 제외 환경 파일과 서버 환경변수에만 두고 요청·오류 로그·브라우저 응답에 포함하지 않는다. 환경 예제에는 값이 아닌 설정 이름만 기록한다.
- 현재 환경 파일에서 토스 키 설정 부재를 값 출력 없이 확인했다. 공식 문서의 공개 체험 키는 기능 체험용으로만 명시해 사용할 수 있지만, 사용자 상점의 거래·웹훅 내역 검증에는 사용자 테스트 키가 필요하다. 어느 상점 키로 검증했는지 비밀값 없이 기록한다.
- 기존 포트 고객 `4000`, 관리자 `4001`, API `4080`을 유지한다. 구현은 새 `codex/` 작업 트리에서 하고 기존 관리자 서버 프로세스를 교체하거나 별도 관리자 포트를 띄우지 않는다. 검증 후 `main`에 병합하고 해당 작업 트리만 정리한다.

## 신규 예약 결제 흐름

1. 고객의 기존 예약 관리 권한으로 checkout을 요청한다. 서버가 예약 상태·저장 총액·KRW·hold 만료를 검증하고 불변 `orderId`와 결제 attempt를 저장한다. 같은 유효 attempt를 재사용하며 병렬 활성 attempt를 막는다.
2. 고객 웹은 서버가 준 주문번호·금액·클라이언트 키·자사 success/fail URL로 토스 SDK 결제창을 연다. 카드 정보는 호텔 앱이 수집하거나 저장하지 않는다.
3. 성공 복귀의 `paymentKey`, `orderId`, `amount`는 성공 증거가 아닌 검증 대상이다. 서버 저장 금액·주문·고객 권한과 일치하는지 확인한 뒤 승인 command를 claim한다. 클라이언트 임의 금액으로 승인하지 않는다.
4. DB 트랜잭션 밖에서 고정 멱등 키로 토스 승인 API를 호출한다. HTTP 200뿐 아니라 `DONE`, orderId, paymentKey, 총액·통화와 merchant를 검증한다. HTTP 오류는 명시적 거부와 불명확 결과를 구분한다.
5. 검증된 승인 결과를 저장하고 예약·일별 재고를 잠가 held→confirmed, `CONFIRMED`, 원 거래 및 감사를 원자 적용한다. PG 성공 이후 내부 적용 실패는 같은 attempt로 재시도하며 이중 승인하지 않는다.
6. 인증 실패·창 닫기는 서버 승인 성공으로 처리하지 않는다. 금액 확보 시간이 남고 승인 미시작이면 재시도 가능하다. 승인 시작 이후 timeout은 `UNKNOWN`으로 거래 재조회 전까지 성공·실패를 단정하지 않는다.

예약 만료 worker는 승인 claim과 같은 예약 잠금 순서로 경쟁한다. 승인 시작 전 만료는 정상 해제한다. 승인 실행·불명확·성공 후 적용 대기는 hold를 임의 해제하지 않으며 명시적 조정 대상으로 남긴다. checkout만 열고 승인하지 않은 예약은 이 보호 상태를 만들지 않아 원래 만료된다. 토스 결과가 성공인데 hold가 이미 해제된 경우 자동 확정하거나 재고를 음수로 만들지 않고 보정 환불/조정 대상으로 남긴다.

## 추가 결제·부분 환불과 취소

- 기존 승인 한도와 15분 변경 hold, 고객 fragment→HttpOnly 세션, outbox lease를 유지한다. checkout에는 원문 링크 token이나 예약 관리 token을 넣지 않는다.
- 토스 외부 사이트 복귀 시 `SameSite=Strict` 쿠키에 의존한 직접 API callback을 사용하지 않는다. 자사 고객 결과 페이지에 복귀한 후 same-origin API로 확인한다. 변경 결제 세션이 만료·삭제됐으면 주문번호만으로 승인하지 않고 재인증을 요구하며, 이미 시작된 승인 결과는 서버 재조회/조정으로 보존한다. 세션 보안 수준을 임의로 낮추지 않는다.
- 추가 결제의 attempt·주문번호·금액을 서버에 묶고 신규 예약과 같은 승인 검증을 수행한다. `CREATE_CHECKOUT` 성공은 준비 완료일 뿐이며 PG 승인 성공을 확인한 뒤 `CHANGE_CHARGE`와 `READY_TO_APPLY`를 기록한다.
- 부분 환불은 같은 merchant·provider의 저장 원 paymentKey에 `cancelAmount`와 사유를 보내고 attempt별 고정 멱등 키를 사용한다. 토스 취소 거래 식별자와 해당 취소 금액을 검증한 뒤 누적 환불액을 한 번만 증가시킨다. 전체 잔액 변화만으로 특정 환불 attempt 성공을 추정하지 않는다.
- 변경 환불은 기존 정책대로 원 결제의 남은 환불 가능액 한도에서만 진행한다. 부족하면 시작 전에 거부하고 임의로 추가 결제 거래에 분산하지 않는다.
- 예약 전체 취소는 원 거래와 성공한 추가 결제 거래의 남은 잔액을 각각 취소한다. 전체 환불 계획을 불변 저장하고 거래별 결과를 감사한다. 일부 환불 성공·일부 불명확이면 나머지만 재시도하며 모든 필요한 환불 확인 전에는 `CANCELLED`·재고 반환으로 완료 처리하지 않는다. fake 거래와 혼합된 예약은 명시적 조정을 요구한다.
- 카드 결제의 취소 성공은 토스 취소 응답·재조회로 판단한다. 국내 카드 취소 웹훅이 항상 발송된다고 가정하지 않는다.

## 웹훅·멱등성과 조회

- 토스 일반 결제 웹훅에는 일반적인 서명 헤더가 없다. 가상계좌의 secret 또는 지급대행 HMAC 규칙을 카드 웹훅에 잘못 적용하지 않는다.
- 웹훅은 알려진 저장 주문을 재조회하는 힌트로만 수용한다. 미지 주문은 처리하지 않고 크기·빈도 제한과 dedupe를 둔다. 본문 상태만으로 결제·환불·예약을 바꾸지 않는다. 서버 인증을 사용한 토스 조회로 orderId·paymentKey·금액·merchant·거래 상태를 다시 확인한다.
- 로컬 localhost는 토스에서 접근할 수 없으므로 공개 터널·배포를 임의로 만들지 않는다. 이번 실제 sandbox 검증은 승인·조회·취소 응답을 권위 경로로 사용하고 webhook handler는 직접 계약 테스트로 검증한다. 실제 외부 웹훅 전달은 사용자 상점과 승인된 공개 URL 준비 전 미검증으로 기록한다.
- PG 승인·환불의 고정 멱등 키는 DB에 보존한다. 토스의 15일 유효 기간을 넘어 불명확 거래를 새 키로 자동 재청구하지 않고 조회 또는 수동 조정한다.
- 중복 callback·다른 사용자 주문·금액 위변조·역순 웹훅·다른 merchant 거래는 무해하거나 거부된다. UNKNOWN에서 성공 확인은 가능하되 terminal 성공을 늦은 실패로 덮어쓰지 않는다.

## 데이터와 API

기존 V35 거래·정산 테이블은 유지한다. additive migration으로 신규 결제 attempt, 주문과 provider·merchant·환경 snapshot, 승인 claim/lease·재시도·결과 상태, 환불 command별 거래 참조와 누적 반영 식별자, 신규 결제 조정 감사를 추가한다. 주문·provider 거래·멱등 키와 활성 attempt에 고유 제약을 둔다. 기존 migration checksum을 수정하지 않는다.

- `POST /api/reservations/{id}/payment-checkout`: 예약 권한·멱등 키로 서버 주문 생성/재사용.
- `POST /api/payments/toss/confirm`: 저장 주문과 권한으로 callback 값 검증 및 승인 시작/기존 결과 재생.
- `GET /api/payments/{orderId}`: 권한 있는 고객의 서버 상태 확인; token·카드·시크릿을 제외한 최소 정보.
- 기존 변경 결제 checkout API: provider 구분을 가진 checkout URL·서버 주문 응답으로 확장하며 fake 회귀 호환을 유지한다.
- `POST /api/payments/toss/webhook`: 재조회 enqueue 전용. 공개 호출 자체는 승인 권한이 아니다.
- 기존 취소 API: fake 기존 동작과 토스 pending/failed/unknown/completed 결과를 구분하고 고객·직원 UI가 pending을 완료 문구로 표시하지 않게 한다.

신규/변경 결제의 고객 권한은 각 기존 예약 관리 token 또는 고객 결제 세션을 유지한다. 필요 checkout 세션은 해시만 저장하고 반환 경로를 서버 allowlist로 제한한다. paymentKey와 callback 쿼리는 최소 시간만 사용하고 고객 페이지가 즉시 주소에서 제거한다. 외부 결제 결과 경로에는 referrer 제한을 적용하고 민감 쿼리를 access log에서 제외한다.

## 완료 기준과 검증

- 테스트 먼저: adapter 요청·응답 및 오류 mapping, live 키 차단, 금액·주문·merchant 위변조, 중복 승인·취소·이벤트와 15일 재시도 정책.
- PostgreSQL 직접·인접 회귀: 신규 hold→확정/만료 경쟁, 승인 성공 후 DB 실패 재시도, 추가 결제→변경 원자 적용, 부분 환불 누적 한도, 전체 취소의 부분 성공/UNKNOWN, provider 분리와 기존 fake 회귀.
- 고객 UI 계약 및 Chromium: 서버 주문으로 SDK 호출, 성공/실패/대기 복귀·새로고침, 민감 주소 정리, 390px·키보드·이중 클릭, 저장 초안 미리보기에서 결제 차단 유지. 변경한 관리자 경로만 필요한 회귀를 실행한다.
- 변경 경로 타입 검사·빌드, migration 및 실제 API readiness, 기존 관리자 4001 유지 확인.
- 실제 토스 sandbox: 테스트 표시 확인→신규 결제 승인→토스 조회와 내부 예약·재고·거래 비교→추가 결제→부분 환불→취소 잔액 비교. PG 오류 헤더로 실패 시나리오를 검증한다. 민감하지 않은 주문·거래 결과와 금액·상태만 한국어 변경 기록에 남긴다.
- 내부 fake 성공, mock 응답 또는 코드·명령의 존재만으로 실제 sandbox 검증을 완료했다고 보고하지 않는다. 카드사 화면의 사용자 인증이 필요하면 그 단계만 사용자에게 요청하고 실제 금융 정보는 채팅으로 요구하지 않는다.
- 전체 suite의 선행 `WebsiteMediaVariantSchedulingTest` bean 장애는 별도 기록하며 이번 변경의 직접 회귀를 우선한다.

## 배포·롤백과 후속

설정·schema→API adapter→고객 UI 순으로 적용하며 테스트 플래그를 명시적으로 켠다. 롤백은 새 checkout 생성을 중지하고 실행·UNKNOWN·성공 후 적용 대기 command를 보존해 조회·조정을 끝낸 뒤 진행한다. 토스 거래가 있는 예약을 fake 성공·fake 환불 경로로 되돌리지 않는다. additive 테이블과 거래 감사는 삭제하지 않는다.

운영 키·계약·HTTPS와 외부 webhook 등록, 수수료·정산 보고서 대사, 자동 안내는 별도 사용자 승인과 환경 준비 후 작업한다.

## 확인한 공식 근거

- [통합결제창 연동](https://docs.tosspayments.com/guides/v2/payment-window/integration): SDK 인증 후 서버 승인과 callback 값 검증.
- [API 키](https://docs.tosspayments.com/reference/using-api/api-keys): test 키의 가상 승인, 키 세트와 시크릿 보안.
- [인증·멱등 헤더](https://docs.tosspayments.com/reference/using-api/authorization): 네트워크 재시도와 멱등 키 15일 유효 기간.
- [LLM 공식 요약](https://docs.tosspayments.com/guides/v2/get-started/llms-quick-reference): 일반 결제와 지급대행 webhook 인증 차이.
- [웹훅 이벤트](https://docs.tosspayments.com/reference/using-api/webhook-events): 이벤트 종류와 국내 일반 결제 취소 전달 제약.
