# 본사 직원 계정 관리 첫 단계

> 상태: 구현 완료. 최종 갱신: 2026-09-19.

## 배경

- `admin-menu-roadmap.md` 3번 `직원 계정 관리`가 다음 대상이다. 1번·2번 메뉴의 읽기 전용과 첫 쓰기 동작은 끝났다.
- 현재는 `StaffDevAccountInitializer`가 환경 변수 비밀번호로 개발 계정을 초기화할 뿐, 애플리케이션에서 직원을 만들거나 조회할 수단이 없었다.
- 전체 범위(생성·역할 변경·지점 할당·비밀번호 초기화·비활성) 중 **직원 생성과 조회**만 먼저 했다. 비밀번호 재발급과 비활성은 권한이 넓어지므로 다음 작업으로 뺐다.

## 완료 기준

1. `GET /api/staff/staff`가 본사 세션으로 전 직원 목록(이메일·이름·역할·지점)을 반환한다. **완료.**
2. `POST /api/staff/staff`가 본사 세션으로 이메일·이름·역할·지점을 받아 직원을 만든다. **완료.**
3. `HQ_ADMIN`만 호출 가능. 지점 직원 403, 잘못된 세션 401. **완료.**
4. 이메일은 형식·254자 이하, 이름은 1~100자, 역할은 4종 중 하나, `BRANCH_STAFF`는 지점 필수·나머지는 지점 없음. 위반은 400. **완료.**
5. 같은 이메일은 409. 멱원 키(`Idempotency-Key`)로 같은 요청의 중복 생성을 막는다. **완료.**
6. 관리자 4001 `/dashboard/staff`의 `직원 권한` 화면에서 직원 표와 추가 대화상자를 쓴다. **완료.**
7. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다. **완료.**

## 구현 범위

### 서버 (services/api)

- `staff/StaffAccountQueryService`: SELECT만 사용. `staff_member` LEFT JOIN `hotel`로 지점 이름까지 반환. `limit`은 1~200.
- `staff/StaffAccountCommandService`: `create(token, idempotencyKey, request)`. `requireHeadquarters`로 본사만 허용하고 BCrypt로 비밀번호를 해싱한다.
  - 임시 비밀번호는 16자 무작지(혼동 문자 제외)로 생성해 응답에 한 번만 내보낸다. 원문은 저장하지 않는다.
  - 이메일 중복은 삽입 전에 확인해 409 `STAFF_EMAIL_DUPLICATE`로 매핑한다. 삽입 실패 뒤 재조회하면 트랜잭션이 중단되기 때문이다.
  - 멱원은 `staff_account_command`의 `(staff_id, idempotency_key)` UNIQUE로 막는다.
- `staff/StaffAccountController`: `GET /api/staff/staff`, `POST /api/staff/staff`. `@ExceptionHandler`로 이메일 중복을 409로 매핑한다.
- V47 `staff_account_command` 테이블. additive이고 기존 `staff_member`를 변경하지 않는다.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`의 `getStaffAccounts`·`createStaffAccount`·`StaffAccountView`·`CreateStaffAccountInput`·`CreatedStaffAccount`. 403·409·기타 실패 안내를 나눴다.
- `components/hotel-admin/staff-accounts.tsx`: 직원 표(이메일·이름·역할·소속 지점)와 추가 대화상자. `HQ_ADMIN`만 버튼이 보인다. 역할이 `BRANCH_STAFF`일 때만 소속 지점 선택이 나타난다. 임시 비밀번호를 별도 대화상자에 한 번 보여준다.
- `nav.ts`에 `본사 관리` 그룹을 추가하고 `직원 권한`을 넣었다. `HQ_ADMIN`에만 노출된다.

### 테스트

- `StaffAccountIntegrationTest` 14종: 본사 목록 200, 지점 403, 잘못된 세션 401, 본사 생성 201, 지점 생성 403, 잘못된 세션 생성 401, 이메일 중복 409, 형식 위반 400, 역할 위반 400, 지점 누락 400, 본사 역할+지점 400, 없는 지점 404, 멱원 키 누락 400, 멱원 재호출 같은 ID.
- `e2e/staff-accounts.spec.ts` 6종: 직원 표, 추가와 임시 비밀번호 1회 표시, 이메일 중복 시 대화상자 유지, 지점 직원 역할의 지점 필수, 지점 직원 안내, 장애 안내.
- `e2e/admin-navigation.spec.ts`에 `직원 권한` 노출·비노출 단언을 추가했다.

## 검증 결과

- API `mvnw -Dtest=StaffAccountIntegrationTest,RoomTypeCommandIntegrationTest,HotelCatalogQueryIntegrationTest,InventoryQueryIntegrationTest test` **41건** 종료 코드 0.
- API `mvnw compile`·`test-compile` 종료 코드 0.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 패턴).
- Playwright: `staff-accounts` 6건·`hotel-catalog` 8건·`inventory-viewer` 6건·`inventory-mobile` 1건·`admin-navigation` 2건 **23건** 종료 코드 0.
- 라이브: API 재빌드 뒤 V47 `success=t`, health `UP`. 본사 세션으로 목록 200, 생성 201, 멱원 재호출 200 같은 ID, 이메일 중복 409 `STAFF_EMAIL_DUPLICATE`, 형식·역할·지점 누락·본사 역할+지정 400, 없는 지점 404, 멱원 키 누락 400, 지점 직원 조회·생성 403, 세션 없음 401. 발급된 임시 비밀번호로 로그인 201을 확인했다. 관리자 4001 브라우저에서 본사 로그인 뒤 `직원 6명` → 추가 → `7명` 전환과 임시 비밀번호 표시를 확인했다. 검증용 직원과 멱원 기록·세션은 모두 삭제해 원래 6명으로 복원했다.

## 미구현 예정 항목

- 비밀번호 재발급. 임시 비밀번호 발급 동선이 별도로 필요하다.
- 비활성·삭제. `staff_member`를 직접 지우면 예약·감사 이력·세션 참조가 끊어진다.
- 역할 변경·지점 재할당. 기존 세션과 진행 중인 작업에 미치는 영향이 크다.
- 본인 계정 수정. 자기 역할 변경을 서버에서 거부하는 검증이 추가로 필요하다.
- 활성 상태 표시. 응답에 `active` 필드는 있지만 비활성 동작이 없으므로 항상 `true`다.
- 영문 전환. 메뉴·추가 대화상자·검증 안내는 한국어 단일 언어다.
