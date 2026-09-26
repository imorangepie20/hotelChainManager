# 본사 직원 계정 - 역할·소속 지점 수정

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.

## 변경 요약

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`에서 남은 다음 항목인 "역할 변경·지점 재할당"을 구현했다. 본사가 직원의 역할을 바꾸거나 지점을 옮길 수단이 전혀 없었고, 바꾸려면 DB를 직접 건드려야 했다.
- `PATCH /api/staff/staff/{staffId}`가 역할과 소속 지점을 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 직원은 404, 없는 지점은 404다.
- **역할과 지점의 짝은 서버가 최종 판단한다.** 본사 역할(`HQ_ADMIN`·`HQ_EDITOR`·`HQ_PUBLISHER`)은 지점을 가질 수 없고, `BRANCH_STAFF`는 지점이 필수다. 위반은 400이고 값을 바꾸지 않는다. 두 값 모두 null이어도 400이다.
- 역할을 본사 역할로 올리면 소속 지점을 비우고, 지점 직원으로 내리면 요청한 지점을 채운다. UI가 지점 선택을 숨기더라도 서버가 항상 같은 결과를 낸다.
- **본인 계정의 변경은 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`로 거부한다.** 본사 관리자가 실수로 본인 권한을 내리거나 지점으로 옮겨 다시는 본사 메뉴에 들어오지 못하는 것을 막는다. 거부할 때 값을 바꾸지 않는다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·`modified_by`·역할·지점의 SHA-256 지문이 같아 같은 결과를 돌려준다. `staff_member` 행을 `for update`로 잡아 동시 수정을 직렬화한다.
- 세션은 `token_hash` 기반이라 역할을 바꿔도 기존 세션이 즉시 만료되지 않는다. 다음 로그인부터 새 역할이 적용된다.
- 관리자 `/dashboard/staff`의 직원 표에 `역할·지점` 열과 수정 버튼·대화상자를 추가했다. 본사만 버튼이 보이고, 대화상자는 현재 역할·소속 지점으로 미리 채운다. 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다.
- 가격·재고·예약 확정의 권한은 계속 Spring Boot에 있다. 관리자 UI는 서버가 검증한 결과만 표시한다.

## 서비스별 변경

### services/api

- `staff/StaffAccountUpdateRequest`(신규): `role`·`hotelId`. `validate()`가 역할 허용 목록·역할-지점 짝·빈 요청을 검증한다.
- `staff/StaffAccountUpdateResponse`(신규): `staffId`·`email`·`displayName`·`role`·`hotelId`·`hotelName`·`created`.
- `staff/StaffSelfModificationException`(신규): 409로 매핑.
- `staff/StaffAccountCommandService`: `KIND_UPDATE` 추가. `update`가 권한·멱원 키·본문 검증을 한 뒤 `staff_member`의 `role`·`hotel_id`를 바꾸고 `staff_account_command`에 `UPDATE` 명령을 남긴다. `findUpdatedCommand`가 같은 키 재호출과 같은 내용의 다른 키 재시도를 모두 잡는다. `requestHash(staffId, modifiedBy, request)`가 지문을 만든다.
- `staff/StaffAccountController`: `PATCH /{staffId}` 핸들러와 `StaffSelfModificationException` 409 매핑 추가. 새 결과는 201, 재호출은 200이다.
- 새 마이그레이션은 없다. 기존 `staff_account_command`(`V47`)의 `kind`에 `UPDATE`만 추가했고, 테이블 구조는 변경하지 않는다.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `UpdateStaffAccountInput`·`UpdatedStaffAccount`·`StaffRole` 타입 추가. `updateStaffAccount` 추가. `staffUpdateFailureMessage`가 403·404·409(`STAFF_SELF_MODIFICATION_FORBIDDEN` 구분)를 한국어 안내로 매핑한다.
- `src/components/hotel-admin/staff-accounts.tsx`: 직원 표에 `역할·지점` 열과 수정 버튼을 추가했다. 수정 대화상자는 현재 역할·소속 지점으로 미리 채우고, 역할이 지점 직원일 때만 소속 지점 선택을 보여준다. 서버 검증 실패 시 대화상자를 닫지 않고 `edit-error` 안내를 보여준다.
- `e2e/staff-accounts.spec.ts`: 10→13건. 본사 역할로의 승격(지점 선택 숨김·`본사` 표시)·지점 직원 이동 시 빈 지점 차단·본인 변경 409 안내를 추가했다.

### 설계 변경

- 계획 없이 바로 구현했다. 로드맵 3번의 "역할 변경·지점 재할당"이 뚜렷한 다음 단계였기 때문이다.
- 역할을 "보냈을 때만 바꾼다" 대신 "항상 역할을 보낸다"로 정했다. 역할과 지점이 짝이라서 지점만 바꾸는 요청이 들어오면 역할을 유지한 채 지점만 옮기는 의미가 모호해진다. UI는 항상 둘을 함께 보낸다.
- 본인 변경을 UI가 아니라 서버에서 거부했다. UI가 본인 행의 버튼을 숨기더라도 API 호출 자체를 막을 수 없고, 우연히 다른 본사 관리자의 행을 본인으로 착각하는 경우를 서버가 잡는다.
- 본인 계정의 이름·비밀번호 직접 수정은 이번 범위에서 뺐다. 본인 변경 보호와 같은 화면에서 충돌하므로 별도 설계가 필요하다.

## 검증

- API `mvnw -Dtest=StaffAccountIntegrationTest` **35건**(기존 22 + 신규 13)이 종료 코드 0이다. 신규 13건: 본사 역할 간 이동 201·지점 비움, 지점 직원으로 이동 201·지점 채움, 지점 직원+지점 없음 400, 본사 역할+지점 400, 본인 변경 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`, 알 수 없는 역할 400, 빈 요청 400, 없는 지점 404, 같은 키 재호출 200 `created=false`·`staff_account_command` 1건, 새 키 같은 내용 200 `created=false`, 멱원 키 누락 400, 지점 직원 403, 세션 없음 401, 없는 직원 404 `STAFF_ACCOUNT_NOT_FOUND`.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 변경 파일 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright `staff-accounts` **13건**이 종료 코드 0이다.
- 라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다.

## 미검증 항목

- 라이브 PostgreSQL 개발 DB에서의 `PATCH` 호출. compose `api` 재빌드도 하지 않았다. 마이그레이션이 없으므로 DB 스키마 변경은 없다.
- Vite production build는 실행하지 않았다. `tsc --noEmit`과 Playwright 브라우저 검증으로 대체했다.
- 두 본사 관리자가 같은 직원을 동시에 바꿀 때의 경합. `staff_member` 행을 `for update`로 잡고 트랜잭션을 묶었지만 동시 쓰기를 직접 시도하지는 않았다.
- 390px 모바일에서 새 열·대화상자가 넘치지 않는지. 기존 5열 표가 6열이 되면서 가로 스크롤이 늘어난다.
- 영문 전환. 한국어 단일 언어다.
- 역할을 바꾼 뒤 기존 세션이 유지되는지 라이브에서 확인하지 않았다. 세션은 `token_hash` 기반이라 서버 응답과 메뉴 노출(`nav.ts`)에만 기대지 않는다.
- 비활성·삭제, 본인 계정 이름·비밀번호 수정은 이번 범위가 아니다.
