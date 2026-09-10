# 첫 예약 흐름 구현 계획

> 실행자는 `executing-plans` 스킬을 사용해 작업별 구현과 검증을 수행한다. 체크박스는 실제 검증 후 갱신한다.

**Goal:** 속초 지점의 검색 → 임시 확보 → 테스트 결제 → 예약 조회·취소를 실제 PostgreSQL과 연결한다.

**Architecture:** 고객 React 앱은 Spring Boot API만 호출한다. Spring Boot가 가격·일자 재고·예약·모의 결제를 하나의 트랜잭션 경계 안에서 관리한다. 개발·테스트 DB는 별도 서비스와 볼륨으로 분리한다.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Maven 3.9.16 Wrapper, PostgreSQL 16.15, React·TypeScript, Docker Compose. 사용자 요청으로 로컬 JDK 21과 Python 3.12를 설치했다. 로컬 Java 개발은 Maven Wrapper를 제공하고 Docker 빌드도 지원한다. Maven Central과 공식 이미지 레지스트리에서 확인한 버전과 이미지 digest를 고정한다.

**Spec:** [예약 설계](../specs/2026-09-10-reservation-foundation-design.md)

## 공통 제약

- 고객 4000, 관리자 4001, API 4080, AI 9000.
- 국내 지점 속초·설악산·제주도. 첫 연결 지점은 속초, 최종 MVP는 세 지점이다.
- 객실 유형 재고와 실제 객실 번호를 분리한다. 이 계획에 실제 객실 배정은 포함하지 않는다.
- 날짜는 Asia/Seoul 기준 체크인 이상·체크아웃 미만. 금액은 KRW 정수, 만료 시각은 UTC.
- 확보 TTL은 초기 개발 기본값 10분으로 설정 가능하게 제공한다. 제품 정책 확정과 구분한다.
- AI 서비스·직원 운영·본사 통계·이메일 복구는 후속 계획이며 이번 완료 조건에 포함하지 않는다.
- 기존 SDTPL_ADM은 독립 .git이 있는 저장소다. 이를 삭제하거나 루트에서 git add .로 암묵적 gitlink를 만들지 않는다. 관리자 통합 시 소스 보관 방법을 별도 결정한다.
- 실제 계정·DB·비밀값·타 프로젝트 소스를 사용하지 않는다.

## 파일과 책임

| 파일·디렉터리 | 책임 |
|---|---|
| compose.yaml, .env.example, .gitignore | 독립 실행 환경과 비밀값 제외 |
| services/api/pom.xml, Dockerfile | Java 빌드와 실행 |
| services/api/src/main/resources/application.yml | 포트·DB·TTL 설정 |
| services/api/src/main/resources/db/migration/ | Flyway 스키마 |
| services/api/src/main/java/team/hotelchain/HotelApplication.java | 애플리케이션 진입점 |
| 위 Java 루트 아래 hotel/, pricing/, inventory/, reservation/, payment/ | 업무별 컨트롤러·서비스·저장소 |
| services/api/src/test/java/team/hotelchain/ | 단위·실제 DB 통합 테스트 |
| apps/web/src/features/search/, reservation/ | 고객 검색과 예약 화면 |
| apps/web/src/lib/api.ts | HTTP와 오류 코드 처리 |
| apps/web/e2e/reservation.spec.ts | 실제 API 기반 고객 E2E |

## 1. 독립 DB·API 기동

산출물: PostgreSQL 연결과 Flyway 마이그레이션이 확인되는 API.

- [x] 루트 .gitignore에 `.env`, `.env.*`, `!.env.example`, `**/node_modules/`, `**/target/`, `.logs/`를 추가한다. 기존 파일은 보존한다.
- [x] compose.yaml의 프로젝트 이름을 `hotel-chain-manager`로 지정한다. `db`는 전용 영구 볼륨, `db-test`는 tmpfs를 사용한다. 로컬 Maven 테스트를 위해 DB 포트 55432·55433은 127.0.0.1에만 바인딩한다. 사용자·비밀번호는 .env로 주입한다.
- [x] Java 21 Maven 공식 이미지와 PostgreSQL 이미지의 digest를 기록한다. API는 컨테이너 내부와 호스트 모두 4080을 사용하고 호스트 바인딩은 127.0.0.1로 제한한다.
- [x] Spring Web·Validation·JDBC·Actuator·Flyway·PostgreSQL·Test 의존성을 가진 pom.xml과 최소 기동 코드를 작성한다.
- [x] `DatabaseReadinessTest.java`에서 `SELECT current_database()`가 `hotel_chain_test`인지 검사한다. DB가 준비되지 않은 상태에서 연결 거부로 실패하는 RED를 확인했다.
- [x] DB 준비 이후 테스트를 실행해 통과시키고, `/actuator/health/readiness`가 UP이며 상세 자격 정보를 노출하지 않는 것을 확인한다. DB 장애 시 DOWN 검증은 응답 지연 원인을 수정했으나 반복 테스트를 줄이기 위해 다음 운영 검증으로 남겼다.

계획 명령(파일 생성 이후 실행):

```powershell
docker compose config --quiet
docker compose up -d db db-test
services\api\mvnw.cmd -B test
docker compose up -d --build api
Invoke-RestMethod http://localhost:4080/actuator/health/readiness
```

검증 기준: 테스트는 테스트 DB만 사용한다. API 정상 기동과 DB readiness를 확인하고 기존 다른 Compose 서비스에 영향이 없어야 한다.

## 2. 지점·객실 유형·날짜별 검색

파일: `V1__hotel_inventory.sql`, `hotel/HotelController.java`, `inventory/AvailabilityService.java`, `pricing/PricingService.java`, `AvailabilityIntegrationTest.java`.

계약:

```text
GET /api/hotels
→ [{id, name, region, timezone}]
GET /api/availability?hotelId=...&checkIn=2026-10-01&checkOut=2026-10-03&adults=2&children=0&rooms=1
→ {offers:[{roomTypeId,ratePlanId,remaining,nightlyPrices:[{date,amount}],total,currency,policyVersion}]}
```

- [x] 스키마에 hotel, room_type, rate_plan, rate_day, inventory_day를 만든다. 날짜·객실 유형 고유 제약과 음수 금지 CHECK, `held + confirmed <= capacity` 제약을 둔다.
- [x] AvailabilityIntegrationTest에 중간 날짜 매진, 재고 행 누락, 수용 인원 초과, 잘못된 날짜 범위, 일별 요금 합산을 작성하고 RED를 확인한다.
- [x] 검색은 날짜 전체가 존재할 때만 offer를 반환한다. 판매 가능 수량은 숙박 기간의 최소 잔여 수량으로 계산한다. 체크아웃 날짜는 포함하지 않는다.
- [x] 테스트 fixture는 고정 날짜를 사용한다. 개발 시드는 dev 프로필에서만 실행하고 속초 3개 유형과 현재 날짜부터 90일분을 생성한다. 설악산·제주도는 지점 메타데이터를 넣되 재고 없는 날짜는 판매 불가다.
- [x] 로컬 Maven Wrapper로 AvailabilityIntegrationTest 5개 통과를 확인한다.

## 3. 임시 확보와 예약 접근

파일: `V2__reservations.sql`, `reservation/ReservationController.java`, `ReservationService.java`, `ReservationRepository.java`, `ReservationAccess.java`, `ReservationIntegrationTest.java`.

계약:

```text
POST /api/reservations
Idempotency-Key: 생성 요청 고유값
X-Reservation-Token: 브라우저가 생성한 32바이트 난수의 Base64URL 값
{roomTypeId,ratePlanId,checkIn,checkOut,adults,children,rooms,expectedTotal,guest:{name,email}}
→ 201 {id,status,expiresAt,total,currency,nightlyPrices,cancellationPolicy,guest}
GET /api/reservations/{id}
X-Reservation-Token: 관리 토큰
→ {id,status,expiresAt,total,nightlyPrices,cancellationPolicy}
```

- [x] 예약·예약일별 금액·요청 멱등성 테이블을 생성한다. 정책 스냅샷은 구조화된 JSON으로 보존한다.
- [x] 테스트는 마지막 1객실에 두 스레드가 동시에 요청해 하나만 성공하는지, 여러 날짜 중 하나 부족하면 확보가 전부 롤백되는지 검사한다.
- [x] 32바이트 난수 관리 토큰을 클라이언트에서 생성·요청 헤더로 전달하는 방식으로 계약을 보완한다. 서버는 해시만 저장한다. 응답 손실 후 같은 토큰·멱등 키로 재시도할 수 있도록 기존 설계와 API 계약도 함께 갱신한다. 토큰을 URL·로그에 넣지 않는다.
- [x] 생성 멱등 키는 토큰 해시와 함께 범위를 지정한다. 같은 키·같은 요청은 동일 예약 결과, 다른 요청 내용은 409다.
- [x] 날짜 순서로 재고 행을 잠근 뒤 모든 날짜 재고와 서버 재계산 금액을 검사한다. 가격 변경은 `PRICE_CHANGED`, 재고 부족은 `SOLD_OUT` 409를 반환한다.
- [x] 다른 토큰·없는 예약 모두 404로 반환한다. 검증되지 않은 예약자 정보는 응답하지 않는다.
- [x] 로컬 Maven Wrapper로 `ReservationIntegrationTest` 5개 통과를 확인한다.

위 토큰 계약 보완은 구현 전 설계 변경 기록에 남길 사항이다. 서버 응답에만 토큰을 발급하면 응답 유실 시 해시만 저장한 서버에서 토큰을 재발급할 수 없다는 문제를 해결한다.

## 4. 테스트 결제와 확보 만료

파일: `payment/TestPaymentController.java`, `TestPaymentService.java`, `reservation/ReservationExpiryJob.java`, `PaymentExpiryIntegrationTest.java`.

계약:

```text
POST /api/reservations/{id}/test-payment
X-Reservation-Token, Idempotency-Key
{outcome:"SUCCESS"|"FAILURE"}
→ {reservationId,status,paymentStatus}
```

- [x] 테스트 전용 프로필에서만 모의 결제 엔드포인트를 활성화한다.
- [x] 성공 시 held 감소·confirmed 증가, 실패 시 PENDING_PAYMENT 유지, 중복 성공 시 재고가 다시 변하지 않는 테스트를 먼저 작성한다.
- [x] 주입된 Clock으로 만료 정각과 결제·만료 경합 후 재고 총합 보존을 확인한다. 만료 직전·직후의 별도 반복 사례는 같은 경계 조건으로 대체했다.
- [x] 모든 전이는 예약 행 → 날짜 순서 재고 행 잠금을 사용한다. 만료 정각은 EXPIRED이며 결제할 수 없다. 결제 요청에서 발생한 만료 처리도 커밋되도록 예외 롤백 경계를 구분한다.
- [x] 만료 작업은 최대 100건의 작은 배치와 예약별 트랜잭션으로 처리한다. 재시도 시 이미 전이한 예약은 변경하지 않는다.
- [x] 로컬 Maven Wrapper로 `PaymentExpiryIntegrationTest` 5개 통과를 확인한다.

## 5. 취소·환불과 정책 보존

파일: `payment/TestRefundService.java`, `reservation/CancellationService.java`, `CancellationIntegrationTest.java`.

계약:

```text
POST /api/reservations/{id}/cancel
X-Reservation-Token, Idempotency-Key
→ {reservationId,status,refundAmount,currency}
```

- [x] 개발 정책은 체크인 전날 18:00 Asia/Seoul까지 전액 환불, 이후 취소 불가로 명시한다. 실제 판매 정책 확정과 구분하고 예약 당시 스냅샷을 사용한다.
- [x] 정책 원본을 변경해도 기존 예약 환불액이 변하지 않는 테스트, 취소 반복 시 한 번만 재고 반환, 환불 실패 시 CONFIRMED 유지 테스트를 작성한다.
- [x] 환불 성공과 재고 반환을 같은 트랜잭션에서 처리한다. 환불 실패는 `REFUND_FAILED`, 취소 불가는 `CANCELLATION_NOT_ALLOWED`로 구분한다.
- [x] 로컬 Maven Wrapper로 `CancellationIntegrationTest` 4개 통과를 확인한다.

## 6. 고객 화면과 실제 API 연결

파일: `apps/web/package.json`, `vite.config.ts`, `src/lib/api.ts`, `src/features/search/SearchPage.tsx`, `src/features/reservation/BookingPage.tsx`, `ManageBookingPage.tsx`, `e2e/reservation.spec.ts`.

- [x] 고객 UI의 최소 디자인을 작성한다. 가상 호텔임을 표시하고 속초 객실 사진·시설·검색·요금제 비교·예약 정보·테스트 결제 단계로 구성한다. 실재하지 않는 호텔 주소나 시설 정보를 사실로 표기하지 않는다.
- [x] API 프록시 `/api`를 4080으로 설정하고 Vite는 4000 strictPort로 실행한다.
- [ ] Playwright 테스트에서 날짜 검색 → offer 선택 → 예약자 입력 → 테스트 결제 → 예약 조회 → 취소를 실제 API와 테스트 DB로 수행한다. 프런트 가격 재계산이나 API 응답 mocking으로 성공을 대체하지 않는다.
- [x] 토큰은 현재 브라우저 세션 저장소로 보관하고 URL에 포함하지 않는다. 세션을 잃으면 조회 복구가 안 된다는 MVP 제한을 예약 완료 화면에 안내한다.
- [x] 409 가격 변경은 결과 초기화로, 만료는 재검색으로 연결한다. 실패 결제는 재시도 가능하며 버튼 중복 클릭을 막고 서버 멱등 검증도 유지한다.
- [x] API 요청 실패·빈 검색·로딩·완료·취소 상태를 구현하고 필수 입력은 브라우저 검증과 연결한다.
- [ ] 데스크톱 1440×900과 모바일 390×844에서 날짜 입력·버튼·키보드 접근·가로 넘침을 검사한다.

계획 명령:

```powershell
pnpm --dir apps/web install --frozen-lockfile
pnpm --dir apps/web build
pnpm --dir apps/web test:e2e
docker compose run --rm api-test mvn -B verify
```

첫 설치에서는 lockfile을 생성하고 이후 frozen-lockfile을 적용한다. 테스트 DB는 disposable이라는 전제에서도 대상 이름을 확인하고, 개발 DB에 초기화를 실행하지 않는다.

## 계획 검토와 완료 기준

- API 여섯 개, 전 날짜 확보, 정책·금액 보존, 멱등성, 만료 경합, 토큰 접근, 테스트 결제·환불 실패, 고객 E2E를 위 작업에 배정했다.
- 작업 3의 토큰 발급 주체 변경은 구현 시 예약 설계와 함께 수정한다.
- 실제 구현 전에 각 작업의 테스트 fixture와 코드 서명을 구체화하고 RED → GREEN 증거를 변경 기록에 남긴다.
- 구현하지 않은 항목은 체크하지 않는다. 위 명령은 아직 구성 전인 계획 명령이며 현재 실행 가능하다고 주장하지 않는다.
- 완료 시 현재 상태·실행 안내·변경 기록을 갱신한다. 이후 직원 운영 계획을 별도로 작성한다.
