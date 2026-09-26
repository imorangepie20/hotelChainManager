# 본사 직원 계정 관리 - 삭제·본인 계정 수정 (2026-09-24)

> 상태: 작업 트리에만 있고 커밋하지 않은 상태.

## 변경 이유

`admin-menu-roadmap.md` 3번 `직원 계정 관리`의 **마지막 남은 항목**이다.
1번·2번 영역이 끝났고 로드맵 순서(1 → 2 → 3 → 5 → 4 → 7 → 6 → 8)에 따라
3번을 마무리한다.

현재 본사는 직원을 만들고 역할·소속 지점을 바꾸고 비밀번호를 재발급하고
비활성할 수 있다. **하지만 잘못 만든 계정이나 퇴사가 확정된 계정을 지울
수단이 없다.** 비활성은 로그인을 막을 뿐 직원 목록에 계속 나타나서
본사가 살아 있는 계정과 멈춰진 계정을 매번 구분해야 한다.

두 번째로 **직원이 본인 이름을 직접 고칠 수단이 없다.** 본사가 임시
비밀번호와 함께 만든 표시 이름을 그대로 써야 하고, 비밀번호도 본사가
다시 재발급해야만 바꿀 수 있다. 본인 계정은 `PATCH .../staff/{staffId}`가
409 `STAFF_SELF_MODIFICATION_FORBIDDEN`로 거부하므로 본사 관리자도
대신 고쳐줄 수 없다.

## 삭제 정책

직원 행은 감사 이력·예약 변경 승인·콘텐츠 발행 이력의 근거다. 그래서
**삭제를 "행을 지우는" 것이 아니라 "식별자를 영구적으로 비우는" 것으로**
정한다. `staff_member` 행을 남겨두고 이메일·표시 이름·비밀번호·역할·지점을
비운다. 계정은 더 이상 로그인할 수 없고 직원 목록에도 나타나지 않지만,
과거 운영 기록의 참조는 끊기지 않는다.

이것은 2026-09-23 객실 유형 삭제가 취한 방식과 같다. 객실 유형 삭제도
`reservation`·`rate_plan`의 `room_type_id` 참조를 끊고 행을 지웠다.
직원은 참조가 20개 표에 더 넓게 퍼져 있어서 행을 남기는 쪽이 안전하다.

### 삭제 조건

- **진행 중인 예약 변경 요청이 있으면 409로 거부한다.** 예약 변경 요청의
  `requested_by`·`decided_by`가 삭제된 직원을 가리키면 승인 이력의
  근거가 사라진다. `COMPLETED`·`REJECTED`·`CANCELLED`·`EXPIRED`된 요청은
  과거 기록이므로 막지 않는다. 이 기준은 객실 유형 삭제가 쓴 것과 같다.
- **진행 중인 정산 실행이 있으면 409로 거부한다.** `toss_settlement_run`의
  `requested_by`가 삭제된 직원을 가리키면 안 된다.
- **본인 계정은 삭제할 수 없다.** 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`.
  본사 관리자가 실수로 본인을 지워 본사 메뉴에 다시 들어오지 못하는 것을
  막는다. 비활성·역할 수정과 같은 예외를 재사용한다.
- **삭제는 되돌릴 수 없다.** 대화상자에 "이 작업은 되돌릴 수 없다"고
  쓴다. 비활성이 되돌릴 수 있는 수단이므로 본사는 먼저 비활성을 쓴다.

### 삭제가 비우는 것

- `email` → 빈 문자열이 아니라 `deleted-<uuid>@deleted.local` 형태의
  자리 표시자. `email`에 `UNIQUE` 제약이 있어서 빈 문자열로 두면
  삭제된 계정이 여러 개일 때 유일 제약이 걸린다. 자리 표시자는
  다시 로그인할 수 없는 값을 쓴다.
- `display_name` → `삭제된 직원`. 감사 이력이 조인으로 읽는 값을
  의미 있는 텍스트로 둬서 과거 기록이 빈 칸으로 보이지 않게 한다.
- `password_hash` → 빈 문자열. BCrypt가 빈 문자열을 받지 않으므로
  삭제된 계정은 비밀번호가 맞아도 로그인할 수 없다.
- `role` → `REMOVED` (새 역할 값). `staff_member.role`의 `CHECK` 제약이
  `HQ_ADMIN`·`BRANCH_STAFF`만 허용하므로 V64가 이 제약을 바꿔야 한다.
- `hotel_id` → `null`.
- `active` → `false`.
- `staff_session` → 해당 직원의 모든 세션을 지운다.

## 본인 계정 수정 정책

- `PATCH /api/staff/me`가 **본인**의 표시 이름과 비밀번호를 바꾼다.
  `HQ_ADMIN`·`HQ_EDITOR`·`HQ_PUBLISHER`·`BRANCH_STAFF` 전 역할이
  쓸 수 있다. 본인 세션이 필요하다.
- **역할·소속 지점은 바꿀 수 없다.** 그것은 본사 전용 권한이다.
  본인 계정 수정이 역할을 바꾸면 본인 권한을 올리는 것이 가능해진다.
- **비밀번호를 바꾸면 다른 기기의 세션을 끊는다.** 비밀번호 변경은
  계정 통제권이 바뀌는 것이므로, 현재 세션 하나만 남기고 나머지를
  지운다. `token_hash` 기반이라 비밀번호를 바꿔도 현재 세션은 살려둔다.
- **현재 비밀번호 확인이 필수다.** 본인 계정 수정은 로그인한 세션만
  있으면 되므로, 세션을 탈취한 사람이 비밀번호를 바꾸는 것을 막으려면
  현재 비밀번호를 다시 물어봐야 한다.
- **새 비밀번호 규칙.** 8자 이상 100자 이하. 임시 비밀번호가 16자인
  것보다 짧지만, 실사용자가 기억할 수 있는 길이의 최소한으로 정한다.

## 서버 변경

### services/api

- `staff/StaffAccountDeletionService`(신규): `@Transactional`. 삭제 본문.
  진행 중인 변경 요청·정산 실행을 세고 있으면 409를 던진다. 멱원 재호출은
  삭제된 계정을 다시 읽어서 같은 결과를 돌려준다.
- `staff/StaffAccountDeletionResponse`(신규): `staffId`·`email`·`displayName`·`deleted`·`remainingStaff`.
- `staff/StaffAccountDeletionConflictException`(신규): 409. 어느 조건이
  막았는지 `openChangeRequests`·`activeSettlementRuns` 개수를 알려준다.
- `staff/StaffSelfUpdateRequest`(신규): `displayName`·`currentPassword`·
  `newPassword`. 둘 다 선택이고, 둘 다 비우면 400이다.
- `staff/StaffSelfUpdateResponse`(신규): `staffId`·`email`·`displayName`·`changed`.
- `staff/StaffSelfUpdateService`(신규): `@Transactional`. 현재 비밀번호를
  BCrypt로 확인하고, 새 비밀번호를 해싱한다. 비밀번호를 바꾸면
  현재 세션을 제외한 모든 세션을 지운다.
- `staff/StaffSelfUpdateException`(신규): 400. 현재 비밀번호가 틀렸을 때.
- `staff/StaffAccountCommandService`: `KIND_DELETE`·`KIND_SELF_UPDATE` 추가.
  삭제·본인 수정 명령도 `staff_account_command`에 같은 패턴으로 기록한다.
- `staff/StaffAccountController`: `DELETE /{staffId}`·`PATCH /me` 핸들러.
  새 결과는 201, 재호출은 200이다.
- `staff/StaffAccountQueryService.list`: `role <> 'REMOVED'`로 삭제된
  직원을 목록에서 뺀다. SELECT만 사용한다.

### 마이그레이션 (V64)

- `role`의 `CHECK`에 `REMOVED`를 추가한다. additive: 기존 행은 모두
  `HQ_ADMIN`·`BRANCH_STAFF`이므로 값을 바꾸지 않는다.
- `staff_account_command.staff_id`·`created_staff_id`가 `staff_member`를
  참조한다. 삭제된 직원의 행을 자리 표시자로 덮어쓰기 때문에
  외래키 위반 없이 삭제 기록을 남길 수 있다.

## 관리자 UI 변경

- `/dashboard/staff`의 직원 표에 `삭제` 열과 확인 대화상자를 추가한다.
  삭제는 매번 새 멱원 키를 쓴다. 서버가 409를 내면 대화상자를 닫지
  않고 이유를 보여준다.
- 본사 전용 `내 계정` (`/dashboard/me`) 화면을 추가한다. 현재 이메일·
  역할·소속 지점을 보여주고 표시 이름·비밀번호를 바꾼다. 전 역할이
  쓴다. `nav.ts`에 `본사 관리` 그룹이 아닌 `운영` 그룹에 넣어서
  지점 직원도 본인 계정을 고칠 수 있게 한다.

## 완료 기준

1. 본사가 직원을 삭제하면 더 이상 로그인할 수 없고 직원 목록에
   나타나지 않는다.
2. 삭제된 직원의 과거 감사 이력·예약 변경 승인·콘텐츠 발행 이력이
   빈 칸이 아니라 `삭제된 직원`으로 보인다.
3. 진행 중인 예약 변경 요청·정산 실행이 있으면 409로 거부하고
   아무것도 비우지 않는다.
4. 본인 계정은 삭제할 수 없다.
5. 멱원 재호출이 같은 결과를 돌려주고 계정이 두 번 비워지지 않는다.
6. 직원이 본인 표시 이름·비밀번호를 바꿀 수 있다. 역할·소속 지점은
   바꿀 수 없다.
7. 비밀번호를 바꾸면 다른 기기의 세션이 끊긴다. 현재 세션은 유지된다.
8. 현재 비밀번호 없이는 바꿀 수 없다.
9. 기존 `PATCH .../staff/{staffId}`·`PATCH .../staff/{staffId}/active`·
   `POST .../staff/{staffId}/password` 호출이 깨지지 않는다.

## 미검증 항목 (사전 명시)

- **라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않는다.**
  라이브 직원 계정을 비우면 감사 이력 표시가 즉시 바뀌므로 사용자가
  확인한다.
- **삭제된 계정의 이메일을 다시 등록할 수 있는지.** 자리 표시자가
  원래 이메일을 차지하므로 같은 이메일로 새 계정을 만들 수 있다.
  이것은 의도한 동작이다 (퇴사한 직원의 이메일을 새 직원이
  물려받을 수 있어야 한다).
- **삭제된 계정이 본인 계정 수정 API를 호출하는지.** `REMOVED` 역할은
  `current()` 통과 후 `StaffSelfUpdateService`에서 막는다.
- **390px 모바일에서 새 `삭제` 열과 `내 계정` 화면.** 기존 390px
  검사 기준을 유지한다.
- 영문 전환. 한국어 단일 언어다.
- **삭제된 직원의 세션이 만료되는 것을 브라우저에서 확인하지 않는다.**
  세션을 지우고 `current()`가 `active`를 검사하므로 다음 호출에 401이 난다.
