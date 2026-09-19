# 본사 공통 정책 - 변경 승인 한도 변경·이력 조회

> 상태: 구현·검증 완료. 최종 갱신: 2026-09-20. [변경 기록](../../changes/2026-09-20-hq-policy-change-limit.md)

## 배경

- `admin-menu-roadmap.md` 5번 `공통 정책 관리`에서 취소 정책 조회·변경은 끝났다. 남은 첫 항목은 예약 변경 승인 한도 변경과 정책 변경 이력 조회다.
- `ReservationChangePolicy.directLimitKrw()`가 `application.yml`의 `reservation.change.direct-limit-krw` 고정값을 쓴다. 본사가 값을 볼 수는 있지만 바꿀 수단이 없어 환경 변수와 재배포 없이 한도를 조정할 수 없다.
- `policy_revision`은 취소 정책만 기록한다. 본사가 정책을 바꾼 이력이 쌓이지 않으므로 언제 누가 바꿨는지 볼 수 없다.

## 완료 기준

1. `PUT /api/staff/policies/change-limit`가 본사 세션으로 지점 직접 승인 한도를 변경한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다.
2. 한도는 0원 이상 10,000,000원 이하다. 위반은 400이고 멱원 키 누락도 400이다.
3. 멱원은 취소 정책과 같은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 revision을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 결과를 돌려준다.
4. 변경은 항상 새 `policy_revision` 행을 append 한다. 기존 revision을 덮어쓰지 않는다.
5. `GET /api/staff/policies`의 `changeApprovalDirectLimitKrw`가 DB의 현재값을 반환한다. revision이 없으면 코드·`application.yml` 기본값을 쓴다.
6. `GET /api/staff/policies/revisions`가 정책 변경 이력을 최신순으로 반환한다. SELECT만 사용한다. `limit` 1~100, `offset` 0 이상이고 위반은 400이다.
7. 진행 중인 예약 변경 요청은 이미 저장된 `approval_limit_krw`를 그대로 쓴다. 한도 변경은 신규 생성·재견적부터 적용된다.
8. 관리자 `/dashboard/policies`의 예약 변경 승인 카드에 현재값·변경 대화상자·변경 이력 표를 추가한다. 서버 검증 실패 시 대화상자를 닫지 않는다.
9. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다.

## 구현 범위

### services/api

- V49 additive 마이그레이션: `policy_revision`에 `value_krw BIGINT`를 추가한다. 취소 정책 행은 `NULL`이고 변경 승인 한도 행만 쓴다. 기존 행·제약을 변경하지 않는다.
- `CurrentPolicy`: `change-approval` 키의 최근 revision을 읽어 `long changeApprovalDirectLimitKrw()`를 제공한다. 없으면 `application.yml` 기본값을 쓴다.
- `ReservationChangePolicy.directLimitKrw()`가 `CurrentPolicy`에서 읽은 현재값을 반환한다.
- `PolicyQueryService.current()`: `changeApprovalDirectLimitKrw`를 `CurrentPolicy`에서 읽어 `PolicyView`에 채운다.
- `PolicyQueryService.revisions(token, limit, offset)`: `policy_revision`을 최신순으로 읽고 이전·현재 요약과 처리 직원을 포함한다.
- `PolicyCommandService.updateChangeApprovalLimit(token, idempotencyKey, request)`: 멱원 키·요청 지문·`pg_advisory_xact_lock`으로 중복·동시 변경을 막는다.
- `PolicyController`: `PUT /api/staff/policies/change-limit`, `GET /api/staff/policies/revisions`.
- `ChangeApprovalLimitUpdateRequest`·`ChangeApprovalLimitUpdateResponse`·`PolicyRevisionView`·`PolicyRevisionsView`(신규).

### SDTPL_ADM

- `staff-api.ts`: `updateChangeApprovalLimit`·`getPolicyRevisions`와 타입을 추가한다.
- `components/hotel-admin/policies.tsx`: 예약 변경 승인 카드에 변경 버튼·대화상자, 변경 이력 표를 추가한다.
- `e2e/policies.spec.ts`: 한도 변경·멱원·검증 실패·이력 표시 시나리오를 추가한다.

### 테스트

- `PolicyIntegrationTest`: 한도 조회·변경, 멱원 재호출, 새 키 같은 내용, 범위 위반 400, 키 누락 400, 지점 직원 403, 세션 없음 401, 이력 최신순·페이징, 진행 중 요청의 저장된 한도 유지.

## 미구현 예정 항목

- 환불 규칙(부분 환불 비율·수수료)과 지점별 정책 예외. 체인 공통 정책만 다룬다.
- 승인 TTL·보류 TTL의 런타임 변경. 진행 중인 승인 만료 시각에 미치는 영향이 커서 읽기 전용으로 둔다.
- 정책 변경의 감사 이력을 감사 메뉴(7번)와 통합. 본 메뉴 안에서만 본다.
- 영문 전환. 한국어 단일 언어다.
