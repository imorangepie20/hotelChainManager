import { changeAction, changeSettlementDestination } from './reservation-change-state'

function equal(actual: unknown, expected: unknown, message: string) { if (actual !== expected) throw new Error(`${message}: ${String(actual)} !== ${String(expected)}`) }
equal(changeAction('AWAITING_PAYMENT'), 'PAY', '추가 결제 대기만 결제 화면으로 보낸다')
equal(changeAction('REFUND_PENDING'), 'POLL', '환불 처리 중에는 예약 상세에서 폴링한다')
equal(changeAction('READY_TO_APPLY'), 'POLL', '적용 대기에는 예약 상세에서 폴링한다')
equal(changeAction('COMPLETED'), 'RETURN', '완료는 예약 상세로 돌려보낸다')

equal(changeSettlementDestination('AWAITING_PAYMENT'), 'PAYMENT', '추가 결제 대기만 결제 화면 목적지로 한다')
equal(changeSettlementDestination('REFUND_PENDING'), 'DETAILS', '환불 처리 중은 예약 상세 목적지로 한다')
equal(changeSettlementDestination('READY_TO_APPLY'), 'DETAILS', '0원 변경 적용 대기는 예약 상세 목적지로 한다')
equal(changeSettlementDestination('COMPLETED'), 'DETAILS', '완료된 변경은 예약 상세 목적지로 한다')

console.log('reservation change state contracts passed')
