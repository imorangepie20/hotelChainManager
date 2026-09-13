# 예약 변경 승인·차액 정산

## 구현 범위

- 체크인 전 `CONFIRMED`·미배정 예약의 일정·객실 유형 변경을 즉시 저장 방식에서 요청·견적·승인·정산·적용 상태 흐름으로 전환했다.
- 지점 직원은 절대 차액 100,000원까지 직접 승인하고, 이를 넘는 요청은 본사 승인 대기열에서 승인 또는 사유와 함께 반려한다. 본사 관리자는 모든 금액을 승인할 수 있다.
- 승인된 변경은 대상 숙박 재고를 15분간 별도 hold한다. 추가 결제는 고객 결제 링크, 환불은 원결제 부분 환불 명령으로 처리하며 성공한 정산만 예약과 `reservation_night`·확정 재고에 원자적으로 적용한다.
- 고객 링크의 원문 token은 DB나 브라우저 저장소에 남기지 않고 해시만 저장한다. fragment를 서버 요청 전에 제거한 뒤 `HttpOnly; SameSite=Strict` 세션으로 교환하며 고객 화면에는 예약 번호 끝 8자리와 변경 일정·상품·차액만 표시한다.
- outbox lease, 멱등 키, 예약 revision, 승인 금액·방향, 원결제 환불 가능 잔액을 서버에서 다시 확인한다. 결과가 불명확하면 자동 성공 처리하지 않고 `RECONCILIATION_REQUIRED`에서 본사 조정 작업을 제공한다.
- 관리자 예약 상세에는 요청 생성·재견적·취소·결제 링크·환불·조정 작업과 상태 이력을, 본사 화면에는 승인 대기열을 추가했다. 테스트 환경의 fake gateway만 연결했다.

## 상태·금액 정책

- 주요 상태는 `PENDING_APPROVAL → APPROVED → AWAITING_PAYMENT/REFUND_PENDING → READY_TO_APPLY → APPLYING → COMPLETED`이며 반려·취소·만료는 각각 terminal 상태로 끝난다.
- 승인 후 차액의 방향이 바뀌거나 승인 절대 금액을 넘는 재견적은 기존 승인을 무효화한다. 정산 중인 예약에는 취소·인원·객실 배정·기존 직접 숙박 변경을 포함한 충돌 mutation을 막는다.
- `CHARGE`는 결제 성공, `REFUND`는 원결제 환불 성공, `NONE`은 hold 성공 뒤에만 적용 준비 상태가 된다. 가격과 재고, 결제·환불 결과, 최종 예약 적용의 권한은 Spring Boot에 있다.
- 적용 트랜잭션은 기존 확정 재고와 대상 hold를 잠그고 차이만 이전한다. 예약 일정·객실 유형·요금제·총액·일별 요금·operation revision과 V33 숙박 변경 감사를 함께 갱신한다.

## 검증 결과

- PostgreSQL 통합 회귀 13개 클래스, 80건이 통과했다. 승인·재견적·변경 mutation 차단, hold 경쟁·만료, 추가 결제·부분 환불·outbox lease·멱등성·조정 상태, 정산 성공 뒤 원자 적용과 기존 예약·취소·직원 운영 회귀를 포함한다.
- 관리자 TypeScript 검사와 production build가 통과했고, 예약 관리 Chromium 회귀 10건이 통과했다. 승인 한도·본사 승인/반려·응답 유실 재시도·고객 링크·기존 예약 상세 작업을 확인했다.
- 고객 결제 세션·경로 직접 테스트와 TypeScript·Vite production build가 통과했다. 고객 Chromium 2건에서 fragment 1회 교환, 불필요한 호텔/재고 요청 부재, 최소 정보 표시, 새로고침 세션, 390×844 가로 넘침과 키보드 결제 진입을 확인했다.
- 기존 Compose API 4080을 현재 코드로 재빌드했다. Flyway가 개발 DB를 V33에서 V35로 올렸고 readiness `UP`을 확인했다. 관리자 4001은 기존 프로세스를 유지했으며 실제 예약 관리 화면에서 오류 없는 승인 대기 0건과 예약 목록을 읽기 전용으로 확인했다.
- 실제 개발 예약의 변경 요청·승인·결제·환불 mutation은 실행하지 않았다.

## 실제 PG 전 남은 작업

- 운영 결제 provider 선택과 계약, 결제·환불 수수료 및 회계 계정 매핑은 정하지 않았고 검증하지 않았다.
- 운영 secret 설정, HTTPS origin, provider가 서명한 production webhook의 검증·재전송·순서 역전 처리는 연결하지 않았다.
- 실제 provider sandbox의 추가 결제·부분 환불·실패·timeout·불명확 결과 transaction을 실행하지 않았다.
- provider 정산 보고서와 내부 `payment_transaction`·`payment_adjustment_attempt`의 수수료 포함 회계 reconciliation은 검증하지 않았다.
- 고객 링크의 이메일·문자 등 자동 전달과 만료·완료 안내는 구현하거나 검증하지 않았다. 현재 직원이 링크를 복사해 전달하는 범위다.

## 배포·롤백

- V34·V35는 기존 예약·결제 테이블을 파괴하지 않는 additive migration이다. rolling 배포에서는 새 API schema 적용 뒤 관리자·고객 앱을 배포한다.
- `RESERVATION_CHANGE_SETTLEMENT_ENABLED=false`로 새 정산 흐름을 비활성화할 수 있다. 실제 gateway가 준비되기 전에는 `RESERVATION_CHANGE_GATEWAY=disabled`를 사용하며 로컬 Compose만 명시적으로 `fake`를 기본값으로 둔다.
- migration을 내리기 위해 테이블을 삭제하지 않는다. 기능 flag를 먼저 끄고 미완료 요청·hold·outbox를 보존한 채 원인을 조정한다. 이미 `COMPLETED`인 변경은 예약과 재고가 반영된 업무 기록이므로 자동 역마이그레이션하지 않고 감사 이력에 근거한 별도 보정 절차가 필요하다.
