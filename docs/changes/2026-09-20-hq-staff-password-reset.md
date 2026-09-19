# 본사 직원 계정 - 비밀번호 재발급

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.
> 구현 계획: [본사 직원 계정 - 비밀번호 재발급](../superpowers/plans/2026-09-20-hq-staff-password-reset.md)

## 변경 요약

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`의 둘째 단계를 구현했다. 조회·생성은 끝났고, 직원이 임시 비밀번호를 분실하면 `StaffDevAccountInitializer`의 환경 변수 비밀번호에 의존해야만 했다.
- `POST /api/staff/staff/{staffId}/password`가 본사 세션으로 새 16자 임시 비밀번호를 발급한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다. 없는 직원은 404, 멱원 키 누락은 400이다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`를 돌려주고, 응답 유실 뒤 새 키로 같은 대상 재발급을 시도하면 쿨다운 내에서 409 `STAFF_PASSWORD_RESET_COOLDOWN`로 거부한다. 비밀번호가 두 번 바뀌지 않는다.
- 임시 비밀번호 원문은 DB에 보관하지 않는다. BCrypt 해시만 저장하고 재호출은 `temporaryPassword: null`로 내려주므로, 이미 발급된 비밀번호는 다시 볼 수 없다는 안내를 UI가 제공한다.
- 재발급은 본인 계정도 포함한다. 세션은 `token_hash` 기반이고 비밀번호와 무관하므로 재발급이 기존 세션을 끊지 않는다.

## 서비스별 변경

### services/api

- `db/migration/V50__staff_password_reset_idempotency.sql`(신규): additive. `staff_account_command`에 `kind VARCHAR(32) NOT NULL DEFAULT 'CREATE'`·`password_reset_at TIMESTAMPTZ`를 추가하고 `(staff_id, kind, idempotency_key)` UNIQUE 인덱스를 만든다. 기존 행은 모두 `kind='CREATE'`이므로 영향이 없다.
- `staff/StaffAccountCommandService.resetPassword(token, staffId, idempotencyKey)`: 본사 권한·멱원 키·직원 존재 확인 뒤 쿨다운을 검사하고 새 비밀번호를 발급한다. `kind`가 `RESET_PASSWORD`인 가장 최근 `password_reset_at`를 읽어 `staff.password.reset-cooldown-seconds`(기본 300)와 비교한다.
- `staff/StaffPasswordResetResponse`(신규): 직원 요약 + `temporaryPassword` + `created` + `cooldownSeconds`.
- `staff/StaffPasswordCooldownException`(신규): 409 `STAFF_PASSWORD_RESET_COOLDOWN`로 매핑.
- `staff/StaffAccountController`: `POST /api/staff/staff/{staffId}/password`와 `StaffPasswordCooldownException` 핸들러를 추가했다.
- `application.yml`: `staff.password.reset-cooldown-seconds` 설정.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `resetStaffPassword`·`StaffPasswordReset` 타입을 추가하고 403·404·409 안내를 나눴다.
- `src/components/hotel-admin/staff-accounts.tsx`: 직원 표에 재발급 버튼, 결과 대화상자(비밀번호 복사 안내), 쿨다운 안내를 추가했다. 재발급마다 새 멱원 키를 발급한다.
- `e2e/staff-accounts.spec.ts` 4종 추가: 재발급 후 임시 비밀번호 한 번 표시, 쿨다운 안내, 없는 직원 안내, 지점 직원에게 재발급 버튼 미노출.

## 검증

- API `mvnw -Dtest=StaffAccountIntegrationTest,PolicyIntegrationTest test` **43건**(직원 21 + 정책 22) 종료 코드 0. 재발급 201, 같은 키 재호출 200 `created=false`, 쿨다운 내 새 키 409, 쿨다운 통과, 새 비밀번호 로그인, 없는 직원 404, 키 누락 400, 지점 직원 403, 세션 없음 401.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 6건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `staff-accounts` 10건 + `policies` 11건, 총 **21건** 종료 코드 0. 인접 suite `audit`·`inventory-viewer`·`hotel-catalog`·`ai-operations`·`operations-report`·`admin-navigation` **33건**도 종료 코드 0.
- 라이브: compose `api` 재빌드 뒤 health 200. 본사 세션으로 재발급 201 `created=true cooldownSeconds=300`, 같은 키 재호출 200 `created=false temporaryPassword=null`, 쿨다운 내 새 키 409 `STAFF_PASSWORD_RESET_COOLDOWN`, 발급된 임시 비밀번호로 로그인 201, 지점 직원 403, 세션 없음 401.
- 검증용 재발급 기록 1건과 policy revision은 삭제했다. 재발급으로 바뀐 `sokcho@hotel-chain.local` 비밀번호는 `StaffDevAccountInitializer`의 `ON CONFLICT DO UPDATE`가 시작마다 환경 변수 비밀번호를 다시 쓰는 것으로 복원했다. `api` 재시작 뒤 환경 변수 비밀번호로 로그인 201을 확인했다.

## 미검증 항목

- Vite production build, 전체 backend suite, 전체 Playwright 회귀는 실행하지 않았다. 변경 범위가 재발급 동선에 국한되므로 사용자가 요청할 때까지 반복하지 않는다.
- 관리자 4001 브라우저에서의 재발급 화면 전환은 이번 세션에 확인하지 않았다. Playwright 라우트 모킹으로 동작만 검증했다.
- 쿨다운 기본 300초가 실제로 만료되는지 기다려서 확인하지 않았다. 설정값과 비교하는 단위·통합 테스트로만 검증했다.
- 비활성·삭제, 역할 변경·지점 재할당, 본인 계정 수정, 비밀번호 만료·변경 이력·강제 로그아웃, 이메일 기반 본인 확인, 영문 전환은 범위 밖이다.
