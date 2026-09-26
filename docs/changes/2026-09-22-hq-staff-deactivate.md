# 본사 직원 계정 - 비활성·재활성 (2026-09-22)

> 상태: 작업 트리에만 있고 커밋하지 않은 상태.

## 변경 이유

`admin-menu-roadmap.md` 3번 `직원 계정 관리`에서 남은 "비활성"을 구현했다. 이전 단계까지 본사는 직원을 만들고 역할·소속 지점을 바꾸고 임시 비밀번호를 재발급할 수 있었지만, **퇴사하거나 권한을 회수해야 하는 직원의 계정을 끌 수단이 없었다.** 계정을 삭제하면 감사 이력이 참조하는 직원 행이 사라지므로, 삭제 대신 비활성을 먼저 구현했다.

## 서버 변경

- `PATCH /api/staff/staff/{staffId}/active`가 본문 `{"active": false|true}`로 활성 상태를 바꾼다. `HQ_ADMIN`만 허용하고 지점 직원은 403, 잘못된 세션은 401, 없는 직원은 404, 멱원 키 누락은 400이다.
- **비활성은 즉시 효력이 있다.** `StaffAccessService.login`이 비밀번호가 맞아도 `active = false`면 403 `StaffAccessDeniedException`로 거부하고, `principal()` 조회 SQL에 `AND m.active`를 붙여 비활성 직원이 남겨둔 세션도 401로 만료시킨다.
- **세션을 먼저 지운다.** 비활성 처리와 동시에 `delete from staff_session where staff_id = ?`를 실행한다. 그렇지 않으면 비활성 직원이 로그아웃하기 전까지 계속 API를 쓸 수 있다.
- **본인 계정은 본인이 비활성할 수 없다.** `staffId.equals(principal.id())`면 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`로 거부한다. 본사 관리자가 실수로 본인을 비활성해 본사 메뉴에 다시 들어오지 못하는 것을 막는다. 역할 수정과 같은 예외를 재사용한다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200에 `created=false`로 같은 결과를 돌려주고, 응답 유실 뒤 새 키로 같은 내용을 보내도 `staff_id`·`modified_by`·`active`의 SHA-256 지문이 같아 같은 결과를 돌려준다.
- **같은 상태를 다시 요청해도 에러가 아니다.** 이미 활성인 계정을 다시 활성하면 상태를 바꾸지 않고 멱원 기록만 남겨, 응답을 유실한 뒤 새 키로 같은 내용을 보내는 재시도가 200으로 같은 결과를 돌려주게 했다.
- V56 additive 마이그레이션이 `staff_member.active BOOLEAN NOT NULL DEFAULT TRUE`를 추가한다. 기존 행은 모두 활성이므로 기존 동작을 변경하지 않는다. `staff_session.staff_id` 인덱스는 비활성 시 세션 삭제 속도를 위해서다.
- `GET /api/staff/staff`와 `StaffAccountView`가 DB의 `active`를 내려준다. 이전에는 상수 `true`를 하드코딩했다.

## 관리자 UI 변경

- `/dashboard/staff`의 직원 표에 `활성` 열과 `비활성`·`재활성` 버튼을 추가했다. 활성 상태를 `Badge`로 보여주고 버튼 라벨·아이콘(`UserX`·`UserCheck`)이 다음 동작을 가리킨다.
- 비활성·재활성은 **매번 새 멱원 키**를 쓴다. `activationKey` 카운터를 올려서 같은 키 재호출과 구분한다. 그래야 연속으로 두 번 비활성해도 두 번째 요청이 첫 번째 결과를 돌려받지 않는다.
- 서버 검증 실패 시 표 위에 `activation-error` 안내를 보여주고 상태를 바꾸지 않는다. 본인 비활성 409는 "본인 계정은 이 화면에서 비활성할 수 없습니다. 다른 본사 관리자에게 요청해 주세요."로 안내한다.
- 본사만 버튼이 보인다. 지점 직원은 본사 전용 안내를 본다.

## 검증 결과

- API `mvnw -Dtest=StaffAccountIntegrationTest` **47건**(기존 35 + 신규 12)이 종료 코드 0이다. 새 검증은 비활성 201·목록의 `active=false`·세션 0건·비활성 세션 사용 401·비활성 로그인 403·재활성 후 로그인 201·같은 상태 재요청·같은 키 재호출 200 `created=false`·새 키 같은 내용 200·본인 비활성 409·지점 직원 403·세션 없음 401·없는 직원 404다.
- 인접 영역 `RoomTypeCommandIntegrationTest` 27건·`RateCommandIntegrationTest` 20건·`InventoryCommandIntegrationTest` 16건이 종료 코드 0이다.
- 관리자 `tsc --noEmit`이 종료 코드 0이고, `eslint`는 경고만 낸다(`react-hooks/set-state-in-effect`, 기존 컴포넌트와 같은 패턴).
- Playwright `staff-accounts` **17건**(기존 10 + 신규 7)·`inventory-viewer` 14건·`hotel-catalog` 13건이 종료 코드 0이다.

## 발견·수정한 결함

1. `RoomTypeCommandIntegrationTest.java`의 마지막 줄이 `    }}`로 붙어 있었다. 클래스 닫는 괄호가 메서드 닫는 괄호에 병합된 것이다. Java 컴파일러는 이것을 정상으로 받아들이지만 diff가 불분명해지고 이후 편집에서 괄호 짝을 잃기 쉬워 분리했다.
2. 이 세션에서 추가한 7개 파일이 Prettier 기본 형식을 따르지 않았다. 프로젝트의 다른 파일과 일치하게 `npx prettier --write`로 정리했다. 본문은 바꾸지 않고 공백·줄넘김만.
3. Playwright `keeps the edit dialog open with a self modification message`가 실패했다. 원인은 코드가 아니라 **테스트의 route 글로브**였다. `**/api/staff/staff/*`는 컬렉션 경로 `/api/staff/staff`를 잡지 않아서 목록 GET이 목을 통과해 실제 API로 나갔고, API가 내려주는 빈 목록 때문에 `직원 2명`이 보이지 않았다. 컬렉션 경로를 따로 채우는 `route`를 추가해서 고쳤다.

## 미검증 항목

- 라이브 DB·compose `api` 재빌드·브라우저 확인은 실행하지 않았다. V56 마이그레이션이 라이브 DB에 적용되는지, 비활성 직원이 브라우저에서 즉시 로그아웃되는지는 사용자가 확인한다.
- 비활성 직원이 진행 중이던 예약 변경 요청·정산 실행의 소유권 표시는 그대로다. 감사 이력의 처리 직원 참조가 비활성 계정을 가리킬 수 있다.
- 삭제는 이번 범위에서 뺐다. `reservation`·`reservation_change_request`·`staff_account_command` 외래키가 걸려 있어 안전한 삭제 조건을 따로 설계해야 한다.
- 본인 계정의 이름·비밀번호 직접 변경(셀프 서비스)도 이번 범위가 아니다.
