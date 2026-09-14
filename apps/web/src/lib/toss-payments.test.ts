import { toConfirmationInput, captureTossReturn, validateTossCheckout, paymentMessage } from './toss-payments.ts'

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
for (const patch of [{ clientKey: 'live_ck_forbidden' }, { amountKrw: 0 }, { currency: 'USD' }, { successUrl: 'https://evil.example/result' }, { failUrl: 'http://localhost:4000/?token=secret' }]) rejects(() => validateTossCheckout({ ...checkout, ...patch }, 'http://localhost:4000'))
equal(paymentMessage({ status: 'CONFIRMED', paymentStatus: 'UNKNOWN' }).completed, false)
equal(paymentMessage({ status: 'PENDING_PAYMENT', paymentStatus: 'SUCCEEDED' }).completed, false)
equal(paymentMessage({ status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' }).completed, true)
console.log('Toss client contracts passed')
