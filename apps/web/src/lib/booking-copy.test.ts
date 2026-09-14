const assert = { equal: (actual: unknown, expected: unknown) => { if (actual !== expected) throw new Error(`Expected ${expected}, received ${actual}`) }, throws: (run: () => unknown) => { let threw = false; try { run() } catch { threw = true }; if (!threw) throw new Error('Expected rejection') } }
import { bookingText } from './booking-copy.ts'
import { localizedTossCheckout, validateTossCheckout } from './toss-payments.ts'
for (const text of ['예약 가능한 객실', '예약자 이름', '예약 및 결제 진행', '테스트 결제 완료하기', '예약이 확정되었습니다', '예약 상세 보기', '객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.']) {
  assert.equal(/[가-힣]/.test(bookingText('en', text)), false)
  assert.equal(bookingText('ko', text), text)
}
const checkout = { clientKey: 'test_ck_fixture', orderId: 'test-order', amountKrw: 360000, currency: 'KRW', successUrl: 'http://127.0.0.1:4000/booking/complete', failUrl: 'http://127.0.0.1:4000/booking/complete', environmentLabel: 'Test' }
assert.equal(localizedTossCheckout(checkout, 'en').successUrl, 'http://127.0.0.1:4000/en/booking/complete')
assert.equal(localizedTossCheckout(checkout, 'en').failUrl, 'http://127.0.0.1:4000/en/booking/complete')
assert.throws(() => localizedTossCheckout({ ...checkout, failUrl: 'https://attacker.invalid/booking/complete' }, 'en'))

const reservationResult = {
  ...checkout,
  successUrl: 'http://127.0.0.1:4000/reservations/123e4567-e89b-12d3-a456-426614174000/payment-result?result=success',
  failUrl: 'http://127.0.0.1:4000/reservations/123e4567-e89b-12d3-a456-426614174000/payment-result?result=fail',
}
const englishResult = localizedTossCheckout(reservationResult, 'en')
assert.equal(englishResult.successUrl, reservationResult.successUrl.replace('/reservations/', '/en/reservations/'))
assert.equal(englishResult.failUrl, reservationResult.failUrl.replace('/reservations/', '/en/reservations/'))
validateTossCheckout(englishResult, 'http://127.0.0.1:4000')
assert.equal(localizedTossCheckout(englishResult, 'en').successUrl, englishResult.successUrl)
assert.equal(localizedTossCheckout(englishResult, 'ko').failUrl, reservationResult.failUrl)
assert.throws(() => localizedTossCheckout({ ...reservationResult, successUrl: reservationResult.successUrl + '&next=https://attacker.invalid' }, 'en'))
assert.throws(() => localizedTossCheckout({ ...reservationResult, failUrl: 'https://attacker.invalid/reservations/123e4567-e89b-12d3-a456-426614174000/payment-result?result=fail' }, 'en'))
