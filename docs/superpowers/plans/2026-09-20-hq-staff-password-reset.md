# 본사 직원 계정 - 비밀번호 재발급

> 상태: 구현·검증 완료. 최종 갱신: 2026-09-20. [변경 기록](../../changes/2026-09-20-hq-staff-password-reset.md)

## 배경

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`의 조회·생성은 끝났다. 남은 첫 항목은 비밀번호 재발급이다.
- 본사가 발급한 16자 임시 비밀번호는 응답에 한 번만 내려오고 BCrypt 해시만 저장된다. 직원이 분실하면 재발급 수단이 현재 없고, 환경 변수 비밀번호로 초기화되는 개발 계정(`StaffDevAccountInitializer`)에 의존해야 한다.
- 본사 메뉴에서 재발급할 때도 생성과 같은 위험(중복 발급, 응답 유실 후 재시도로 인한 연속 재발급)이 있다. 생성의 멱원 패턴을 그대로 가져온다.

## 완료 기준

1. `POST /api/staff/staff/{staffId}/password`가 본사 세션으로 새 16자 임시 비밀번호를 발급한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401이다.
2. 없는 직원은 404, 멱원 키 누락은 400이다.
3. 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 **같은 임시 비밀번호**를 돌려준다. 응답 유실 뒤 새 키로 같은 대상 재발급을 시도하면 같은 결과를 돌려준다. 비밀번호가 두 번 바뀌지 않는다.
4. 재발급은 본인 계정도 포함한다. 본사 자신의 비밀번호를 재발급하면 현재 세션은 그대로 둔다(세션은 `token_hash` 기반이고 비밀번호와 무관하다).
5. 새 임시 비밀번호로 실제 로그인이 가능해야 한다.
6. 관리자 `/dashboard/staff`의 직원 표에 재발급 버튼을 추가하고, 성공하면 임시 비밀번호를 대화상자에 한 번 보여준다. 서버 검증 실패 시 대화상자를 닫지 않는다.
7. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다.

## 설계 결정

### 멱원과 비밀번호 보관

- `staff_account_command`의 `(staff_id, idempotency_key)` UNIQUE 제약을 재발급에도 재사용한다. `kind` 컬럼을 추가해 `CREATE`·`RESET_PASSWORD`를 구분하고, `(staff_id, kind, idempotency_key)`로 식별한다.
- 재호출에 같은 임시 비밀번호를 내려주려면 발급 시점의 비밀번호를 어디선가 읽어야 한다. BCrypt 해시는 복호화할 수 없으므로 **임시 비밀번호 원문을 저장하지 않는다**. 대신 재호출은 `created=false`와 함께 직원 요약만 내려주고, UI는 "이미 발급된 임시 비밀번호는 다시 볼 수 없다"고 안내한다. 비밀번호 원문을 DB에 보관하지 않는 생성 쪽 원칙을 우선한다.
- 응답 유실 뒤 새 키로 같은 대상 재발급을 시도할 때 매번 비밀번호가 바뀌면 사용자가 어떤 비밀번호가 유효한지 알 수 없다. 그래서 **재발급은 최근 재발급이 일정 시간 내에 있었으면 새 비밀번호를 만들지 않고 409로 거부**한다. 생성의 SHA-256 요청 지문이 아무 의미가 없기 때문에(내용이 항상 같으므로) 대신 `password_reset_cooldown_seconds`(기본 300초)로 연속 재발급을 막는다.

### 쿨다운과 멱원의 관계

- 같은 멱원 키 재호출 → 쿨다운과 무관하게 200 `created=false`.
- 새 멱원 키 + 쿨다운 내 → 409 `STAFF_PASSWORD_RESET_COOLDOWN`, 직원 요약 없음.
- 새 멱원 키 + 쿨다운 밖 → 새 비밀번호 발급, 201.

## 구현 범위

### services/api

- V50 additive 마이그레이션: `staff_account_command`에 `kind VARCHAR(32) NOT NULL DEFAULT 'CREATE'`·`password_reset_at TIMESTAMPTZ`를 추가하고 `UNIQUE (staff_id, kind, idempotency_key)`로 바꾼다. 기존 제약은 DROP하지 않고 새 제약을 추가하지 않는다 — 대신 `(staff_id, kind, idempotency_key)` UNIQUE 인덱스를 추가한다. 기존 행은 모두 `kind='CREATE'`이므로 영향이 없다.
- `StaffAccountCommandService.resetPassword(token, staffId, idempotencyKey)`: 본사 권한·멱원 키·직원 존재 확인 뒤 쿨다운을 검사하고 새 비밀번호를 발급한다.
- `StaffPasswordResetResponse`(신규): 직원 요약 + `temporaryPassword` + `created` + `cooldownExpiresAt`.
- `StaffPasswordCooldownException`(신규): 409로 매핑.
- `StaffAccountController`: `POST /api/staff/staff/{staffId}/password`.
- `staff.password.reset-cooldown-seconds`(기본 300) 설정.

### SDTPL_ADM

- `staff-api.ts`: `resetStaffPassword`·`StaffPasswordReset` 타입. 403·409·404 안내 분리.
- `components/hotel-admin/staff-accounts.tsx`: 재발급 버튼, 결과 대화상자(비밀번호 복사 안내), 쿨다운 안내.
- `e2e/staff-accounts.spec.ts`: 재발급·반영·멱원·쿨다운·검증 실패·권한 시나리오 추가.

### 테스트

- `StaffAccountIntegrationTest` 확장: 재발급 201, 같은 키 재호출 200, 쿨다운 내 새 키 409, 쿨다운 후 새 키 201, 새 비밀번호 로그인, 없는 직원 404, 키 누락 400, 지점 직원 403, 세션 없음 401.

## 미구현 예정 항목

- 비활성·삭제, 역할 변경·지점 재할당, 본인 계정 수정. 다음 후보다.
- 비밀번호 만료·변경 이력·강제 로그아웃. 세션은 비밀번호와 무관하므로 재발급이 기존 세션을 끊지 않는다.
- 이메일 기반 본인 확인. 본사가 직접 발급하는 동선만 다룬다.
- 영문 전환. 한국어 단일 언어다.
