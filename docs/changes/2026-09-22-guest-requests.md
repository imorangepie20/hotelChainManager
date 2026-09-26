# 본사·지점 고객 요청 관리 - 접수·조회·상태 변경 (2026-09-22)

> 상태: 작업 트리에만 있고 커밋하지 않은 상태.

## 변경 이유

`admin-menu-roadmap.md` 6번 `고객 요청 관리`의 1차를 구현했다. **고객이 호텔에 요청을 보낼 수 있는 채널이 아예 없었다.** 예약 변경·취소는 전용 API가 있지만, 그 외의 문의(객실 요청·편의 요청·환불 문의·일반 문의)는 전화로만 들어와서 직원이 받은 요청을 시스템에 남기거나 처리 상태를 추적할 수 없었다.

## 서버 변경

- `POST /api/hotels/{hotelId}/guest-requests`가 고객의 요청을 받는다. **인증이 없다.** 고객 웹은 세션 기반이 아니므로 누구나 접수할 수 있고, 조회·처리는 직원 세션만 허용한다.
- **예약 변경·취소는 받지 않는다.** `reservation_change_request`·`cancellation_attempt`가 전용으로 처리하므로 중복을 피한다. 허용 유형은 `ROOM_REQUEST`·`AMENITY_REQUEST`·`REFUND_INQUIRY`·`GENERAL_INQUIRY`·`OTHER` 5종이고 나머지는 400이다. 유형은 DB `CHECK` 제약과 서비스 `Set` 두 곳에서 검증한다.
- 멱원 키는 필수이고, 빼면 400이다. **같은 키 재호출은 200에 `created=false`로 같은 요청을 돌려준다.** 응답 유실 뒤 새 키로 같은 내용을 보내도 지점·예약·유형·제목·내용·이름·이메일의 SHA-256 지문이 같아 같은 결과를 돌려준다. 동시 접수는 `guest_request_idempotency_idx` 유일 인덱스가 막고, `DataIntegrityViolationException`을 잡아 이미 저장된 요청을 돌려준다.
- 접수는 가격·재고·예약 상태를 전혀 바꾸지 않는다. `guest_request`·`guest_request_event`에만 쓴다.
- `GET /api/staff/guest-requests`가 요청 목록을 돌려준다. **본사는 지점 필터가 없으면 전 지점을 읽고**, 지점 직원은 무조건 자기 지점만 읽는다. 지점 직원이 다른 지점을 명시적으로 요청하면 403이다. `status` 필터, `limit` 1~100, `offset` 0~10,000이고 위반은 400, 알 수 없는 `status`도 400이다. SELECT만 사용한다.
- `GET /api/staff/guest-requests/{requestId}`가 연락처(이름·이메일·전화번호)·내용·처리 이력을 돌려준다. 본사가 아니면 자기 지점 요청만 읽을 수 있고, 다른 지점은 403, 없는 요청은 404 `GUEST_REQUEST_NOT_FOUND`다.
- `POST /api/staff/guest-requests/{requestId}/transition`이 처리 상태를 `OPEN`→`IN_PROGRESS`→`RESOLVED`→`CLOSED` 중 하나로 바꾼다. **순서를 강제하지는 않는다.** 직원이 상황에 따라 임의의 상태로 옮길 수 있게 했고, UI는 현재 상태가 아닌 나머지 버튼만 보여준다.
- 멱원은 두 갈래로 동작한다. 같은 `Idempotency-Key` 재호출은 200으로 같은 결과를 돌려주고, **이미 같은 상태면 값을 바꾸지 않고 멱원 기록만 남겨서** 재시도가 200으로 같은 결과를 돌려주게 한다. 상태를 바꾸면 `guest_request_event`에 `from_status`·`to_status`·처리 직원·비고를 남긴다.
- `resolutionNote`는 선택이고, `assignTo`는 전환 본문에서 받지만 아직 UI가 담당자를 지정하지 않는다. `coalesce`로 기존 값을 유지한다.

## 데이터베이스 변경

- V58 additive 마이그레이션이 `guest_request`·`guest_request_event` 표와 `guest_request_detail` 뷰를 만든다. **기존 표를 변경하지 않는다.**
- `guest_request`는 지점(필수)·예약(선택)에 묶인다. 일반 문의는 예약 없이 접수되므로 `reservation_id`는 NULL 허용이고, 이 경우 남겨둔 연락처로만 답변한다.
- `guest_request_detail` 뷰가 지점 이름·담당자 이름을 조인한다. 서비스에서 조인을 매번 짜지 않게 한 곳에 정의했다.
- `priority`는 `LOW`·`NORMAL`·`HIGH` 제약이 있지만 현재 모두 `NORMAL`로만 만들어진다. UI 노출은 1차 범위가 아니다.

## 관리자 UI 변경

- `/dashboard/guest-requests`에 상태 필터 버튼·요청 표·검토 대화상자·상태 변경 대화상자를 추가했다.
- `nav.ts`의 `운영` 그룹에 `고객 요청`을 넣었다. 본사·지점 직원이 처리하므로 7번 감사 메뉴와 달리 **본사 전용이 아니다**. `HQ_ADMIN`·`BRANCH_STAFF`만 보고, 다른 역할은 안내문을 본다. `getNavGroupsForRole`이 `HQ_ADMIN`은 6개 그룹, `BRANCH_STAFF`는 `운영` 그룹만 반환한다.
- 상태 변경은 매번 `crypto.randomUUID()`로 새 멱원 키를 발급한다. 재시도가 같은 키를 재사용하지 않게 해서 네트워크 장애 뒤 다시 눌러도 중복 전환이 생기지 않는다.
- 서버 검증 실패 시 대화상자를 닫지 않고 안내를 보여준다. 다른 본사 화면과 같은 패턴이다.
- 검토 대화상자에 고객 연락처가 나온다. 화면 공유 시 주의 안내를 `CardDescription`에 넣었다. 7번 감사 메뉴의 마스킹과는 별개로, 이 화면은 처리를 담당하므로 원문이 필요하다.

## 고객 웹 변경

- `/contact`·`/en/contact`에 요청 접수 폼을 추가했다. `customer-route.ts`가 `/contact`를 `contact` 라우트로 분류하고, `App.tsx`가 `GuestRequestPage`를 그린다.
- 지점 선택기가 `GET /api/hotels` 목록을 쓴다. 폼 검증은 브라우저 `required`·`maxLength`가 하고, 유형 허용 여부·멱원은 서버가 최종 판단한다.
- 접수에 성공하면 접수 id 앞 8자리를 보여주고, 실패하면 안내를 낸다. 고객 웹은 세션 기반이 아니므로 접수 후 본인 요청을 다시 볼 수는 없다. 답변은 남긴 이메일로 받는다.

## 검증 결과

- API `mvnw -Dtest=GuestRequestIntegrationTest` **22건**이 종료 코드 0이다. 접수 201·같은 키 재호출 200 `created=false`·키 누락 400·빈 제목 400·알 수 없는 유형 400·없는 지점 404·본사 전 지점 조회·지점 직원 자기 지점만·본사 지점 필터·세션 없음 401·`limit` 범위 위반 400·음수 `offset` 400·알 수 없는 `status` 400·상세에 연락처·이력 포함·지점 직원 타 지점 상세 403·없는 요청 404·전환 200·전환 멱원 200·전환 세션 없음 401·지점 직원 타 지점 전환 403·알 수 없는 상태 400·없는 요청 전환 404를 다룬다.
- 인접 영역 `AuditIntegrationTest` 16건·`OperationsReportIntegrationTest` 18건·`StaffAccountIntegrationTest` 47건도 종료 코드 0이다. 새 표가 additive하므로 기존 영역에 영향이 없음을 확인했다.
- 관리자 `tsc --noEmit`·`eslint`(경고만, 기존 컴포넌트와 같은 `react-hooks/set-state-in-effect` 패턴)·Playwright `guest-requests` **7건**·인접 suite 49건이 종료 코드 0이다. 메뉴 노출·목록 라벨·상태 필터 전환·빈 상태·비운영 역할 안내·목록 장애 안내·검토 후 상태 전환을 다룬다.
- 고객 웹 `tsc --noEmit`과 `tsx` 검증 스크립트 25건이 종료 코드 0이다.

## 미검증 항목

- **라이브 DB·compose 재빌드·브라우저 확인은 실행하지 않았다.** V58이 라이브에 적용되지 않았으므로 실제 접수·조회·전환 흐름은 사용자가 확인한다. 마이그레이션이 additive하므로 기존 데이터는 영향을 받지 않는다.
- **담당자 지정 UI가 없다.** `transition` 본문이 `assignTo`를 받지만 관리자 화면은 상태 전환만 한다. 담당자 배정은 후속 항목이다.
- **요청 유형별 알림이 없다.** 긴급 요청이 들어와도 직원이 새로고침하기 전에 알 수 없다.
- **고객이 접수한 요청을 다시 볼 수 없다.** 세션 기반이 아니므로 접수 id를 안내만 한다. 조회 토큰을 만들려면 별도 설계가 필요하다.
- **연락처 노출을 마스킹하지 않는다.** 7번 감사 메뉴와 달리 처리 자체에 연락처가 필요하므로 원문을 보여준다. 화면 공유 주의 안내만 있다.
- **고객 요청 이력이 7번 감사 메뉴에 통합되지 않았다.** `guest_request_event`는 감사 `UNION ALL`에 없다. 후속 항목이다.
- **`priority`가 항상 `NORMAL`이다.** 제약은 있지만 만드는 경로가 하나뿐이다.
