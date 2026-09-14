import { captureTossReturn, paymentMessage, localizedTossCheckout, validateTossCheckout } from './toss-payments.ts'

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

let resultPath = ''
const existingRoute = captureTossReturn({
  pathname: '/reservations/123e4567-e89b-12d3-a456-426614174000/payment-result',
  search: '?result=fail', hash: '',
}, path => { resultPath = path })
equal(existingRoute, { kind: 'fail' }, '기존 예약별 결제 복귀 경로도 실패 결과를 처리해야 한다')
equal(resultPath, '/reservations/123e4567-e89b-12d3-a456-426614174000/payment-result', '기존 결제 복귀 query도 URL에서 제거해야 한다')

const invalid = captureTossReturn({
  pathname: '/booking/complete',
  search: '?paymentKey=payment_123&paymentKey=payment_456&orderId=order_123&amount=240000',
  hash: '',
}, () => undefined)
equal(invalid, { kind: 'invalid' }, '중복된 결제 복귀 값은 승인에 전달하지 않아야 한다')
equal(paymentMessage({ status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' }), { completed: true, text: '결제가 확인되어 예약이 확정되었습니다.' }, '확정은 완료 메시지로 표시해야 한다')
equal(paymentMessage({ status: 'PENDING_PAYMENT', paymentStatus: 'FAILED' }).completed, false, '실패한 결제는 다시 시도 가능한 상태로 남아야 한다')
equal(paymentMessage({ status: 'PENDING_PAYMENT', paymentStatus: 'UNKNOWN' }).completed, false, '미확정 결제는 중복 승인 없이 상태 재확인으로 남아야 한다')
equal(paymentMessage({ status: 'EXPIRED', paymentStatus: 'FAILED' }).text, '객실 확보 시간이 만료되어 예약이 확정되지 않았습니다. 객실을 다시 검색해 주세요.', '만료된 결제 실패는 재검색으로 안내해야 한다')

console.log('toss payment return contracts passed')

const changeCheckout = { orderId: 'change_1', amountKrw: 120000, currency: 'KRW', clientKey: 'test_gck_fixture', environmentLabel: '테스트', successUrl: 'http://localhost:4000/reservation-change-payment?result=success', failUrl: 'http://localhost:4000/reservation-change-payment?result=fail' }
const englishCheckout = localizedTossCheckout(changeCheckout, 'en')
equal(englishCheckout.successUrl, 'http://localhost:4000/en/reservation-change-payment?result=success', '실제 추가 결제 복귀 경로의 언어를 유지한다')
validateTossCheckout(englishCheckout, 'http://localhost:4000')
let unsafeReturnRejected = false
try { validateTossCheckout({ ...englishCheckout, failUrl: englishCheckout.successUrl }, 'http://localhost:4000') } catch { unsafeReturnRejected = true }
equal(unsafeReturnRejected, true, '성공/실패 복귀 query를 서로 바꾸지 않는다')
