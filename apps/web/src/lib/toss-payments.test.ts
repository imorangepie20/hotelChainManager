import { captureTossReturn, paymentMessage } from './toss-payments.ts'

function equal(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${message}: expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`)
}

let replaced = ''
const returned = captureTossReturn({
  pathname: '/booking/complete',
  search: '?paymentKey=payment_123&orderId=order_123&amount=240000',
  hash: '',
}, path => { replaced = path })

equal(returned, { kind: 'success', input: { paymentKey: 'payment_123', orderId: 'order_123', amountKrw: 240000 } }, '정상 결제 복귀 값은 메모리에서만 확인해야 한다')
equal(replaced, '/booking/complete', '결제 복귀 query는 서버 상태 조회 전에 URL에서 제거해야 한다')

const invalid = captureTossReturn({
  pathname: '/booking/complete',
  search: '?paymentKey=payment_123&paymentKey=payment_456&orderId=order_123&amount=240000',
  hash: '',
}, () => undefined)
equal(invalid, { kind: 'invalid' }, '중복된 결제 복귀 값은 승인에 전달하지 않아야 한다')
equal(paymentMessage({ status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' }), { completed: true, text: '결제가 확인되어 예약이 확정되었습니다.' }, '확정은 완료 메시지로 표시해야 한다')
equal(paymentMessage({ status: 'PENDING_PAYMENT', paymentStatus: 'FAILED' }).completed, false, '실패한 결제는 다시 시도 가능한 상태로 남아야 한다')

console.log('toss payment return contracts passed')
