# 고객 예약·변경 여정 재설계 검증 기록

날짜: 2026-09-15

## 변경 범위

- 홈은 검색 조건의 시작점만 맡기고, 고객 여정을 `/booking/results`, `/booking/checkout`, `/booking/complete`, `/reservations`, `/reservations/:id`, `/reservation-change-payment`으로 분리했다.
- 검색 조건만 URL에 넣고, 예약자 정보·예약 관리 토큰·결제 복귀 값은 브라우저 세션 또는 `HttpOnly` 고객 변경 세션에서만 사용한다.
- 객실 총액·재고·확정·취소 가능 금액·변경 차액·결제 가능 상태는 Spring Boot 응답만 표시한다. 고객과 관리자 화면은 값을 계산하거나 상태를 전이하지 않는다.
- 고객 변경 결제는 기존/변경 예약의 일정·객실·요금·총액을 비교하고, 직원 화면은 로컬 테스트 결제 링크의 생성·만료·복사 결과와 직접 선택 fallback을 표시한다. 자동 이메일·SMS 전달은 추가하지 않았다.

## 이번 실행의 비브라우저 검증 결과

아래 명령은 현재 HEAD `ead37d8`에서 실행했으며 모두 종료 코드 0이다.

- 고객 순수 계약: `customer-route`, `booking-query`, `booking-session`, `latest-availability-request`, `booking-checkout-state`, `booking-checkout-message`, `toss-payments`, `toss-widget-checkout`, `payment-api-contract`, `reservation-management-state`, `reservation-change-payment-state`를 각각 `node --experimental-strip-types`로 실행했다. 경로 우선순위, 엄격한 검색 조건, 세션 데이터 격리, 오래된 검색 응답 무시, 확보/오류/만료 상태, 토스 복귀 query 제거, 위젯 분기, API 경로, 예약·변경 상태 번역을 확인한다.
- 관리자 순수 계약: `reservation-change-link-state.test.ts`를 같은 Node 방식으로 실행했다. 링크 생성·복사·만료 및 clipboard fallback 상태를 확인한다. Node가 `package.json`의 module 형식을 재해석한다는 경고만 출력했고 테스트 실패는 없었다.
- 고객 웹: `pnpm exec tsc -b`, `pnpm run build`가 통과했다. Vite production build는 2,916개 모듈을 변환했다.
- 관리자 웹: `pnpm exec tsc --noEmit`, `pnpm run build`가 통과했다. Next.js production build는 정적 route 104개를 생성했다.
- API: `.\\mvnw.cmd -q -DskipTests compile`, `.\\mvnw.cmd -q -DskipTests test-compile`이 통과했다. `.\\mvnw.cmd -q "-Dtest=TossPaymentsHttpClientTest,TossPaymentsPropertiesTest" test`는 비DB 단위 테스트 6건(HTTP client 4건, 설정 2건)을 실패·오류·건너뜀 없이 통과했다.
- Git: 문서 작성 전 `git status --short`가 비어 있었고 `git diff --check`도 통과했다. 문서 변경 뒤에도 같은 공백 검사를 다시 실행한다.

## 사용자 실행 브라우저·실서버 검증 절차

이번 실행에서는 사용자 소유 범위인 Playwright, 브라우저/UI 자동화, 4000·4001·4080 라이브 서버와 PostgreSQL, Toss 결제·부분 환불을 실행하지 않았다. 아래 절차는 테스트 전용 예약과 테스트 키만 사용하는 사용자 검증 체크리스트다. 비밀값, 결제 키, 링크 token, 예약자 개인정보를 문서·터미널 기록에 남기지 않는다.

### 기동과 종료

별도 기동/종료 스크립트는 저장소에 없다. 기존 Compose·package script를 사용한다.

```powershell
docker compose up -d --build api
pnpm --dir apps/web dev
pnpm --dir SDTPL_ADM dev
```

- API readiness는 `Invoke-WebRequest -UseBasicParsing http://127.0.0.1:4080/actuator/health/readiness`로 확인한다.
- 고객 웹은 `http://127.0.0.1:4000`, 관리자 웹은 `http://127.0.0.1:4001`에서 연다.
- 고객·관리자 개발 서버는 각각 실행한 터미널에서 `Ctrl+C`로 종료한다. API만 멈출 때는 `docker compose stop api`를 사용한다. 사용자 개발 DB를 삭제하는 `docker compose down --volumes`는 이 절차에 포함하지 않는다.

### 화면 계약과 접근성

1. 다음 Playwright 계약을 실행한다. 현재 `apps/web/package.json`에는 Playwright 실행기가 선언돼 있지 않아 이 checkout에서 `pnpm exec playwright`는 실행할 수 없다. 고객 test runner 의존성을 준비한 환경에서만 첫 명령을 실행한다. 순수 계약에는 위 Node 명령을 사용했다.

   ```powershell
   cd apps/web
   pnpm exec playwright test test/customer-booking-journey.spec.ts test/customer-reservation-management.spec.ts

   cd ../../SDTPL_ADM
   pnpm exec playwright test e2e/staff-reservation-change-settlement.spec.ts
   ```

2. 데스크톱과 390×844에서 홈 검색 → 결과 조건 수정·객실 선택 → checkout의 예약자 입력·동의 → 확보·결제 준비 상태를 확인한다. 홈에는 결과 카드·예약자 입력·결제·예약 관리 패널이 없어야 하며, 모바일 가로 스크롤과 하단 주요 행동의 콘텐츠 가림이 없어야 한다.
3. checkout에서 결제창을 닫은 뒤 같은 확보를 재시도하고, 새로고침 뒤 선택·확보 요약이 복원되는지 확인한다. 결제 복귀 후 주소에 예약자 이메일·관리 토큰·`paymentKey` 등 결제 query가 남지 않는지 확인한다.
4. 같은 브라우저의 `/reservations` 목록과 상세에서 저장된 취소 정책·서버의 예상 환불액을 확인한다. 취소 대화상자의 Tab/Shift+Tab 순환, Escape·돌아가기 뒤 취소 버튼 포커스 복귀, 실패 alert 포커스를 확인한다.
5. 관리자에서 테스트 변경 요청을 `AWAITING_PAYMENT`까지 만든 뒤 결제 링크를 생성·복사한다. 생성·만료 시각, 로컬 테스트 라벨, clipboard 거부 시 읽기 전용 URL의 focus/select fallback, 이메일·SMS가 자동 전송되지 않음을 확인한다.
6. 고객 링크를 열어 fragment가 즉시 제거되고 기존/변경 일정·객실·요금·총액·차액이 서버 값과 일치하는지 확인한다. `AWAITING_PAYMENT` 외 상태에서는 추가 결제 버튼이 노출되지 않아야 한다.

### 실제 Toss 테스트와 DB 대조

실제 카드·OTP·계정 인증은 사용자가 직접 수행한다. 화면 계약을 모두 확인한 뒤에만 Standard City 예약 240,000 KRW 승인 1건, Deluxe Ocean으로 증액 100,000 KRW 승인 1건, 원 조건으로 감액 100,000 KRW 부분 환불 1건을 테스트한다.

각 단계에서 브라우저의 상태와 `reservation`, `inventory_day`, `payment_provider_attempt`, `payment_transaction`, `payment_adjustment_attempt`를 대조한다. 예약·변경 상태, 일자별 재고, 원승인·추가결제·환불 금액과 멱등 재시도 결과를 기록하되 UUID, token, 개인 정보와 키는 마스킹한다.

## 이번 실행에서 확인하지 않은 항목과 한계

- Playwright·실제 브라우저·키보드·390×844 UI 동작, 4000/4001/4080 API 연결, Flyway migration과 readiness를 실행하지 않았다.
- PostgreSQL 통합 테스트, 예약·재고·거래·outbox의 실제 행과 금액/재고 delta 대조를 실행하지 않았다. 따라서 이번 기록에는 예약 ID, 상태 변화, 재고 수량, 결제·환불 transaction 증거가 없다.
- Toss SDK 실제 로딩, 결제 승인, 추가 결제, 부분 환불, provider 조회·webhook 및 실제 전달 성공을 실행하지 않았다. `TossReservationPaymentIntegrationTest`는 격리 `TEST_DATABASE_URL` PostgreSQL이 준비된 환경에서만 활성화하도록 보류돼 있다.
- 이메일·SMS 공급자, public HTTPS webhook, 수수료·회계 reconciliation은 검증하지 않았다.

이 제한 때문에 이번 결과는 코드 계약·컴파일·production build와 비DB payment client 단위 테스트의 증거이며, 실제 예약·재고·결제 정합성의 통과를 의미하지 않는다.
