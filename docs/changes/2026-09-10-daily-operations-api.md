# 당일 운영 조회 API 변경 기록

## 변경 이유

지점 직원이 당일 도착·출발 예약과 청소 필요 객실을 한 화면에서 확인할 수 있어야 한다.

## 구현 내용

- `GET /api/staff/hotels/{hotelId}/operations?date=YYYY-MM-DD`를 추가했다.
- 직원 세션과 지점 접근 권한을 먼저 검증한다.
- 도착 예정 예약(`CONFIRMED`), 출발 예정 투숙 예약(`CHECKED_IN`), 청소 필요 객실(`NEEDS_CLEANING`)을 각각 반환한다.
- 예약 목록에는 배정된 실제 객실 번호를 포함한다.

## 검증

- `mvnw.cmd -q -Dtest=StaffOperationsIntegrationTest test` 통과.
- API Docker 이미지 재빌드 후 준비 상태 API가 HTTP 200을 반환하는 것을 확인했다.

## 다음 작업

- 관리자 화면에 일일 운영 목록을 연결한다.
