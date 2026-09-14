# Task 4 고객 토스 테스트 결제 UI

## 구현

- 승인된 Task 4 설계를 그대로 적용했다. 신규 예약에는 서버 주문을 받아 v2 standard SDK를 여는 토스 테스트 결제 버튼을 추가하고, 기존 가상 성공·실패 시험은 유지했다. 서버 오류를 가상 결제 성공으로 대체하지 않는다.
- 결과 경로 `/reservations/{id}/payment-result`는 CMS에서 분리한다. 콜백 값은 메모리에서만 읽고 API 호출 전에 query를 제거한다. 예약 권한은 기존 sessionStorage token을 사용하며 권한이 없으면 승인 요청하지 않는다. 새로고침은 승인 재실행 없이 상태만 조회한다.
- SDK에는 서버 주문·금액·테스트 client key·자사 결과 주소만 전달한다. 라이브 키, 잘못된 금액, 외부/비허용 복귀 URL을 차단한다. 로더 Promise를 공유하고 15초 시간 제한을 두었다. 카드 입력 필드는 만들지 않았다. HTML 및 SDK script에 no-referrer를 적용했다.
- 변경 결제는 기존 fragment→HttpOnly Strict 세션 교환을 유지하고 자사 `/current/toss/{checkout,confirm,status}`로 처리한다. React StrictMode에서도 최초 교환/승인 Promise를 공유한다. 비민감 시작 표시만 sessionStorage에 남겨 UNKNOWN 새로고침 후 재결제 버튼 노출을 막는다. 새 링크 교환 시 이전 표시를 초기화한다.
- Java DTO와 기존 고객 날짜 타입의 불일치 `targetCheckIn/targetCheckOut`를 실제 `checkIn/checkOut`에 맞췄다.
- UNKNOWN을 완료로 표시하지 않으며 추가 결제 성공과 예약 변경 최종 완료를 구분한다. 전체 취소 응답은 `CANCELLED`인 경우에만 완료 문구를 표시한다. 오류 초점과 44px 이상 버튼, 모바일 줄바꿈을 적용했다.
- 고객 전용 Playwright 설정은 테스트 동안 고객 4000만 시작/종료한다. 관리자 4001 및 API 4080 프로세스에 작업하지 않았다.

## 검증

- TDD RED: Toss helper 부재, 결과 route null 실패 확인. 브라우저 결과 화면 미구현·취소 pending 완료 문구 실패 2건 확인. 후속 변경 UNKNOWN 새로고침·라이브 키 오류 초점 실패 2건 확인 후 수정했다.
- `pnpm exec tsx src/lib/toss-payments.test.ts`, `customer-route.test.ts`, `reservation-change-payment-session.test.ts`, `website-preview.test.ts` 통과. preview는 25개 assertion 통과.
- `pnpm exec tsc -b`, `pnpm run build` 통과.
- `pnpm exec playwright test`: Chromium 11건 모두 통과(21.3초). 서버 주문 SDK 전달, 390px·키보드·이중 클릭, clean URL·권한·새로고침, UNKNOWN, 권한 유실, 실패 복귀, Strict 변경 세션·새로고침·만료, 라이브 키 차단, 기존 fake 이동, pending 취소, 저장 초안 결제 차단을 포함한다.
- `git diff --check` 통과. Windows LF/CRLF 및 Playwright NO_COLOR/FORCE_COLOR 경고는 기능 실패가 아니다.
- 재현 가능한 고객 테스트를 위해 tsx 및 Playwright dev dependency와 lockfile을 추가했다. 기존 설치 브라우저와 맞춰 Playwright 1.60.0을 사용했고 pnpm build script 허용은 esbuild로 한정했다.

## 미검증 및 다음 작업

- 브라우저 검증은 API/SDK mock 계약 검증이다. 실제 Toss sandbox 승인·카드사 창·부분 환불·외부 웹훅 검증을 의미하지 않는다. 실제 키 부재와 sandbox 검증 결과는 Task 5에서 별도 기록한다.
- 상위 작업의 독립 코드 검토와 필요한 수정·재검토를 기다린다.
- 기존 관리자 디렉터리의 고객 변경 결제 E2E fixture도 날짜 2필드를 실제 DTO로 맞추도록 상위 작업에 전달했다.
