import { checkoutStateFromReservation, formatHoldRemaining, isRetryablePaymentState, shouldPollPaymentStatus } from './booking-checkout-state.ts'

function equal(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, received ${String(actual)}`)
}

const future = '2026-09-22T10:05:00.000Z'
const past = '2026-09-22T09:55:00.000Z'
const now = new Date('2026-09-22T10:00:00.000Z')

equal(checkoutStateFromReservation('PENDING_PAYMENT', future, now), 'PAYMENT_READY', '확보된 예약은 결제를 다시 시도할 수 있어야 한다')
equal(checkoutStateFromReservation('PENDING_PAYMENT', past, now), 'EXPIRED', '서버 만료 시각이 지난 확보는 결제할 수 없어야 한다')
equal(checkoutStateFromReservation('CONFIRMED', future, now), 'CONFIRMED', '확정 예약은 완료 화면으로 전달해야 한다')
equal(checkoutStateFromReservation('EXPIRED', future, now), 'EXPIRED', '서버가 만료로 표시한 예약은 즉시 만료 상태여야 한다')
equal(checkoutStateFromReservation('UNEXPECTED', future, now), 'ERROR', '알 수 없는 서버 상태는 결제를 계속하지 않아야 한다')
equal(formatHoldRemaining(future, now), '05:00', '확보 남은 시간은 서버 만료 시각으로 계산해야 한다')
equal(formatHoldRemaining(past, now), '00:00', '만료된 확보 시간은 음수로 표시하지 않아야 한다')
equal(isRetryablePaymentState('PAYMENT_READY'), true, '결제창을 닫은 확보는 다시 시도할 수 있어야 한다')
equal(isRetryablePaymentState('EXPIRED'), false, '만료된 확보는 다시 시도할 수 없어야 한다')
equal(shouldPollPaymentStatus({ status: 'PENDING_PAYMENT', paymentStatus: 'UNKNOWN' }), true, '알 수 없는 승인 결과는 서버 상태를 다시 확인해야 한다')
equal(shouldPollPaymentStatus({ status: 'PENDING_PAYMENT', paymentStatus: 'APPROVING' }), true, '승인 처리 중인 결제는 서버 상태를 다시 확인해야 한다')
equal(shouldPollPaymentStatus({ status: 'PENDING_PAYMENT', paymentStatus: 'FAILED' }), false, '실패한 결제는 자동 조회를 계속하지 않아야 한다')
equal(shouldPollPaymentStatus({ status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' }), false, '확정된 결제는 자동 조회를 멈춰야 한다')

console.log('booking checkout state contracts passed')
