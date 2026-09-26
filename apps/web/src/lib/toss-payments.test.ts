import { toConfirmationInput, captureTossReturn, validateTossCheckout, paymentMessage, checkoutFailureMessage } from './toss-payments.ts'

function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`Expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`)
}
function rejects(action: () => unknown) { let rejected = false; try { action() } catch { rejected = true }; equal(rejected, true) }

equal(toConfirmationInput(new URLSearchParams('paymentKey=pk&orderId=o&amount=120000')), { paymentKey: 'pk', orderId: 'o', amountKrw: 120000 })
for (const amount of ['0', '-1', '1.1', '1e3', 'Infinity', '9007199254740992', '']) {
  rejects(() => toConfirmationInput(new URLSearchParams(`paymentKey=pk&orderId=o&amount=${amount}`)))
}
rejects(() => toConfirmationInput(new URLSearchParams('paymentKey=pk&orderId=o&amount=1&amount=2')))
let replaced = ''
const result = captureTossReturn({ pathname: '/reservation-change-payment', search: '?result=success&paymentKey=pk&orderId=o&amount=120000', hash: '' }, path => { replaced = path })
equal(replaced, '/reservation-change-payment')
equal(result, { kind: 'success', input: { paymentKey: 'pk', orderId: 'o', amountKrw: 120000 } })
equal(captureTossReturn({ pathname: '/reservation-change-payment', search: '?result=success&amount=invalid', hash: '' }, path => { replaced = path }), { kind: 'invalid' })
const checkout = { orderId: 'order-1', amountKrw: 120000, currency: 'KRW', clientKey: 'test_ck_fixture', successUrl: 'http://localhost:4000/reservations/id/payment-result?result=success', failUrl: 'http://localhost:4000/reservations/id/payment-result?result=fail', environmentLabel: '테스트' }
validateTossCheckout(checkout, 'http://localhost:4000')
validateTossCheckout({ ...checkout, clientKey: 'live_gck_fixture', successUrl: 'https://hotel.example/reservations/id/payment-result?result=success', failUrl: 'https://hotel.example/reservations/id/payment-result?result=fail' }, 'https://hotel.example')
validateTossCheckout(checkout, 'http://localhost:4000', 'toss-test')
rejects(() => validateTossCheckout({ ...checkout, clientKey: 'live_ck_fixture' }, 'http://localhost:4000', 'toss-test'))
rejects(() => validateTossCheckout({ ...checkout, clientKey: 'test_ck_fixture' }, 'http://localhost:4000', 'toss-live'))
for (const patch of [{ clientKey: 'secret_key_forbidden' }, { amountKrw: 0 }, { currency: 'USD' }, { successUrl: 'https://evil.example/result' }, { failUrl: 'http://localhost:4000/?token=secret' }]) rejects(() => validateTossCheckout({ ...checkout, ...patch }, 'http://localhost:4000'))
equal(paymentMessage({ status: 'CONFIRMED', paymentStatus: 'UNKNOWN' }).completed, false)
equal(paymentMessage({ status: 'PENDING_PAYMENT', paymentStatus: 'SUCCEEDED' }).completed, false)
equal(paymentMessage({ status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' }).completed, true)
console.log('Toss client contracts passed')

equal(checkoutFailureMessage('API', { code: 'HOLD_EXPIRED' }), '[API / HOLD_EXPIRED] 객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.')
equal(checkoutFailureMessage('SDK', new Error('secret_key_private')), '[SDK] 결제창 프로그램을 불러오지 못했습니다. 네트워크 연결과 브라우저 차단 여부를 확인해 주세요.')
equal(checkoutFailureMessage('PAYMENT_WINDOW', { code: 'INVALID_CLIENT_KEY', message: 'secret_key_private' }), '[PAYMENT_WINDOW / INVALID_CLIENT_KEY] 결제창 방식에 맞는 테스트 클라이언트 키인지 확인해 주세요.')
equal(checkoutFailureMessage('API', { code: 'secret_key_private', message: 'token_private' }), '[API] 결제 주문을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.')
console.log('Checkout diagnostic contracts passed')

validateTossCheckout({ ...checkout, clientKey: 'test_gck_fixture' }, 'http://localhost:4000')
equal(paymentMessage({ status: 'EXPIRED', paymentStatus: 'NEW' }), { completed: false, text: '객실 확보 시간이 만료되어 예약이 확정되지 않았습니다. 객실을 다시 검색해 주세요.' })
equal(paymentMessage({ status: 'EXPIRED', paymentStatus: 'UNKNOWN' }).text.includes('다시 검색'), false)
equal(checkoutFailureMessage('CONFIRM', { code: 'HOLD_EXPIRED', status: 409, message: 'private' }), '[CONFIRM / HOLD_EXPIRED] 객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.')
equal(checkoutFailureMessage('STATUS', { code: 'private', status: 404, message: 'private' }), '[STATUS / HTTP 404] 결제 상태를 조회하지 못했습니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.')
equal(checkoutFailureMessage('CONFIRM', new Error('private')), '[CONFIRM] 결제 승인 결과를 확인하지 못했습니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.')
