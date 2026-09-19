# 본사 공통 정책 조회·취소 정책 변경

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.
> 구현 계획: [본사 공통 정책 관리 첫 단계](../superpowers/plans/2026-09-19-hq-policies.md)

## 변경 요약

- `admin-menu-roadmap.md` 5번 `공통 정책 관리`의 첫 단계를 구현했다. 취소 정책과 예약 변경 승인 한도가 코드와 `application.yml`에 고정돼 있어 본사가 현재 값을 볼 수단이 없었다.
- `GET /api/staff/policies`가 취소 정책(마감 일수·마감 시각·시간대)·예약 변경 승인 한도·정산 활성 여부·revision 수를 반환한다. SELECT만 쓴다.
- `PUT /api/staff/policies/cancellation`이 취소 마감 일수(1~30)와 마감 시각(`HH:MM`)을 변경한다. `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 revision을 돌려준다. 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·정책 키·본문 SHA-256 지문이 같아 같은 결과를 돌려준다. 정책 키 단위 `pg_advisory_xact_lock`으로 동시 변경을 직렬화한다.
- V48 additive 마이그레이션으로 `policy_revision` 테이블을 만든다. 현재값은 항상 가장 최근 revision에서 읽고, 없으면 `application.yml`의 `policy.cancellation.*` 기본값을 쓴다. 기존 `reservation.policy_snapshot`을 변경하지 않는다.
- 변경은 항상 새 revision을 append 한다. 기존 revision을 덮어쓰지 않으므로 이미 확정된 예약의 취소 조건은 그대로 남고, `ReservationService`가 `CurrentPolicy`에서 읽은 값으로 신규 예약의 `policy_snapshot`을 만든다.
- 관리자 `/dashboard/policies`에 취소 정책 카드·예약 변경 승인 카드·변경 대화상자를 추가했다. `nav.ts`의 `본사 관리` 그룹은 `HQ_ADMIN`에만 노출되고, 다른 역할은 본사 전용 안내를 본다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.

## 서비스별 변경

### services/api

- `policy/CurrentPolicy`(신규): `policy_revision`의 가장 최근 행을 읽어 `CancellationPolicy`를 제공한다. 없으면 `policy.cancellation.refund-cutoff-days-before`(기본 1)·`refund-cutoff-local-time`(기본 `18:00`)·`Asia/Seoul`을 쓴다.
- `policy/PolicyQueryService`(신규): `current(token)`. `requireHeadquarters`로 본사만 허용하고 `ReservationChangePolicy`의 변경 승인 한도·정산 활성 여부를 함께 묶는다. revision 수는 `policy_revision` 행 수로 노출한다.
- `policy/PolicyCommandService`(신규): `updateCancellation(token, idempotencyKey, request)`. 멱원 키와 요청 지문으로 중복 변경을 막고 새 revision을 append 한다.
- `policy/PolicyController`(신규): `GET /api/staff/policies`, `PUT /api/staff/policies/cancellation`. 생성은 201, 멱원 재호출은 200에 `created=false`를 붙였다.
- `policy/CancellationPolicy`(신규), `policy/PolicyView`(신규), `policy/CancellationPolicyUpdateRequest`(신규), `policy/CancellationPolicyUpdateResponse`(신규).
- `reservation/ReservationService`: `CurrentPolicy`를 생성자 주입받아 `policy_snapshot`의 `timezone`·`refundCutoffDaysBefore`·`refundCutoffLocalTime`을 DB에서 읽은 값으로 채운다. 기존 하드코딩을 제거했다.
- `application.yml`: `policy.cancellation.refund-cutoff-days-before`·`refund-cutoff-local-time`을 환경 변수로 조정할 수 있게 했다.
- `db/migration/V48__policy_revision.sql`(신규): additive. `policy_revision` 테이블과 멱원 키·요청 지문·최근 revision 조회 인덱스.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `getChainPolicies`·`updateCancellationPolicy`·`ChainPolicy`·`CancellationPolicy`·`UpdateCancellationPolicyInput`·`UpdatedCancellationPolicy`를 추가했다. 403·기타 실패 안내를 나눴다.
- `src/components/hotel-admin/policies.tsx`(신규): 취소 정책 카드와 예약 변경 승인 카드, 취소 정책 변경 대화상자. `HQ_ADMIN`만 버튼이 보이고, 성공하면 정책을 새로고침한다.
- `src/app/(dashboard)/dashboard/policies/page.tsx`(신규).
- `src/lib/nav.ts`: `본사 관리` 그룹에 `공통 정책`(`/dashboard/policies`, `ScrollText` 아이콘)을 추가했다.
- `e2e/policies.spec.ts`(신규) 6종: 메뉴 노출 권한, 현재값 표시, 변경·반영·새로고침 유지, 서버 검증 실패 시 대화상자 유지, 지점 직원 안내, 장애 안내.

## 검증

- API `mvnw -Dtest=PolicyIntegrationTest test` **11건** 종료 코드 0. 본사 조회 200, 지점 403, 잘못된 세션 401, 본사 변경 201, 같은 키 재호출 같은 revision, 새 키 같은 내용 같은 revision, 다른 내용은 새 revision, 일수 0·31 400, 시각 `25:00`·`6시` 400, 멱원 키 누락 400, 지점 변경 403.
- API 예약·변경·직원·카탈로그·재고 집중 영역 **167건** 종료 코드 0. `ReservationService`의 snapshot 변경이 기존 예약·취소·변경 동작에 영향을 주지 않음을 확인했다.
- API `mvnw compile`·`test-compile` 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 `staff-accounts`·`hotel-catalog`와 같은 패턴).
- Playwright `policies` 6건, `admin-navigation` 2건, `staff-accounts` 6건, `hotel-catalog` 8건, `inventory-viewer` 6건, `inventory-mobile` 1건, `ai-operations` 4건, 총 **33건** 종료 코드 0.
- 라이브: API 재빌드 뒤 V48 `success=t`, health `UP`. 본사 세션으로 조회 200(취소 1일 전·18:00·Asia/Seoul, 변경 한도 100,000원, 정산 활성), 변경 201, 같은 키 재호출 200 `created=false`, 새 키 같은 내용 200 `created=false`, 일수 31 400, 시각 `25:00` 400, 멱원 키 누락 400, 지점 직원 조회·변경 403, 세션 없음 401. 관리자 4001 브라우저에서 `체크인 1일 전` → 변경 → `체크인 3일 전` 전환과 새로고침 후 3일 유지를 확인했다. 검증용 revision 2건은 모두 삭제해 기본값으로 복원했다.

## 미검증 항목

- Vite production build, 전체 backend suite, 전체 Playwright 회귀는 실행하지 않았다. 변경 범위가 정책 조회·취소 정책 변경과 그 snapshot에 국한되므로 사용자가 요청할 때까지 반복하지 않는다.
- 실제 예약 생성에 변경된 취소 정책이 `policy_snapshot`에 반영되는지는 브라우저 예약 흐름에서 확인하지 않았다. 통합 테스트로 `ReservationService`의 snapshot 생성 동작만 검증했다.
- 정책 변경의 감사 이력 조회 화면, 예약 변경 승인 한도·TTL 변경, 지점별 정책, 환불 규칙, 영문 전환은 범위 밖이다.
