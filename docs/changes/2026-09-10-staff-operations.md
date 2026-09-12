# 직원 객실 운영 흐름 변경 기록

## 구현 내용
- 판매 단위 `room_type`과 실제 배정 단위 `physical_room`을 분리했다.
- 실제 객실, 예약별 객실 배정, 객실 청소 상태를 Flyway V7에 추가했다.
- 직원 세션과 지점 권한을 확인하는 객실 배정·체크인·체크아웃·청소 완료 API를 구현했다.
- 체크아웃은 예약을 `CHECKED_OUT`으로 전환하고, 배정 객실을 `NEEDS_CLEANING`으로 전환한다. 청소 완료만 객실을 `CLEAN`으로 되돌린다.
- 관리자 당일 운영 화면에 도착·출발·청소 필요 목록을 표시하고, 체크인·체크아웃·청소 완료 처리를 연결했다. 성공하면 목록을 다시 불러온다.
- `GET /api/staff/reservations/{reservationId}/assignable-rooms`를 추가했다. 동일 지점·객실 유형의 청결 객실 중, 다른 활성 예약과 숙박 기간이 겹치지 않는 객실만 반환한다.
- 당일 도착 목록에서 배정 후보를 조회하고 객실 번호를 선택해 `POST /api/staff/reservations/{reservationId}/assignments`를 실행하도록 연결했다.
- 예약 확정 상태만 `POST /api/staff/reservations/{reservationId}/no-show`로 노쇼 처리할 수 있게 했다. 기존 객실 배정은 함께 해제하며, 당일 도착 목록에서 처리 후 성공 안내와 목록 갱신을 제공한다.

## 검증
- `mvnw.cmd -q -Dtest=StaffOperationsIntegrationTest test` 통과.
- 지점 직원의 객실 배정 → 체크인 → 체크아웃 → 청소 완료 상태 전이를 PostgreSQL 통합 테스트로 확인했다.
- API Docker 이미지 재빌드와 `http://127.0.0.1:4080/actuator/health/readiness` HTTP 200을 확인했다.
- 당일 운영 화면의 체크아웃·청소 완료 E2E 통과. API 요청과 성공 안내, 목록 재조회 흐름을 확인했다.
- 실제 객실 후보 조회를 `StaffOperationsIntegrationTest`에 추가해 통과했다.
- 당일 운영 E2E에서 객실 후보 조회·702호 배정·성공 안내·목록 재조회, 체크아웃, 청소 완료를 확인했다.
- 노쇼 상태 전환을 PostgreSQL 통합 테스트로, 객실 배정 뒤 노쇼 처리와 목록 재조회를 관리자 E2E로 확인했다.

## 다음 작업
- 객실 변경, 여러 객실 배정, 객실 점검·판매 중지는 아직 구현하지 않았다.
