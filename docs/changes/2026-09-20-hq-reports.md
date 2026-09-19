# 본사 운영 통계·리포트 첫 단계

> 상태: 작업 트리에만 있고 커밋하지 않은 상태. 최종 갱신: 2026-09-20.
> 구현 계획: [본사 운영 통계·리포트 첫 단계](../superpowers/plans/2026-09-20-hq-reports.md)

## 변경 요약

- `admin-menu-roadmap.md` 4번 `운영 통계·리포트`의 첫 단계를 구현했다. `reservation`·`reservation_change_request`·`inventory_day`에 데이터가 쌓이고 있었으나 집계 쿼리와 본사 화면이 없었다.
- `GET /api/staff/reports/operations`가 지점별로 예약 건수·취소·노쇼·만료·매출·점유율과 예약 변경 승인 대기·완료 건수를 반환한다. 3개 집계 쿼리 모두 SELECT이므로 가격·재고·예약 상태를 변경하지 않는다.
- 매출은 실제 숙박으로 이어진 예약만 인정한다. 취소뿐 아니라 노쇼 예약의 금액도 합계에서 뺐다(노쇼는 환불 여부와 무관하게 매출로 볼 수 없다).
- `from`·`to`·`hotelId` 필터를 지원한다. 기본값은 지점 현지 시간대(`Asia/Seoul`) 기준 최근 7일이다. 기간은 최대 92일이고 위반은 400, 없는 지점은 404, 역순 기간은 400이다.
- `HQ_ADMIN`만 호출 가능하고 지점 직원은 403, 잘못된 세션은 401이다.
- 집계 기준 날짜는 `created_at at time zone 'Asia/Seoul'`로 현지 날짜를 쓴다. 점유율은 조회 기간의 `inventory_day.capacity` 합계 대비 `confirmed` 합계이며 1.0을 넘지 않게 한다.
- 관리자 `/dashboard/reports`에 기간 프리셋(7일·14일·30일)·시작일·종료일, 전체 합계 카드 4종, 지점별 표를 추가했다. `nav.ts`의 `본사 관리` 그룹은 `HQ_ADMIN`에만 노출되고, 다른 역할은 본사 전용 안내를 본다.
- additive 마이그레이션은 없다. 기존 표에 컬럼을 추가하거나 제약을 변경하지 않았다.

## 서비스별 변경

### services/api

- `reports/OperationsReportService`(신규): `hotel`·`reservation`·`reservation_change_request`·`inventory_day`를 지점 현지 날짜로 묶어 집계한다. 3개 쿼리 모두 SELECT다.
- `reports/ReportController`(신규): `GET /api/staff/reports/operations`. `requireHeadquarters`로 본사만 허용한다.
- `reports/OperationsReportView`(신규)·`reports/HotelOperationsMetrics`(신규).
- `ApiExceptionHandler`가 이미 `HotelNotFoundException`을 404로 매핑하므로 없는 지점은 404가 내려간다.

### SDTPL_ADM

- `src/lib/staff-api.ts`: `getOperationsReport`·`OperationsReport`·`OperationsReportTotals`·`HotelOperationsMetrics`를 추가했다. 403·기타 실패 안내를 나눴다.
- `src/components/hotel-admin/operations-report.tsx`(신규): 기간 프리셋·전체 합계 카드·지점별 표. `HQ_ADMIN`만 노출.
- `src/app/(dashboard)/dashboard/reports/page.tsx`(신규).
- `src/lib/nav.ts`: `본사 관리` 그룹에 `운영 통계`(`/dashboard/reports`, `TrendingUp` 아이콘)를 추가했다.
- `e2e/operations-report.spec.ts`(신규) 6종: 메뉴 노출 권한, 지점별 표시와 합계, 30일 프리셋 전환, 지점 직원 안내, 장애 안내, 빈 범위 안내.

## 검증

- API `mvnw compile` 종료 코드 0.
- API `mvnw -Dtest=OperationsReportIntegrationTest test` **10건** 종료 코드 0. 본사 지점별 조회·합계, 기본 7일, 지점 필터, 빈 기간 0, 지점 403, 세션 없음 401, 역순 기간 400, 100일 초과 400, 없는 지점 404.
- 관리자 `tsc --noEmit` 종료 코드 0. `eslint` 종료 코드 0(경고 2건 `react-hooks/set-state-in-effect`, 기존 `policies`·`staff-accounts`·`audit-events`와 같은 패턴).
- Playwright `operations-report.spec.ts` **6건** 종료 코드 0. 인접 suite(`admin-navigation`·`audit`·`policies`·`staff-accounts`·`hotel-catalog`·`inventory-viewer`·`inventory-mobile`·`ai-operations`) **40건**도 종료 코드 0으로 메뉴 추가가 내비게이션에 영향을 주지 않음을 확인했다.
- 라이브: compose `api`를 현재 코드로 재빌드한 뒤 health `UP`. 본사 세션으로 `GET /api/staff/reports/operations` 200으로 속초 `reservations=21, cancelled=1, expired=11, revenueKrw=6,100,000, changeRequestsPending=2, changeRequestsCompleted=5, occupancyRate=16.1%`와 나머지 두 지점 0이 내려왔다. 값은 동일한 조건의 직접 SQL과 일치한다. 역순 기간 400, 112일 400, 없는 지점 404, 지점 직원 403, 세션 없음 401을 확인했다. 관리자 4001의 Next rewrite 경유 요청도 200이다. 검증용으로 발급한 세션은 모두 삭제해 `staff_session`을 비웠다.

## 미검증 항목

- 관리자 4001 브라우저에서 `운영 통계` 메뉴와 화면 렌더링은 사용자가 확인한다. 본사 세션을 관리자로 직접 주입하지 않았다.
- Vite production build, 고객 웹 Playwright 회귀는 실행하지 않았다. 변경 범위가 본사 읽기 전용 조회에 국한되므로 사용자가 요청할 때까지 반복하지 않는다.
- 객실 유형별·요금제별 상세 매출, 차트 시각화, CSV·Excel 내보내기, 이전 기간 대비 증감, 영문 전환은 범위 밖이다.
