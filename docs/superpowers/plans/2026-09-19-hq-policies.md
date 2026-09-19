# 본사 공통 정책 관리 첫 단계

> 상태: 구현 완료. 최종 갱신: 2026-09-20.

## 배경

- `admin-menu-roadmap.md` 5번 `공통 정책 관리`가 다음 대상이었다. 1·2·3번 메뉴의 읽기·첫 쓰기 동작은 끝났다.
- 취소 정책(`refundCutoffDaysBefore`·`refundCutoffLocalTime`)과 예약 변경 승인 한도(`direct-limit-krw`)가 코드와 `application.yml`에 고정돼 있었다. 본사가 화면에서 현재 값을 볼 수단이 없었다.
- 정책을 바꾸면 이미 확정된 예약의 `policy_snapshot`과 신규 예약에 다르게 적용되므로, 첫 단계에서는 **정책을 DB에서 읽어 노출**하고 본사가 **취소 정책만** 변경한다. 예약 변경 한도·TTL 변경은 영향이 커서 다음 작업으로 뺐다.

## 완료 기준

1. `GET /api/staff/policies`가 본사 세션으로 현재 정책(취소 마감 일수·마감 시각·시간대, 예약 변경 승인 한도, 정산 활성 여부, revision 수)을 반환한다. **완료.**
2. `PUT /api/staff/policies/cancellation`이 본사 세션으로 취소 마감 일수(1~30)와 마감 시각(`HH:MM`)을 변경한다. **완료.**
3. `HQ_ADMIN`만 호출 가능. 지점 직원 403, 잘못된 세션 401. **완료.**
4. 마감 일수는 1~30, 마감 시각은 `HH:MM` 형식. 위반은 400. **완료.**
5. 멱원 키(`Idempotency-Key`)로 같은 요청의 중복 변경을 막는다. **완료. 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 revision을 돌려준다.**
6. 변경 이력이 `policy_revision`에 쌓이고, 현재값은 항상 가장 최근 revision에서 읽는다. **완료.**
7. 신규 예약 생성 시 이 정책이 `policy_snapshot`에 반영된다. **완료. `ReservationService`가 `CurrentPolicy`에서 읽은 값으로 snapshot을 만든다.**
8. 관리자 4001 `/dashboard/policies`의 `공통 정책` 화면에서 현재값과 변경 폼을 쓴다. **완료.**
9. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다. **완료.**

## 구현 범위

### 서버 (services/api)

- `policy/CurrentPolicy`: `policy_revision`의 가장 최근 행을 읽는다. 없으면 `application.yml`의 `policy.cancellation.*` 기본값을 쓴다. SELECT만 사용한다.
- `policy/PolicyQueryService`: `current(token)`. `requireHeadquarters`로 본사만 허용하고 취소 정책·변경 승인 한도·정산 활성 여부를 읽기 전용으로 묶는다.
- `policy/PolicyCommandService`: `updateCancellation(token, idempotencyKey, request)`. `requireHeadquarters`로 본사만 허용하고 `pg_advisory_xact_lock(hashtextextended('cancellation', 0))`로 정책 키 단위 쓰기를 직렬화한다. 새 revision을 append 한다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 저장된 revision을 돌려준다. 키는 달라도 `staff_id`·정책 키·본문 SHA-256 지문이 같으면 같은 결과를 돌려준다.
- `policy/PolicyController`: `GET /api/staff/policies`, `PUT /api/staff/policies/cancellation`. 생성은 201, 멱원 재호출은 200에 `created=false`를 붙였다.
- `policy/CancellationPolicyUpdateRequest`가 일수·시각 검증을 담당한다. 0·31일, `25:00`·`6시` 형식 위반은 400이다.
- `reservation/ReservationService`가 `CurrentPolicy`에서 읽은 취소 정책으로 `policy_snapshot`을 만든다. 기존 하드코딩 `1`/`18:00`/`Asia/Seoul`을 제거했다.
- V48 additive 마이그레이션으로 `policy_revision` 테이블을 만든다. 기존 `reservation.policy_snapshot`을 변경하지 않는다.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`에 `getChainPolicies`·`updateCancellationPolicy`·`ChainPolicy`·`UpdatedCancellationPolicy` 타입을 추가했다. 403·기타 실패 안내를 나눴다.
- `components/hotel-admin/policies.tsx`: 취소 정책 카드와 예약 변경 승인 카드, 취소 정책 변경 대화상자. `HQ_ADMIN`만 버튼이 보이고, 성공하면 정책을 새로고침한다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- `nav.ts`의 `본사 관리` 그룹에 `공통 정책`을 추가했다. `HQ_ADMIN`에만 노출된다.

### 테스트

- `PolicyIntegrationTest` 11종: 본사 조회 200, 지점 403, 잘못된 세션 401, 본사 변경 201, 같은 키 재호출 같은 revision, 새 키 같은 내용 같은 revision, 다른 내용은 새 revision, 일수 0·31 400, 시각 `25:00`·`6시` 400, 멱원 키 누락 400, 지점 변경 403.
- `e2e/policies.spec.ts` 6종: 메뉴 노출 권한, 현재값 표시, 변경·반영·새로고침 유지, 서버 검증 실패 시 대화상자 유지, 지점 직원 안내, 장애 안내.

## 검증 결과

- API `mvnw -Dtest=PolicyIntegrationTest test` **11건** 종료 코드 0.
- API 예약·변경·직원·카탈로그·재고 집중 영역 **167건** 종료 코드 0.
- API `mvnw compile`·`test-compile` 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `policies` 6건을 포함해 관리자 7개 suite **33건** 종료 코드 0.
- 라이브: API 재빌드 뒤 V48 `success=t`, health `UP`. 본사 세션으로 조회 200(취소 1일 전·18:00·Asia/Seoul, 변경 한도 100,000원, 정산 활성), 변경 201, 같은 키 재호출 200 `created=false`, 새 키 같은 내용 200 `created=false`, 일수 31 400, 시각 `25:00` 400, 멱원 키 누락 400, 지점 직원 조회·변경 403, 세션 없음 401. 관리자 4001 브라우저에서 `체크인 1일 전` → 변경 → `체크인 3일 전` 전환과 새로고침 후 3일 유지를 확인했다. 검증용 revision 2건은 모두 삭제해 기본값으로 복원했다.

## 미구현 예정 항목

- 예약 변경 승인 한도·TTL 변경. 진행 중인 승인 요청에 미치는 영향이 크다. 현재값은 읽기 전용으로 노출한다.
- 지점별 정책. 첫 버전은 체인 공통 정책만 둔다.
- 정책 변경의 감사 이력 조회. revision은 쌓이지만 조회 화면은 다음 작업이다.
- 환불 규칙(부분 환불 비율 등). 결제 공급자 정책과 엮여 있다.
- 영문 전환. 한국어 단일 언어다.
