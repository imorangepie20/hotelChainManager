# 고객 예약 전화번호 입력

## 변경 이유

고객 체크아웃에 이름과 이메일만 있어 호텔이 긴급 연락에 사용할 전화번호를 받을 수 없었다.

## 구현

- 체크아웃에 필수 `tel` 입력을 추가하고 영문 화면에는 `Phone number`로 표시한다.
- 예약 생성 요청의 `guest.phone`을 서버가 받아 `reservation.guest_phone`에 저장한다.
- 기존 API 호출과 과거 예약은 전화번호가 없어도 조회되도록 nullable 컬럼과 2개 인자 `ReservationGuest` 생성자를 유지한다.
- 전화번호는 예약 생성 멱등성 fingerprint에 포함하며 최대 30자로 제한한다.

## 검증

- 고객 문구 계약, TypeScript, production build를 실행한다.
- API compile/test-compile로 DTO·SQL·기존 테스트 호환성을 확인한다.
- PostgreSQL 통합 테스트에는 전화번호 저장·조회 회귀를 추가한다.

## 미검증

브라우저 입력 동작과 PostgreSQL 통합 테스트 실행은 사용자가 진행한다.
