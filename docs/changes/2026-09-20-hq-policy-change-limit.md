# 본사 공통 정책 - 예약 변경 승인 한도 변경·이력 조회

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.
> 구현 계획: [본사 공통 정책 - 변경 승인 한도 변경·이력 조회](../superpowers/plans/2026-09-20-hq-policy-change-limit.md)

## 변경 요약

- `admin-menu-roadmap.md` 5번 `공통 정책 관리`의 둘째 단계를 구현했다. 취소 정책 조회·변경은 끝났고, `ReservationChangePolicy.directLimitKrw()`가 `application.yml` 고정값을 쓰고 있어 본사가 한도를 바꿀 수단이 없었다.
- `PUT /api/staff/policies/change-limit`가 본사 세션으로 지점 직접 승인 한도를 변경한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다.
- 한도는 0원 이상 10,000,000원 이하다. 위반은 400이고 멱원 키 누락도 400이다.
- 멱원은 취소 정책과 같은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 revision을 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 같은 결과를 돌려준다.
- `GET /api/staff/policies/revisions`가 정책 변경 이력을 최신순으로 반환한다. SELECT만 사용한다. `limit` 1~100, `offset` 0 이상이고 위반은 400이다.
- 변경은 항상 새 `policy_revision` 행을 append 한다. 진행 중인 예약 변경 요청은 이미 저장된 `approval_limit_krw`를 그대로 쓴다.

## 서비스별 변경

### services/api

- `db/migration/V49__policy_change_limit_value.sql`(신규): additive. `policy_revision`에 `value_krw BIGINT`를 추가한다. 취소 정책 행은 `NULL`이고 변경 승인 한도 행만 쓴다. 기존 행·제약을 변경하지 않는다.
- `policy/CurrentPolicy`: `change-approval` 키의 최근 revision을 읽어 `changeApprovalDirectLimitKrw()`를 제공한다. 없으면 `application.yml`의 `reservation.change.direct-limit-krw` 기본값을 쓴다.
- `reservation/ReservationChangePolicy.directLimitKrw()`: 설정 고정값 대신 `CurrentPolicy`에서 읽은 현재값을 반환한다.
- `policy/PolicyQueryService`: `current()`의 `changeApprovalDirectLimitKrw`를 `CurrentPolicy`에서 채운다. `revisions(token, limit, offset)`이 `policy_revision`을 최신순으로 읽어 이전·현재 요약과 처리 직원을 포함한다.
- `policy/PolicyCommandService.updateChangeApprovalLimit(token, idempotencyKey, request)`: 멱원 키·요청 지문·`pg_advisory_xact_lock`으로 중복·동시 변경을 막는다.
- `policy/PolicyController`: `PUT /api/staff/policies/change-limit`, `GET /api/staff/policies/revisions`를 추가했다.
- `policy/ChangeApprovalLimitUpdateRequest`(신규)·`ChangeApprovalLimitUpdateResponse`(신규)·`PolicyRevisionView`(신규)·`PolicyRevisionsView`(신규).

### SDTPL_ADM

- `src/lib/staff-api.ts`: `updateChangeApprovalLimit`·`getPolicyRevisions`와 `UpdatedChangeApprovalLimit`·`PolicyRevision`·`PolicyRevisions` 타입을 추가했다.
- `src/components/hotel-admin/policies.tsx`: 예약 변경 승인 카드에 현재값·변경 대화상자·변경 이력 표와 페이징을 추가했다.
- `e2e/policies.spec.ts` 6종 추가: 한도 변경·반영, 서버 검증 실패 시 대화상자 유지, 이력 표시·페이징, 지점 직원 안내.

### policies.tsx 대화상자 입력 복구

- 한도 입력이 `<input type="number" required min={0} max={10000000}>`였다. 브라우저가 `max` 초과로 폼 제출을 막아 서버 검증 응답이 도달하지 않았고, `keeps the limit dialog open with a server validation message` E2E가 실패했다.
- `type="text"` + `inputMode="numeric"`으로 바꾸고 정수 파싱만 클라이언트가 확인한다. 범위 판단은 서버에 둬서 검증 메시지를 대화상자에서 본다. 서버 검증 실패 시 대화상자를 닫지 않는다는 계획과 기존 주석을 따랐다.

## 검증

- API `mvnw -Dtest=PolicyIntegrationTest,StaffAccountIntegrationTest test` **43건**(정책 22 + 직원 21) 종료 코드 0. 한도 조회·변경, 멱원 재호출, 새 키 같은 내용, 범위 위반 400, 키 누락 400, 지점 직원 403, 세션 없음 401, 이력 최신순·페이징, 진행 중 요청의 저장된 한도 유지, 비밀번호 재발급 201·쿨다운 409·없는 직원 404·새 비밀번호 로그인.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 6건 `react-hooks/set-state-in-effect`, 기존 `staff-accounts`와 같은 패턴).
- Playwright `policies` 11건 + `staff-accounts` 10건, 총 **21건** 종료 코드 0. 인접 suite `audit`·`inventory-viewer`·`hotel-catalog`·`ai-operations`·`operations-report`·`admin-navigation` **33건**도 종료 코드 0.
- 라이브: compose `api` 재빌드 뒤 health 200. 본사 세션으로 조회 200(변경 한도 100,000원), 변경 201 `revision=1 created=true`, 같은 키 재호출 200 `created=false`, 새 키 같은 내용 200 `created=false`, 10,000,001원 400, 이력 200 `totalCount=1`, 지점 직원 403, 세션 없음 401. 비밀번호 재발급 201 `created=true cooldownSeconds=300`, 같은 키 재호출 200 `created=false temporaryPassword=null`, 쿨다운 내 새 키 409 `STAFF_PASSWORD_RESET_COOLDOWN`, 발급된 임시 비밀번호로 로그인 201.
- 검증용 revision 2건과 재발급 기록 1건은 모두 삭제해 기본값(한도 100,000원, revision 0)으로 복원했다.

## 미검증 항목

- Vite production build, 전체 backend suite, 전체 Playwright 회귀는 실행하지 않았다. 변경 범위가 정책 한도·이력 조회와 재발급 동선에 국한되므로 사용자가 요청할 때까지 반복하지 않는다.
- 관리자 4001 브라우저에서의 한도 변경·이력 화면 전환은 이번 세션에 확인하지 않았다. Playwright 라우트 모킹으로 동작만 검증했다.
- 환불 규칙·지점별 정책 예외·승인 TTL 런타임 변경·정책 변경의 감사 메뉴 통합·영문 전환은 범위 밖이다.
