# 관리자(Admin) 메뉴 로드맵

> 최종 갱신: 2026-09-20. 작업 트리에만 있고 커밋하지 않은 상태.
## 현재 메뉴 (`SDTPL_ADM/src/lib/nav.ts`)
| 그룹 | 메뉴 | 경로 | 권한 | 상태 |
|---|---|---|---|---|
| 운영 | 운영 대시보드 | `/dashboard/default` | 전 역할 | 구현됨 |
| 운영 | 예약 관리 | `/dashboard/reservations` | 전 역할 | 구현됨 |
| 운영 | 오늘의 운영 | `/dashboard/operations` | 전 역할 | 구현됨 |
| 본사 관리 | 직원 권한 | `/dashboard/staff` | `HQ_ADMIN` | 구현됨 |
| 본사 관리 | 공통 정책 | `/dashboard/policies` | `HQ_ADMIN` | 구현됨 |
| 본사 관리 | 운영 통계 | `/dashboard/reports` | `HQ_ADMIN` | 구현됨 |
| 본사 관리 | 감사 이력 | `/dashboard/audit` | `HQ_ADMIN` | 구현됨 |
| 카탈로그 | 호텔 및 객실 | `/dashboard/hotels` | `HQ_ADMIN` | 구현됨 |
| 카탈로그 | 재고·가격 | `/dashboard/inventory` | `HQ_ADMIN` | 구현됨 |
| 재무 | 정산·대사 | `/dashboard/settlements` | `HQ_ADMIN` | 구현됨 |
| 콘텐츠 | 웹사이트 CMS | `/dashboard/website` | `HQ_ADMIN`, `HQ_EDITOR`, `HQ_PUBLISHER` | 구현됨 |
| AI | AI 도우미 운영 | `/dashboard/ai-operations` | `HQ_ADMIN` | 구현됨 |

지점 직원(`BRANCH_STAFF`)은 운영 그룹 3개만, 본사 관리자는 12개 메뉴를 본다. CMS 그룹의 화면 내부에서만 `HQ_EDITOR`·`HQ_PUBLISHER`의 검토·발행 분리가 동작한다.

## 앞으로 구현해야 할 관리자 메뉴

범위는 [전체 구현 설계서](../architecture/full-site-implementation-design.md) 6.2 본사 업무와 12단계 6번 `본사 운영 확장`에서 정한 대로 한다. 첫 버전에서 제외하는 OTA·도어록·여권 수집·복잡한 회계·매출 최적화는 넣지 않는다.

### 1. 지점·객실 유형 관리 (HQ_ADMIN)

- 지점 생성·수정·판매 중지. 객실 유형·요금제(`rate_plan`·`rate_day`) 등록과 일자별 요금 변경.
- 현재: 읽기 전용 카탈로그(`GET /api/staff/hotels/{hotelId}/room-types`)와 `호텔 및 객실` (`/dashboard/hotels`) 메뉴가 객실 유형·요금제·요금 범위를 보여준다. 객실 유형 추가(`POST /api/staff/hotels/{hotelId}/room-types`)가 기본 요금제와 90일분 일자 요금·재고를 같은 트랜잭션에 심어서, 만든 직후 고객 가용성에 나타난다.
- **후보 메뉴**: `호텔 및 객실` (`/dashboard/hotels`) — **읽기 전용 1차 + 객실 유형 추가·초기 재고·요금 시드 구현됨**
- 남음: 지점 생성·수정·판매 중지, 객실 유형 수정·삭제, 요금제 등록·수정, 일자별 요금 변경.

### 2. 재고·가격 관리 (HQ_ADMIN)

- 객실 유형별 일자 재고(`inventory_day`) 조회·조정, 판매 중지 구간 설정, 재고 일괄 업로드.
- 현재: 읽기 전용 조회(`GET /api/staff/hotels/{hotelId}/inventory`)와 `재고·가격` (`/dashboard/inventory`) 메뉴가 객실 유형별 일자 재고를 보여준다. 조정·판매 중지·일괄 업로드는 없다.
- **후보 메뉴**: `재고·가격` (`/dashboard/inventory`) — **읽기 전용 1차 구현됨**

### 3. 직원 계정 관리 (HQ_ADMIN)

- 직원 생성·역할 변경(`HQ_ADMIN`·`HQ_EDITOR`·`HQ_PUBLISHER`·`BRANCH_STAFF`)·지점 할당·비밀번호 초기화·비활성.
- 현재: `GET /api/staff/staff`가 전직원 목록을 반환하고 `POST /api/staff/staff`가 새 직원을 만든다. `POST /api/staff/staff/{staffId}/password`가 임시 비밀번호를 재발급하고 쿨다운(기본 300초) 내 연속 재발급을 409로 막는다. `직원 권한` (`/dashboard/staff`) 메뉴가 직원 표와 추가·재발급 대화상자를 보여준다. 임시 비밀번호는 발급 시 한 번만 내보낸다.
- **후보 메뉴**: `직원 권한` (`/dashboard/staff`) — **조회·생성·비밀번호 재발급 구현됨**
- 남음: 비활성·삭제, 역할 변경·지점 재할당, 본인 계정 수정.

### 4. 운영 통계·리포트 (HQ_ADMIN)

- 지점별 매출·점유율·취소율·노쇼율과 예약 변경 승인 대기·완료 건수.
- 현재: `GET /api/staff/reports/operations`가 지점별 예약 건수·취소·노쇼·만료·매출·점유율과 변경 승인 대기·완료 건수를 반환한다. `운영 통계` (`/dashboard/reports`) 메뉴가 기간 프리셋·전체 합계 카드·지점별 표를 보여준다. SELECT만 사용한다.
- **후보 메뉴**: `통계·리포트` (`/dashboard/reports`) — **읽기 전용 1차 구현됨**
- 남음: 객실 유형별·요금제별 상세 매출, 차트 시각화, CSV·Excel 내보내기, 이전 기간 대비 증감, 영문 전환.

### 5. 공통 정책 관리 (HQ_ADMIN)

- 취소 정책 기준(체크인 며칠 전까지 전액 환급, 마감 시각), 예약 변경 승인 한도(지점 직접 승인 가능 차액), 환불 규칙.
- 현재: `GET /api/staff/policies`가 취소 정책·변경 승인 한도·정산 활성 여부를 반환하고 `PUT /api/staff/policies/cancellation`이 취소 마감 일수·마감 시각을, `PUT /api/staff/policies/change-limit`가 지점 직접 승인 한도를 변경한다. `GET /api/staff/policies/revisions`가 변경 이력을 최신순으로 반환한다. `공통 정책` (`/dashboard/policies`) 메뉴가 현재값·변경 대화상자·변경 이력 표를 보여준다. 멱원 키와 `policy_revision` 이력으로 중복 변경을 막는다.
- **후보 메뉴**: `공통 정책` (`/dashboard/policies`) — **조회·취소 정책·승인 한도 변경·이력 조회 구현됨**
- 남음: 승인 TTL 런타임 변경, 환불 규칙, 지점별 정책, 정책 변경의 감사 이력(7번 메뉴) 통합.

### 6. 고객 요청 관리 (지점 직원)

- 고객의 예약 변경·취소 요청과 일반 문의를 받고 처리 상태를 표시.
- 현재: 고객 변경 요청(`reservationchange`)은 직원이 처리하지만 요청 자체를 받는 채널이 없다. 고객이 직접 시작한 변경만 있다.
- **후보 메뉴**: `고객 요청` (`/dashboard/guest-requests`)

### 7. 감사·이력 조회 (HQ_ADMIN)

- V29~V37 감사 테이블(예약자 정정·인원·객실 재배정·일정 변경·취소·운영 상태 전환)의 통합 조회.
- 현재: `GET /api/staff/audit`가 8종 감사 이력을 발생 시각 내림차순으로 반환하고 `감사 이력` (`/dashboard/audit`) 메뉴가 유형별 표와 더 보기 페이지 이동을 보여준다. SELECT만 사용한다.
- **후보 메뉴**: `감사 이력` (`/dashboard/audit`) — **1차 구현됨**
- 남음: 개인정보 마스킹 옵션, CSV·Excel 내보내기, 고객 요청 이력(6번 메뉴와 겹침).

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
