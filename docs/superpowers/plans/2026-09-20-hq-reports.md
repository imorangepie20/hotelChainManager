# 본사 운영 통계·리포트 첫 단계

> 상태: 구현 완료. 최종 갱신: 2026-09-20. 상세 내용은 [변경 기록](../../changes/2026-09-20-hq-reports.md)을 따른다.

## 배경

- `admin-menu-roadmap.md` 4번 `운영 통계·리포트`가 다음 대상이다. 1·2·3·5·7번 메뉴의 읽기·첫 쓰기 동작은 끝났다.
- `reservation`·`reservation_change`·`payment` 데이터는 있으나 집계 쿼리와 본사 화면이 없다. 로드맵 순서상 4번은 5번·7번 뒤에 왔는데, 이제 데이터가 의미를 가질 만큼 쌓였다.
- 본사가 지점별 매출·점유율·취소율·노쇼율과 예약 변경 승인 대기·완료 건수를 하루 단위로 확인하는 첫 버전을 만든다. 통계는 읽기 전용이다.

## 완료 기준

1. `GET /api/staff/reports/operations`가 본사 세션으로 일자별·지점별 운영 통계를 반환한다. SELECT만 사용하고 상태를 변경하지 않는다.
2. 응답은 지점별로 예약 건수·매출·점유율·취소율·노쇼율과 예약 변경 승인 대기·완료 건수를 묶는다.
3. `from`·`to`·`hotelId` 필터를 지원한다. 기간은 최대 92일이고 위반은 400, 없는 지점은 404다.
4. `HQ_ADMIN`만 호출 가능. 지점 직원 403, 잘못된 세션 401.
5. 집계는 지점 현지 시간대(`Asia/Seoul`) 기준 날짜로 묶는다.
6. 관리자 4001 `/dashboard/reports`의 `통계·리포트` 화면이 기간 프리셋(7일·14일·30일)과 지점별 표를 쓴다. `nav.ts`의 `본사 관리` 그룹에 노출한다.
7. 서버 통합 테스트와 관리자 TypeScript·ESLint·Playwright 시나리오가 종료 코드 0이다.

## 구현 범위

### 서버 (services/api)

- `reports/OperationsReportView`: 지점별 통계와 전체 합계를 담는 읽기 전용 레코드.
- `reports/OperationsReportService`: `reservation`·`reservation_change_request`를 지점 현지 날짜로 묶어 집계한다. 모든 쿼리가 SELECT다.
- `reports/ReportController`: `GET /api/staff/reports/operations`. `requireHeadquarters`로 본사만 허용한다.
- additive 마이그레이션은 없다. 기존 표에 컬럼을 추가하지 않는다.

### 관리자 (SDTPL_ADM)

- `staff-api.ts`에 `getOperationsReport`·`OperationsReport`·`HotelOperationsMetrics` 타입을 추가한다.
- `components/hotel-admin/operations-report.tsx`: 기간 프리셋·지점별 표·전체 합계 카드. `HQ_ADMIN`만 노출.
- `nav.ts`의 `본사 관리` 그룹에 `통계·리포트`를 추가한다.

### 테스트

- `OperationsReportIntegrationTest`: 본사 조회 200, 지점 403, 잘못된 세션 401, 기간 위반 400, 없는 지점 404, 건수·매출 집계 일치, 취소율·노쇼율 계산, 변경 승인 대기·완료 건수, 빈 기간 0.
- `e2e/operations-report.spec.ts`: 메뉴 노출 권한, 기간 프리셋 전환, 지점별 표시, 지점 직원 안내, 장애 안내.

## 미구현 예정 항목

- 객실 유형별·요금제별 상세 매출. 첫 버전은 지점 단위 집계다.
- 차트 시각화. 표와 요약 카드만 제공한다.
- CSV·Excel 내보내기.
- 일정 기간 대비 이전 기간 증감(전년 동기·전주 대비).
- 영문 전환. 한국어 단일 언어다.
