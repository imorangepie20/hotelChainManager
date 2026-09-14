function equal(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, received ${String(actual)}`)
}

import { reservationPaymentPath as endpoint } from './api.ts'

equal(endpoint('r1', 'checkout'), '/api/reservations/r1/payment-checkout', '결제창 생성은 예약 범위 endpoint를 사용해야 한다')
equal(endpoint('r1', 'confirm'), '/api/reservations/r1/payment-confirm', '승인은 예약 범위 endpoint를 사용해야 한다')
equal(endpoint('r1', 'status'), '/api/reservations/r1/payment-status', '상태 조회는 예약 범위 endpoint를 사용해야 한다')
equal(endpoint('r1', 'reconcile'), '/api/reservations/r1/payment-reconcile', '재조회는 새 승인을 만들지 않는 예약 범위 endpoint를 사용해야 한다')
console.log('payment API contract passed')
