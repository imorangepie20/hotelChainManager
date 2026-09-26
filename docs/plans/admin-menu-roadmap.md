# 관리자(Admin) 메뉴 로드맵

> 최종 갱신: 2026-09-27 (호텔·객실 및 재고·가격 쓰기 기능 감사 완료).

## 현재 메뉴 (`SDTPL_ADM/src/lib/nav.ts`)
| 그룹 | 메뉴 | 경로 | 권한 | 상태 |
|---|---|---|---|---|
| 운영 | 운영 대시보드 | `/dashboard/default` | 전 역할 | 구현됨 |
| 운영 | 예약 관리 | `/dashboard/reservations` | 전 역할 | 구현됨 |
| 운영 | 오늘의 운영 | `/dashboard/operations` | 전 역할 | 구현됨 |
| 운영 | 고객 요청 | `/dashboard/guest-requests` | `HQ_ADMIN`, `BRANCH_STAFF` | 구현됨 |
| 운영 | 내 계정 | `/dashboard/me` | 전 역할 | 구현됨 |
| 본사 관리 | 직원 권한 | `/dashboard/staff` | `HQ_ADMIN` | 구현됨 |
| 본사 관리 | 공통 정책 | `/dashboard/policies` | `HQ_ADMIN` | 구현됨 |
| 본사 관리 | 운영 통계 | `/dashboard/reports` | `HQ_ADMIN` | 구현됨 |
| 본사 관리 | 감사 이력 | `/dashboard/audit` | `HQ_ADMIN` | 구현됨 |
| 카탈로그 | 호텔 및 객실 | `/dashboard/hotels` | `HQ_ADMIN` | 구현됨 |
| 카탈로그 | 재고·가격 | `/dashboard/inventory` | `HQ_ADMIN` | 구현됨 |
| 재무 | 정산·대사 | `/dashboard/settlements` | `HQ_ADMIN` | 구현됨 |
| 콘텐츠 | 웹사이트 CMS | `/dashboard/website` | `HQ_ADMIN`, `HQ_EDITOR`, `HQ_PUBLISHER` | 구현됨 |
| AI | AI 도우미 운영 | `/dashboard/ai-operations` | `HQ_ADMIN` | 구현됨 |

지점 직원(`BRANCH_STAFF`)은 운영 그룹 5개, 본사 관리자는 14개 메뉴를 본다. CMS 그룹의 화면 내부에서만 `HQ_EDITOR`·`HQ_PUBLISHER`의 검토·발행 분리가 동작한다.

## 앞으로 구현해야 할 관리자 메뉴

범위는 [전체 구현 설계서](../architecture/full-site-implementation-design.md) 6.2 본사 업무와 12단계 6번 `본사 운영 확장`에서 정한 대로 한다. 첫 버전에서 제외하는 OTA·도어록·여권 수집·복잡한 회계·매출 최적화는 넣지 않는다.

### 1. 지점·객실 유형 관리 (HQ_ADMIN)

- 지점 생성·수정·판매 중지. 객실 유형·요금제(`rate_plan`·`rate_day`) 등록과 일자별 요금 변경.
- 현재: 읽기 전용 카탈로그(`GET /api/staff/hotels/{hotelId}/room-types`)와 `호텔 및 객실` (`/dashboard/hotels`) 메뉴가 객실 유형·요금제·요금 범위를 보여준다. `POST /api/staff/hotels`가 새 지점을 만들되 **지점 행만 만들고 객실 유형·요금·재고를 심지 않는다.** 시드는 객실 유형 추가가 담당한다. `GET /api/staff/hotels`가 지점 목록을 돌려주고(이전까지 관리자 화면은 3개 UUID를 하드코딩했다), 이름은 대소문자 구분 없이 중복을 409 `HOTEL_NAME_CONFLICT`로 거부하고 시간대를 `ZoneId.of`로 검증한다. `PATCH /api/staff/hotels/{hotelId}`가 지점의 이름·지역·시간대를 바꾸되 세 필드 모두 선택이고 보내지 않은 값은 유지하며, 이름을 바꿀 때만 자기 자신을 제외하고 중복을 검사하고, 빈 본문은 400이며, 실제로 바뀌는 값이 없으면 DB를 건드리지 않고 멱원 기록만 남긴다. `PATCH /api/staff/hotels/{hotelId}/active`가 지점의 판매를 중지·재개하되, **중지는 신규 판매에만 적용해서 이미 확정된 예약을 그대로 두고**, 중지한 지점은 고객 `GET /api/hotels`·`GET /api/availability`에서 빠지지만 직원 목록에는 계속 나타나며, 이미 같은 상태면 DB를 건드리지 않고 멱원 기록만 남긴다. 객실 유형 추가(`POST /api/staff/hotels/{hotelId}/room-types`)가 본사가 정한 **조식 포함 여부·기본 요금**을 받아 기본 요금제와 90일분 일자 요금·재고를 같은 트랜잭션에 심어서, 만든 직후 고객 가용성에 나타난다. 수정(`PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`)이 이름·최대 인원을 바꾸되, 인원을 내릴 때 확정 예약과 충돌하면 409로 거부하고, 조식 포함 여부는 확정 예약의 계약 조건이어서 충돌하면 409로 거부하며, 기본 요금은 주말·계절 차등을 보존한 채 일자별 금액을 같은 폭으로 옮긴다. `DELETE /api/staff/hotels/{hotelId}/room-types/{roomTypeId}`가 유형을 지우되, **진행 중인 예약·예약 변경 요청·보류 재고·배정된 실제 객실이 있으면 409 `ROOM_TYPE_DELETION_CONFLICT`로 거부하고 아무것도 지우지 않으며**, 종료된 예약·요금제는 `room_type_id` 참조만 끊고 보존해서 과거 운영 기록이 깨지지 않게 한다. `GET /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/defaults`가 수정 화면에 미리 채울 현재값을 돌려준다. `POST /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rate-plans`가 두 번째 요금제를 만들되, **요금제 행과 일자별 금액을 같은 트랜잭션에 심고 재고는 심지 않으며** 재고가 없는 날짜를 포함하면 409 `RATE_PLAN_INVENTORY_DAY_NOT_FOUND`로 거부한다. 조식 포함 여부·정책 버전이 다른 요금제가 같은 객실에서 동시에 팔리게 된다. `PATCH .../rate-plans/{ratePlanId}`가 이름만 바꾸고, 조식·정책은 확정 예약의 계약 조건이라 새 요금제를 만드는 것으로만 바꿀 수 있으며, 이름 중복은 같은 유형 안에서만 409 `RATE_PLAN_NAME_CONFLICT`로 거부한다. `GET .../rate-plans`가 유형의 전체 요금제를 돌려준다. `PATCH .../rates`·`GET .../rates`가 선택적 `ratePlanId`를 받아서 명시하지 않으면 기본 요금제를 쓴다.
- **후보 메뉴**: `호텔 및 객실` (`/dashboard/hotels`) — **읽기 전용 1차 + 지점 생성·지점 수정·지점 목록 조회·지점 판매 중지·재개 + 객실 유형 추가·초기 재고·요금·조식 여부 시드·수정·삭제 + 두 번째 요금제 등록·이름 수정 구현됨**
- 남음: 없음. 1번 영역이 완료됐다.

### 2. 재고·가격 관리 (HQ_ADMIN)

- 객실 유형별 일자 재고(`inventory_day`) 조회·조정, 판매 중지 구간 설정, 재고 일괄 업로드.
- 현재: 읽기 전용 조회(`GET /api/staff/hotels/{hotelId}/inventory`)와 `재고·가격` (`/dashboard/inventory`) 메뉴가 객실 유형별 일자 재고를 보여준다. `PATCH /api/staff/hotels/{hotelId}/inventory`가 본사가 객실 유형의 일자 총량을 바꾼다. 총량을 내릴 때 확정·보류 건수 아래로 내리면 409 `INVENTORY_CAPACITY_CONFLICT`로 거부하고, 시드되지 않은 일자는 404 `INVENTORY_DAY_NOT_FOUND`다. 0실은 판매 중지와 같다. 멱원 키와 `inventory_command`의 요청 지문으로 같은 요청의 중복 조정을 막는다. `GET /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/rates`가 일자별 요금을 읽어주고 `PATCH .../rates`가 날짜별 금액을 덮어쓴다. `rate_day` 행이 없는 날짜는 404 `RATE_DAY_NOT_FOUND`, 요금제가 없는 유형은 404 `ROOM_TYPE_RATE_PLAN_NOT_FOUND`다. `PATCH /api/staff/hotels/{hotelId}/room-types/{roomTypeId}/sales-status`가 날짜 구간의 판매를 중지·재개하되, **총량은 건드리지 않고 신규 판매만 막아서** 재개하면 중지 전과 같은 재고가 돌아오고, 확정·보류 중인 예약이 있어도 중지할 수 있으며, 중지한 일자는 고객 `GET /api/availability`에서 빠진다. `GET .../sales-status`가 중지 구간을 돌려주고, 재고 화면의 각 일자가 `salesStatus`를 노출해서 "매진"과 "중지"를 구분한다. `GET .../inventory/export`가 객실 유형별 일자 재고를 CSV로 내려주고 `POST .../inventory/import`가 CSV로 여러 객실 유형의 여러 날짜 재고를 한 번에 바꾸되, **총량 열만 반영하고 한 파일의 모든 행을 한 트랜잭션에 처리**해서 전부 성공하거나 전부 실패하고, 형식 오류는 400 `INVENTORY_IMPORT_FORMAT`으로 거부한다. 내보낸 파일을 그대로 다시 올릴 수 있다.
- **후보 메뉴**: `재고·가격` (`/dashboard/inventory`) — **읽기 전용 1차 + 일자 총량 조정 + 일자별 요금 개별 변경 + 판매 중지 구간 설정 + 재고 일괄 업로드(CSV) 구현됨**
- 남음: 없음. 2번 영역이 완료됐다.

### 3. 직원 계정 관리 (HQ_ADMIN)

- 직원 생성·역할 변경(`HQ_ADMIN`·`HQ_EDITOR`·`HQ_PUBLISHER`·`BRANCH_STAFF`)·지점 할당·비밀번호 초기화·비활성.
- 현재: `GET /api/staff/staff`가 전직원 목록을 반환하고 `POST /api/staff/staff`가 새 직원을 만든다. `POST /api/staff/staff/{staffId}/password`가 임시 비밀번호를 재발급하고 쿨다운(기본 300초) 내 연속 재발급을 409로 막는다. `PATCH /api/staff/staff/{staffId}`가 역할과 소속 지점을 바꾸되, 본사 역할은 지점을 가질 수 없고 지점 직원은 지점이 필수이며, 본인 계정의 변경은 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`로 거부한다. `PATCH /api/staff/staff/{staffId}/active`가 비활성·재활성을 하되, 비활성 직원의 세션을 즉시 삭제하고 로그인과 세션 사용을 403으로 거부한다. `DELETE /api/staff/staff/{staffId}`가 계정을 영구 삭제하되 **행을 남기고 식별자만 영구적으로 비워서** 감사 이력·예약 변경 승인·콘텐츠 발행 이력 20개 표의 참조가 끊기지 않게 하고, 이메일을 `deleted-<uuid>@deleted.local` 자리 표시자로, 표시 이름을 `삭제된 직원`으로, 비밀번호를 빈 문자열로, 역할을 `REMOVED`로 바꿔서 다시 로그인할 수 없게 하며, 진행 중인 예약 변경 요청·정산 실행이 있으면 409 `STAFF_DELETION_CONFLICT`로 거부하고 아무것도 비우지 않는다. 본인 계정은 409 `STAFF_SELF_MODIFICATION_FORBIDDEN`로 거부한다. 삭제된 직원은 `role <> 'REMOVED'`로 목록에서 빠진다. `PATCH /api/staff/staff/me`가 본인의 표시 이름·비밀번호를 바꾸되 **역할·소속 지점은 받지 않고**, 비밀번호를 바꿀 때는 현재 비밀번호 확인이 필수이고 틀리면 400 `STAFF_PASSWORD_MISMATCH`이며, 비밀번호를 바꾸면 현재 세션 하나만 남기고 나머지를 끊는다. 전 역할이 쓸 수 있다. `직원 권한` (`/dashboard/staff`) 메뉴가 직원 표와 추가·역할 수정·재발급·활성 전환·삭제 대화상자를 보여주고, `내 계정` (`/dashboard/me`) 메뉴가 전 역할에게 본인 표시 이름·비밀번호 수정을 제공한다. 임시 비밀번호는 발급 시 한 번만 내보낸다.
- **후보 메뉴**: `직원 권한` (`/dashboard/staff`) + `내 계정` (`/dashboard/me`) — **조회·생성·역할·소속 지점 수정·비밀번호 재발급·비활성·재활성·삭제·본인 계정 수정 구현됨**
- 남음: 없음. 3번 영역이 완료됐다.

### 4. 운영 통계·리포트 (HQ_ADMIN)

- 지점별 매출·점유율·취소율·노쇼율과 예약 변경 승인 대기·완료 건수.
- 현재: `GET /api/staff/reports/operations`가 지점별 예약 건수·취소·노쇼·만료·매출·점유율과 변경 승인 대기·완료 건수를 반환하고 `GET /api/staff/reports/operations/room-types`가 지점의 객실 유형별 예약·취소·노쇼·매출·매출 비중을 반환한다. 둘 다 SELECT만 사용한다. `운영 통계` (`/dashboard/reports`) 메뉴가 기간 프리셋·전체 합계 카드·지점별 표·객실 유형별 매출 표·CSV 내보내기를 보여준다.
- **후보 메뉴**: `통계·리포트` (`/dashboard/reports`) — **읽기 전용 1차 + 객실 유형별 상세 매출 + CSV 내보내기 구현됨**
- 남음: 차트 시각화, 이전 기간 대비 증감, 영문 전환, Excel 내보내기(CSV만 있다).

### 5. 공통 정책 관리 (HQ_ADMIN)

- 취소 정책 기준(체크인 며칠 전까지 전액 환급, 마감 시각), 예약 변경 승인 한도(지점 직접 승인 가능 차액), 환불 규칙.
- 현재: `GET /api/staff/policies`가 취소 정책·변경 승인 한도·변경 승인 TTL·정산 활성 여부를 반환하고 `PUT /api/staff/policies/cancellation`이 취소 마감 일수·마감 시각을, `PUT /api/staff/policies/change-limit`가 지점 직접 승인 한도를, `PUT /api/staff/policies/change-approval-ttl`이 본사 승인 대기 시간을 변경한다. `GET /api/staff/policies/revisions`가 변경 이력을 최신순으로 반환한다. `공통 정책` (`/dashboard/policies`) 메뉴가 현재값·변경 대화상자·변경 이력 표를 보여준다. 멱원 키와 `policy_revision` 이력으로 중복 변경을 막는다.
- **후보 메뉴**: `공통 정책` (`/dashboard/policies`) — **조회·취소 정책·승인 한도·승인 TTL 변경·이력 조회 구현됨**
- 남음: 환불 규칙, 지점별 정책, 정책 변경의 감사 이력(7번 메뉴) 통합. **1번·2번·3번 영역이 끝났으므로 로드맵 순서에 따라 이 영역이 다음 작업이다.**

### 6. 고객 요청 관리 (지점 직원)

- 고객의 예약 변경·취소 요청과 일반 문의를 받고 처리 상태를 표시.
- 현재: `POST /api/hotels/{hotelId}/guest-requests`가 고객의 객실·편의 요청·환불 문의·일반 문의를 받는다. 예약 변경·취소는 전용 API가 있으므로 이 API에서 처리하지 않는다. `GET /api/staff/guest-requests`가 본사는 전 지점을, 지점 직원은 자기 지점만 돌려주고 `GET /api/staff/guest-requests/{requestId}`가 연락처·내용·처리 이력을 돌려준다. `POST /api/staff/guest-requests/{requestId}/transition`이 처리 상태를 `OPEN`→`IN_PROGRESS`→`RESOLVED`→`CLOSED`로 바꾼다. 멱원은 두 갈래로 동작한다. `고객 요청` (`/dashboard/guest-requests`) 메뉴가 상태 필터·표·검토 대화상자·상태 변경 대화상자를 보여준다. 고객 웹 `/contact`·`/en/contact`가 요청 접수 폼을 제공한다.
- **후보 메뉴**: `고객 요청` (`/dashboard/guest-requests`) — **1차 구현됨**
- 남음: 담당자 지정 UI(현재는 상태 전환만 있다, `transition` 본문은 `assignTo`를 받는다), 요청 유형별 알림, 고객 요청 이력의 감사 메뉴(7번) 통합, `priority` 노출.

### 7. 감사·이력 조회 (HQ_ADMIN)

- V29~V37 감사 테이블(예약자 정정·인원·객실 재배정·일정 변경·취소·운영 상태 전환)의 통합 조회.
- 현재: `GET /api/staff/audit`가 8종 감사 이력을 발생 시각 내림차순으로 반환하고 `GET /api/staff/audit?masked=true`가 고객 이름·이메일과 `GUEST_UPDATE` 요약의 개인정보를 가린다. `감사 이력` (`/dashboard/audit`) 메뉴가 유형별 표·더 보기 페이지 이동·개인정보 마스킹 토글·CSV 내보내기를 보여준다. SELECT만 사용한다.
- **후보 메뉴**: `감사 이력` (`/dashboard/audit`) — **1차 + 개인정보 마스킹 + CSV 내보내기 구현됨**
- 남음: Excel(xlsx) 내보내기(CSV만 있다), 고객 요청 이력(6번 메뉴와 겹침).

### 8. AI 도우미 운영 (HQ_ADMIN)

- `/chat` 응답 정책 위반 건수·LLM `outcome` 측정(`success`·`schema_rejected`·`unparsable`·`empty_response`·`api_error`·`no_key`)·지연 추이.
- 현재: `/metrics/llm`이 프로세스 단위 측정을 노출하고 `AI 운영` (`/dashboard/ai-operations`) 메뉴가 결과별 호출 수·평균 지연을 읽기 전용으로 보여준다. 메뉴와 측정은 `HQ_ADMIN`만 쓸 수 있다.
- **후보 메뉴**: `AI 운영` (`/dashboard/ai-operations`) — **1차 구현됨**
- 남음: 측정이 프로세스 재시작으로 0으로 돌아간다. 영구 보관·시계열 집계·정책 위반 건수 노출은 아직이다.

## 완료 기준

1. 각 메뉴를 추가할 때 `nav.ts`의 그룹과 역할 매핑을 먼저 정하고, 본사 전용 메뉴는 `getNavGroupsForRole`에서 `HQ_ADMIN`에만 노출한다.
2. 관리자 UI는 SDTPL_ADM의 기존 테마·공통 컴포넌트(표·달력·대화상자)를 우선 재사용하고, 키보드·390px 동작을 확인한다.
3. 가격·재고·예약 확정의 권한은 Spring Boot에 있다. 관리자 UI는 서버가 검증한 결과만 표시한다.
4. 메뉴 추가 순서는 1 → 2 → 3 → 5 → 4 → 7 → 6 → 8을 따른다. 정책과 직원 관리가 본사 운영의 기반이고, 통계·감사는 데이터가 쌓인 뒤에 의미가 있다.

## 미구현 사유

- OTA·도어록·여권 수집·복잡한 회계·매출 최적화: 핵심 원칙에서 첫 버전 제외 대상으로 정했다.
- 마케팅·캠페인·프로모션 관리: CMS 7.2 6단계의 `오퍼·쿠폰·외부 미디어 연동`과 묶여 있으며 후속 단계다.
- CDN·객체 저장소 관리: CMS 7.2 6단계 범위다.
