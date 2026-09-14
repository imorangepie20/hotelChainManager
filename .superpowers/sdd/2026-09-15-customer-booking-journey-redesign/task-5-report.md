# Task 5 예약 변경 비교 결제·직원 링크 전달 UX 작업 기록

## 설계·구현 계획·완료 기준

- 기존 fragment → `HttpOnly; SameSite=Strict` 고객 세션 교환과 직원 결제 링크 발급 API를 유지한다. 공개 token·고객 개인정보·직원 정보는 URL 본문, 저장소, 화면 상태에 추가하지 않는다.
- 고객 결제 화면은 Spring Boot가 반환한 이전·변경 일정, 객실·요금, 기존·변경 총액과 차액만 나란히 표시한다. 브라우저가 가격·차액·결제 가능 상태를 계산하거나 상태를 전이하지 않는다.
- `AWAITING_PAYMENT`, `READY_TO_APPLY`, `APPLYING`, `COMPLETED`, `EXPIRED`, `RECONCILIATION_REQUIRED`는 성공으로 뭉개지 않고 각각 화면에 남기며, 결제 시작은 `AWAITING_PAYMENT`에서만 노출한다.
- 직원 화면은 링크 생성 중·준비·복사 완료·만료를 구분하고, clipboard 접근이 거부되어도 읽기 전용 URL을 포커스해 선택·전달할 수 있게 한다. 자동 이메일·문자 전달은 제공하지 않는다.
- 완료 기준은 전후 비교와 차액의 표시, 상태별 결제 행동 제한, 복사 결과와 키보드 선택 가능한 URL, 고객/관리자 TypeScript 및 고객 production build, API 컴파일과 비DB 결제 클라이언트 테스트의 통과다. 브라우저·실제 API/DB·Toss 검증은 사용자 범위다.

## 변경 내용

- 고객 변경 결제 응답에 이전 일정·객실 유형·요금제·기존 총액과 대상 총액·차액을 additive하게 추가했다. 조회 SQL은 변경 요청과 불변 견적의 이전·대상 값을 읽을 뿐 상태를 바꾸지 않는다.
- 고객 결제 화면은 `기존 예약`과 강조된 `변경 예약`을 나란히 보여 주고, 서버 차액을 `추가 결제` 등으로 표시한다. 만료·조정 필요·완료를 포함해 서버 상태를 번역하며 중복 checkout을 숨긴다.
- 직원 패널은 서버 링크의 생성 시각·만료 시각과 로컬 테스트 라벨을 표시한다. 복사 성공은 `role=status`로 알리고 실패는 `role=alert`로 URL 직접 선택을 안내한다.
- 고객·직원 Chromium 계약을 추가해 fragment 제거, 전후 비교, 만료·조정·완료의 결제 차단, 생성 시각·만료 시각, clipboard 성공·실패 fallback을 기록했다.

## RED·자동 검증

```powershell
cd apps/web
node --experimental-strip-types src/lib/reservation-change-payment-state.test.ts
```

상태 계약 구현 전에는 `ERR_MODULE_NOT_FOUND`로 실패했다. 최소 상태 계약을 추가한 뒤 다음을 실행했다.

```powershell
cd apps/web
node --experimental-strip-types src/lib/reservation-change-payment-state.test.ts
node --experimental-strip-types src/lib/reservation-management-state.test.ts
pnpm exec tsc -b
pnpm run build

cd ../../SDTPL_ADM
pnpm exec tsc --noEmit

cd ../services/api
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q -DskipTests test-compile
.\mvnw.cmd -q '-Dtest=TossPaymentsHttpClientTest,TossPaymentsPropertiesTest' test
```

모든 명령은 exit code 0으로 끝났다. 고객 build는 2,916개 모듈을 변환했다. 예약 변경 서버의 직접 통합 테스트는 DB를 사용하므로 이번 사용자 소유 검증 범위에서 실행하지 않았다.

## 사용자 브라우저 검증 시나리오

1. 테스트 예약 변경을 추가 결제 `AWAITING_PAYMENT` 상태까지 진행하고, 직원 상세에서 링크를 만든다. 생성·만료 시각과 `로컬 테스트 결제 링크` 라벨을 확인하고, 복사 뒤 `복사했습니다` 상태가 읽히는지 확인한다.
2. clipboard 권한을 막은 환경에서 복사한다. 오류 안내 뒤 읽기 전용 URL이 포커스로 선택되고 키보드로 복사·전달할 수 있는지 확인한다. 이메일·문자가 자동 전송되지 않는지도 확인한다.
3. 고객 링크를 새 탭에서 열어 fragment가 즉시 제거되는지, URL에 예약 번호·고객 정보·결제값이 남지 않는지 확인한다. 기존/변경 일정·객실·요금·총액과 `추가 결제` 차액이 서버 값과 일치하는지 확인한다.
4. fake provider 상태를 `READY_TO_APPLY`, `APPLYING`, `COMPLETED`, `EXPIRED`, `RECONCILIATION_REQUIRED`로 각각 확인한다. `AWAITING_PAYMENT` 외에는 결제 버튼이 없고 만료·조정 필요 결과가 화면에 남는지 확인한다.
5. 데스크톱과 390×844에서 가로 스크롤 없이 비교 행과 금액이 줄바꿈되는지, Tab 순서·44px 결제 버튼·가시 focus가 유지되는지 확인한다.

## 미검증·후속 작업

- Playwright, 실제 브라우저, 라이브 API·DB, 실제 Toss 추가 결제·부분 환불과 webhook은 실행하지 않았다.
- 실제 provider 계약, secret, 서명 webhook, 이메일·문자 자동 전달, 수수료·회계 reconciliation은 Task 5 범위 밖이며 추가하지 않았다.
- 만료된 fragment를 새 고객 세션으로 교환하는 동작은 기존 서버 보안 정책대로 거부된다. 이미 열린 고객 세션에서 보이는 terminal 상태는 서버 응답을 표시하며, 실제 만료 시나리오는 위 사용자 브라우저 검증으로 확인한다.

## 수정 라운드 1

- 고객 결제 화면은 `AWAITING_PAYMENT`·`READY_TO_APPLY`·`APPLYING`에서만 2초 단일 in-flight poll을 수행한다. focus, visible, BFCache `pageshow` 복귀에도 현재 상태를 다시 읽으며 terminal 상태와 unmount에서는 timer와 event listener를 해제한다.
- 고객 세션의 `401`/`404`는 서버 오류로 표시하지 않고 `EXPIRED` terminal 상태로 바꾼다. 이전 비교를 이미 읽은 경우에는 그 내용을 유지한다. fake provider의 명시적 실패 `CANCELLED`도 별도 실패 안내와 직원에게 새 링크를 요청할 다음 행동으로 표시한다.
- 차액 helper는 한국어 표시 문자열 대신 `CHARGE`·`REFUND`·`NONE` 의미값을 반환하고, 고객 화면이 현재 locale로 문구를 결정한다.
- 직원 panel은 request poll 뒤 `EXPIRED`가 되어도 기존 URL·만료 사유를 유지한다. 새 변경 요청을 시작해 새 결제 링크를 만들 수 있고, clipboard 실패 시 ref로 읽기 전용 URL을 focus·select한다.
- `ReservationChangeSettlementIntegrationTest`에는 고객 current JSON의 이전 일정·객실/요금·기존 총액, 대상 일정·총액과 차액 assertion을 추가했다. 이 DB 통합 테스트는 사용자 지시에 따라 컴파일만 하고 실행하지 않았다.

### 수정 라운드 1 검증

```powershell
cd apps/web
node --experimental-strip-types src/lib/reservation-change-payment-state.test.ts
node --experimental-strip-types src/lib/reservation-management-state.test.ts
pnpm exec tsc -b
pnpm run build

cd ../../SDTPL_ADM
node --experimental-strip-types src/lib/reservation-change-link-state.test.ts
pnpm exec tsc --noEmit

cd ../services/api
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q -DskipTests test-compile
.\mvnw.cmd -q '-Dtest=TossPaymentsHttpClientTest,TossPaymentsPropertiesTest' test
```

위 명령은 모두 exit code 0이다. 관리자 순수 test의 Node 실행은 package가 CommonJS type을 명시하지 않아 module 재해석 warning만 출력하며 test와 TypeScript 검사는 통과했다. Playwright, 브라우저, 라이브 API·DB, DB 통합 테스트와 PG·Toss는 실행하지 않았다.
