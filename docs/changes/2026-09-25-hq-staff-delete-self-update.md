# 본사 직원 계정 관리 - 삭제·본인 계정 수정

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-25.

## 변경 요약

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`의 **마지막 남은 항목**을 구현했다. 1번·2번 영역이 끝나 로드맵 순서(1 → 2 → 3 → 5 → 4 → 7 → 6 → 8)에 따라 3번을 마무리했다.
- 본사는 직원을 만들고 역할·소속 지점을 바꾸고 비밀번호를 재발급하고 비활성할 수 있었지만 **잘못 만든 계정이나 퇴사가 확정된 계정을 지울 수단이 없었다.** 비활성은 로그인을 막을 뿐 직원 목록에 계속 나타나서 본사가 살아 있는 계정과 멈춰진 계정을 매번 구분해야 했다.
- 직원도 **본인 이름을 직접 고칠 수단이 없었다.** 본사가 임시 비밀번호와 함께 만든 표시 이름을 그대로 써야 했고, 비밀번호도 본사가 다시 재발급해야만 바꿀 수 있었다.
- `DELETE /api/staff/staff/{staffId}`가 직원 계정을 영구 삭제한다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 멱원 키 누락은 400, 없는 직원은 404다. **삭제는 "행을 지우는" 것이 아니라 "식별자를 영구적으로 비우는" 것**이다. `staff_member` 행을 남겨두고 이메일·표시 이름·비밀번호·역할·지점을 비운다.
- **진행 중인 예약 변경 요청이나 정산 실행이 있으면 409 `STAFF_DELETION_CONFLICT`로 거부하고 아무것도 비우지 않는다.** 삭제는 되돌릴 수 없으므로 모든 충돌 검사를 삭제 앞에 둔다. 에러 메시지가 어느 조건이 막았는지 알려준다.
- **본인 계정은 삭제할 수 없다.** 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`다. 비활성·역할 수정과 같은 예외를 재사용해서 본사 관리자가 실수로 본인을 지워 본사 메뉴에 다시 들어오지 못하는 것을 막는다.
- `PATCH /api/staff/staff/me`가 **본인**의 표시 이름과 비밀번호를 바꾼다. `HQ_ADMIN`·`HQ_EDITOR`·`HQ_PUBLISHER`·`BRANCH_STAFF` 전 역할이 쓸 수 있다. **역할·소속 지점은 본사 전용 권한이라 받지 않는다.** 본인 계정 수정이 역할을 바꾸면 본인 권한을 올리는 것이 가능해진다.
- **비밀번호를 바꿀 때는 현재 비밀번호 확인이 필수다.** 세션을 탈취한 사람이 비밀번호를 바꾸는 것을 막으려면 계정 통제권이 넘어가기 전에 다시 물어봐야 한다. 틀리면 400 `STAFF_PASSWORD_MISMATCH`다.
- **비밀번호를 바꾸면 현재 세션 하나만 남기고 나머지를 끊는다.** 비밀번호 변경은 계정 통제권이 바뀌는 것이므로 다른 기기의 세션을 끊어야 한다. 세션은 `token_hash` 기반이라 비밀번호를 바꿔도 현재 세션은 살려둔다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `deleted=false`·`changed=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 결과를 돌려준다. **삭제의 멱원 재호출은 계정 조회 앞에서 검사**해서 계정이 이미 비워졌으면 404로 착각하게 되는 것을 막는다.
- **삭제는 매번 새 멱원 키를 쓴다.** 되돌릴 수 없는 동작이므로 재시도가 같은 키를 재사용하지 않게 한다. 본인 계정 수정도 매번 새 멱원 키를 써서 비밀번호가 두 번 바뀌지 않게 한다.
- 관리자 `/dashboard/staff`의 직원 표에 `삭제` 열과 확인 대화상자를 추가했고, 전 역열이 쓰는 `내 계정` (`/dashboard/me`) 화면을 추가했다. `nav.ts`의 `운영` 그룹에 넣어서 지점 직원도 본인 계정을 고칠 수 있게 했다. 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
- 가격·재고·예약 확정의 권한은 계속 Spring Boot에 있다. 관리자 UI는 서버가 검증한 결과만 표시한다.

## 삭제 정책

직원 행은 감사 이력·예약 변경 승인·콘텐츠 발행 이력 20개 표의 근거다. 그래서
**삭제를 "행을 지우는" 것이 아니라 "식별자를 영구적으로 비우는" 것으로**
정했다. 2026-09-23 객실 유형 삭제가 취한 방식과 같다. 객실 유형 삭제도
`reservation`·`rate_plan`의 `room_type_id` 참조를 끊고 행을 지웠다. 직원은
참조가 20개 표에 더 넓게 퍼져 있어서 행을 남기는 쪽이 안전하다.

삭제가 비우는 것:

- `email` → `deleted-<uuid>@deleted.local` 자리 표시자. `email`에 `UNIQUE`
  제약이 있어서 빈 문자열로 두면 삭제된 계정이 여러 개일 때 유일 제약이
  걸린다. 자리 표시자는 다시 로그인할 수 없는 값을 쓴다.
- `display_name` → `삭제된 직원`. 감사 이력이 조인으로 읽는 값을 의미 있는
  텍스트로 둬서 과거 기록이 빈 칸으로 보이지 않게 한다.
- `password_hash` → 빈 문자열. BCrypt가 빈 문자열을 받지 않으므로
  삭제된 계정은 비밀번호가 맞아도 로그인할 수 없다.
- `role` → `REMOVED`. `staff_member.role`의 `CHECK` 제약이
  `HQ_ADMIN`·`BRANCH_STAFF`만 허용했으므로 V64가 이 제약을 바꿨다.
- `hotel_id` → `null`. `active` → `false`.
- `staff_session` → 해당 직원의 모든 세션을 지운다.

삭제 조건:

- **진행 중인 예약 변경 요청이 있으면 거부한다.** `COMPLETED`·`REJECTED`·
  `CANCELLED`·`EXPIRED`된 요청은 과거 기록이므로 막지 않는다.
- **진행 중인 정산 실행이 있으면 거부한다.** `toss_settlement_run`의
  `PENDING`·`PROCESSING` 상태를 검사한다.
- **본인 계정은 삭제할 수 없다.**
- **삭제는 되돌릴 수 없다.** 대화상자에 "이 작업은 되돌릴 수 없다"고
  쓴다. 비활성이 되돌릴 수 있는 수단이므로 본사는 먼저 비활성을 쓴다.

## 서비스별 변경

### services/api

- `staff/StaffAccountDeletionService`(신규): `@Transactional`. 진행 중인
  변경 요청·정산 실행을 세고 있으면 409를 던진다. `staff_member`를
  `select ... for update`로 잡아 동시 삭제·수정을 직렬화한다. 멱원 재호출은
  삭제된 계정을 다시 읽어서 같은 결과를 돌려준다. `requestHash`가
  `staff_id`·`staffId`의 SHA-256 지문을 만든다.
- `staff/StaffAccountDeletionResponse`(신규):
  `staffId`·`email`·`displayName`·`deleted`·`remainingStaff`.
- `staff/StaffAccountDeletionConflictException`(신규): 409.
  `openChangeRequests`·`activeSettlementRuns` 개수를 알려준다.
- `staff/StaffSelfUpdateRequest`(신규): `displayName`·`currentPassword`·
  `newPassword`. 셋 모두 선택이고, `validate()`가 이름 1~100자, 새 비밀번호
  8~100자, 비밀번호를 바꿀 때 현재 비밀번호 필수를 검사한다. 둘 다 비우면
  400이다.
- `staff/StaffSelfUpdateResponse`(신규):
  `staffId`·`email`·`displayName`·`role`·`changed`.
- `staff/StaffSelfUpdateService`(신규): `@Transactional`. `access.current()`
  로 본인을 찾고(비활성·삭제된 계정은 401), 현재 비밀번호를 BCrypt로
  확인한다. 비밀번호를 바꾸면 `token_hash`로 현재 세션을 찾아 나머지를
  지운다. `requestHash`가 `staffId`·표시 이름·`password=<바꾸는지>`의
  SHA-256 지문을 만든다. **비밀번호 원문은 지문에 넣지 않는다.**
- `staff/StaffSelfUpdateException`(신규): 400 `STAFF_PASSWORD_MISMATCH`.
- `staff/StaffAccountController`: `DELETE /{staffId}`·`PATCH /me` 핸들러.
  새 결과는 201, 재호출은 200이다. `StaffSelfUpdateException`·
  `StaffAccountDeletionConflictException` 예외 매핑을 추가했다.
- `staff/StaffAccountQueryService.list`: `role <> 'REMOVED'`로 삭제된
  직원을 목록에서 뺀다. SELECT만 사용한다.
- `web/ApiExceptionHandler`: `IllegalArgumentException` 400
  `INVALID_REQUEST` 매핑을 그대로 쓴다.

### 마이그레이션 (V64)

- `role`의 `CHECK`에 `REMOVED`를 추가하고 `staff_member_hotel_scope_check`에
  `role = 'REMOVED' AND hotel_id IS NULL` 가지를 추가한다. **additive다.**
  기존 행은 모두 `HQ_ADMIN`·`HQ_EDITOR`·`HQ_PUBLISHER`·`BRANCH_STAFF`이므로
  값이 바뀌지 않는다.
- `staff_account_command.staff_id`·`created_staff_id`가 `staff_member`를
  참조하지만 삭제된 직원의 행을 자리 표시자로 덮어쓰기 때문에 외래키 위반
  없이 삭제 기록을 남길 수 있다.

### SDTPL_ADM

- `src/lib/nav.ts`: `운영` 그룹에 `고객 요청`(`Inbox`)과 `내 계정`
  (`UserCog`)을 추가했다. **전 역할이 본인 계정을 고칠 수 있다.** 본사 관리
  그룹이 아닌 운영 그룹에 넣어서 지점 직원도 메뉴를 본다.
- `src/lib/staff-api.ts`: `DeletedStaffAccount`·`StaffSelfUpdateInput`·
  `StaffSelfUpdateResult` 타입, `deleteStaffAccount`·
  `updateOwnStaffAccount` 추가. `deletionFailureMessage`가
  `STAFF_SELF_MODIFICATION_FORBIDDEN`·`STAFF_DELETION_CONFLICT`·403·404를
  한국어 안내로 매핑하고, `selfUpdateFailureMessage`가
  `STAFF_PASSWORD_MISMATCH`·400·401을 매핑한다.
- `src/components/hotel-admin/staff-accounts.tsx`: 직원 표에 `활성`·
  `역할·지점`·`삭제` 열을 추가하고 역할 수정·활성 전환·삭제 대화상자를
  추가했다. **삭제는 매번 새 멱원 키를 쓴다.** 서버가 409를 내면
  대화상자를 닫지 않고 이유를 보여준다.
- `src/components/hotel-admin/staff-self-account.tsx`(신규): 현재 이메일·
  역할·소속 지점을 읽기 전용으로 보여주고 표시 이름·비밀번호를 바꾼다.
  바꾸지 않는 필드는 서버에 보내지 않고, 비밀번호 원문은 저장하지 않는다.
  성공하면 `localStorage`의 직원 정보를 서버 응답으로 맞춘다.
- `src/app/(dashboard)/dashboard/me/page.tsx`(신규): `StaffSelfAccount`
  를 렌더링한다.
- `e2e/staff-accounts.spec.ts`: 13→14건. 삭제 4건(목록에서 빠짐·409
  대화상자 유지·본인 삭제 거부·지점 직원 버튼 숨김)을 추가했다.
- `e2e/staff-self-account.spec.ts`(신규): 7건. 메뉴 노출(본사·지점)·
  읽기 전용 신원·이름·비밀번호 동시 변경·현재 비밀번호 틀림·확인 불일치
  사전 차단·지점 직원 본인 수정.

### 설계 변경

- [계획](../plans/2026-09-24-hq-staff-delete-self-update.md)을 먼저 쓰고
  그대로 구현했다. 완료 기준 9개를 모두 충족한다.
- **삭제를 행 삭제가 아니라 식별자 비우기로** 정했다. 참조가 20개 표에
  퍼져 있어서 행을 지우면 과거 운영 기록의 근거가 사라진다.
- **삭제된 계정의 이메일을 자리 표시자로** 정했다. 빈 문자열로 두면
  `UNIQUE` 제약이 걸려서 삭제된 계정이 여러 개일 때 두 번째 삭제가
  실패한다.
- **본인 계정 수정의 역할·지점을 막기로** 정했다. 본인 권한을 올리는 것이
  가능해지면 안 된다.
- **비밀번호 변경 시 다른 세션을 끊기로** 정했다. 비밀번호 변경은 계정
  통제권이 바뀌는 것이므로 현재 세션 하나만 남긴다.
- **요청 지문에 비밀번호 원문을 넣지 않기로** 정했다. 비밀번호를
  바꿨다는 사실만으로 같은 요청인지 판별할 수 있다.

## 검증

- API `mvnw -Dtest=StaffAccountIntegrationTest` **75건**(기존 47 + 신규 28)이
  종료 코드 0이다. 그 내용은: 삭제 201 `deleted=true remainingStaff=1`·
  목록에서 빠짐·삭제된 계정 로그인 401·삭제된 계정 세션 401·같은 키 재호출
  200 `deleted=false`·새 키 같은 내용 200 `deleted=false`·명령 1건·
  본인 삭제 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`·지점 직원 403·
  세션 없음 401·멱원 키 누락 400·없는 직원 404·진행 중인 변경 요청 409
  `STAFF_DELETION_CONFLICT`·진행 중인 정산 실행 409·완료된 요청은
  삭제 허용·본인 수정 201 `changed=true`·표시 이름만 변경·비밀번호만
  변경·둘 다 변경·현재 비밀번호 틀림 400 `STAFF_PASSWORD_MISMATCH`·
  새 비밀번호 7자 400·이름 101자 400·빈 본문 400·역할은 무시·
  비밀번호를 바꾸면 다른 세션은 끊기고 현재 세션은 유지·같은 키 재호출
  200 `changed=false`·새 키 같은 내용 200 `changed=false`·
  `REMOVED` 역할의 본인 수정 401.
- 인접 suite `StaffAccessIntegrationTest` 1건·`GuestRequestIntegrationTest`
  22건·`PolicyIntegrationTest` 30건·`AuditIntegrationTest` 16건·
  `OperationsReportIntegrationTest` 18건, 합 **87건**도 종료 코드 0이다.
  기존 `PATCH .../staff/{staffId}`·`PATCH .../staff/{staffId}/active`·
  `POST .../staff/{staffId}/password` 호출이 깨지지 않았다.
- 관리자 `tsc --noEmit` 종료 코드 0. 고객 웹 `tsc --noEmit` 종료 코드 0.
- 관리자 `eslint` 변경 파일 종료 코드 0(경고 3건
  `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `staff-accounts` **14건**(기존 13 + 신규 4)·
  `staff-self-account` **7건**(신규)·인접 `admin-navigation` 2건·
  `staff-login` 1건·`staff-session-guard` 4건·`guest-requests` 8건,
  합 **36건**이 종료 코드 0이다.
- 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다.

## 수정한 결함 (검증 중 발견)

- **`e2e/staff-accounts.spec.ts`의 Playwright glob이 DELETE 요청을 잡지
  못했다.** `**/api/staff/staff*`를 등록했으나 **Playwright glob의 `*`는
  `/`를 넘지 못해서** `/api/staff/staff/{id}` DELETE가 라우트 핸들러에
  잡히지 않고 서버로 빠져나갔다. 서버가 404를 내서 "진행 중인 예약 변경
  요청이나 정산 실행이 있어 삭제할 수 없습니다" 안내 대신 "직원을 찾을
  수 없습니다"가 나왔다. `**/api/staff/staff{,/**}`로 컬렉션과 하위
  경로를 함께 잡고, 핸들러가 URL의 `/api/staff/staff/` 접두어와
  `DELETE` 메서드를 함께 검사하게 고쳤다. 3개 테스트가 실패하다가 모두
  통과했다.
- **같은 파일의 한국어 주석이 깨져 있었다.** 터미널(Windows PowerShell)
  코드 페이지가 한글을 표시하지 못해서 콘솔에서만 글자가 깨져 보였다.
  깨진 주석을 원래 의미에 맞게 다시 썼다.
- **`seedStaffScript`가 id 인수를 받지 않아 `tsc --noEmit`이
  TS2554로 실패했다.** 본인 계정 삭제 시나리오에서 목록에 나열된 계정의
  id로 로그인해야 하는데 함수가 `id`를 매개변수로 받지 않았다. 기본값
  `test`를 둬서 기존 호출은 그대로 뒀다.

## 미검증 항목 (사전 명시)

- **라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않는다.** 라이브
  직원 계정을 비우면 감사 이력 표시가 즉시 바뀌므로 사용자가 확인한다.
- **삭제된 계정의 이메일을 다시 등록할 수 있는지.** 자리 표시자가
  원래 이메일을 차지하므로 같은 이메일로 새 계정을 만들 수 있다.
  이것은 의도한 동작이다(퇴사한 직원의 이메일을 새 직원이 물려받을 수
  있어야 한다).
- **삭제된 계정이 본인 계정 수정 API를 호출하는지.** `REMOVED` 역할은
  `current()`가 `active`를 검사하므로 401이 난다.
- **390px 모바일에서 새 `삭제` 열과 `내 계정` 화면.** 기존 390px 검사
  기준을 유지한다. 직원 표는 `overflow-x-auto`가 가로 스크롤을 담당한다.
- 영문 전환. 한국어 단일 언어다.
- **삭제된 직원의 세션이 만료되는 것을 브라우저에서 확인하지 않는다.**
  세션을 지우고 `current()`가 `active`를 검사하므로 다음 호출에 401이 난다.
- **두 본사 관리자가 같은 직원을 동시에 삭제할 때의 경합.**
  `select ... for update`와 트랜잭션으로 직렬화하지만 동시 삭제를
  직접 시도하지는 않았다.
- Vite·Next production build는 실행하지 않았다. `tsc --noEmit`과
  Playwright 브라우저 검증으로 대체했다.
- **3번 `직원 계정 관리` 영역이 완료됐다.** 1번·2번·3번 영역이
  끝났으므로 로드맵 순서에 따라 다음은 5번 `공통 정책 관리`의 남은
  항목(환불 규칙, 지점별 정책, 정책 변경의 감사 이력 통합)이다.
