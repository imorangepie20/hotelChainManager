# 직원 객실 운영 흐름 변경 기록

## 구현 내용
- 판매 단위 `room_type`과 실제 배정 단위 `physical_room`을 분리했다.
- 실제 객실, 예약별 객실 배정, 객실 청소 상태를 Flyway V7에 추가했다.
- 직원 세션과 지점 권한을 확인하는 객실 배정·체크인·체크아웃·청소 완료 API를 구현했다.
- 체크아웃은 예약을 `CHECKED_OUT`으로 전환하고, 배정 객실을 `NEEDS_CLEANING`으로 전환한다. 청소 완료만 객실을 `CLEAN`으로 되돌린다.

## 검증
- `mvnw.cmd -q -Dtest=StaffOperationsIntegrationTest test` 통과.
- 지점 직원의 객실 배정 → 체크인 → 체크아웃 → 청소 완료 상태 전이를 PostgreSQL 통합 테스트로 확인했다.
- API Docker 이미지 재빌드와 `http://127.0.0.1:4080/actuator/health/readiness` HTTP 200을 확인했다.

## 다음 작업
- 관리자 당일 도착·출발·객실 배정·청소 목록과 실행 UI를 연결한다.
- 노쇼, 객실 변경, 여러 객실 배정, 객실 점검·판매 중지는 아직 구현하지 않았다.
