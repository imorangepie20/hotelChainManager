import { test } from 'node:test'
import assert from 'node:assert/strict'
import { requestTossCheckout } from '../src/lib/toss-payments.ts'

const checkout = { orderId: 'hotel_test_order', amountKrw: 120000, currency: 'KRW', clientKey: 'test_gck_fixture', successUrl: 'http://localhost:4000/reservations/id/payment-result?result=success', failUrl: 'http://localhost:4000/reservations/id/payment-result?result=fail', environmentLabel: '테스트' }

function fixture(fail = false) {
  const calls: unknown[] = []
  const handlers: Record<string, () => void> = {}
  let ready!: () => void
  const rendered = new Promise<void>(resolve => { ready = resolve })
  const sdk = Object.assign((key: string) => ({
    widgets: (options: unknown) => {
      calls.push(['widgets', key, options])
      return {
        setAmount: async (amount: unknown) => { calls.push(['amount', amount]) },
        renderPaymentWindow: async () => {
          calls.push('render')
          return { on: (name: string, handler: () => void) => { handlers[name] = handler; if (name === 'cancel') ready() }, destroy: async () => { calls.push('destroy') } }
        },
        requestPayment: async (input: unknown) => { calls.push(['request', input]); if (fail) throw { code: 'INVALID_API_KEY', message: 'private' } },
      }
    },
    payment: () => ({ requestPayment: async (input: unknown) => { calls.push(['legacy', input]) } }),
  }), { ANONYMOUS: '@@ANONYMOUS' })
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { location: { origin: 'http://localhost:4000' }, TossPayments: sdk } })
  return { calls, handlers, rendered }
}

test('위젯 금액 설정 후 창을 열고 구매자 요청에서만 서버 주문을 전달한다', async () => {
  const f = fixture()
  const pending = requestTossCheckout(checkout)
  await f.rendered
  assert.deepEqual(f.calls, [['widgets', checkout.clientKey, { customerKey: '@@ANONYMOUS' }], ['amount', { currency: 'KRW', value: 120000 }], 'render'])
  f.handlers.paymentRequest(); f.handlers.paymentRequest()
  await pending
  assert.deepEqual(f.calls.slice(3), [['request', { orderId: checkout.orderId, orderName: '호텔 예약 테스트 결제', successUrl: checkout.successUrl, failUrl: checkout.failUrl }], 'destroy'])
})

test('창 닫기는 결제를 요청하지 않고 해제하며 다시 열 수 있다', async () => {
  const f = fixture()
  const pending = requestTossCheckout(checkout)
  const rejected = assert.rejects(pending, /USER_CANCEL/)
  await f.rendered; f.handlers.cancel(); await rejected
  assert.equal(f.calls.at(-1), 'destroy')
  assert.equal(f.calls.some(c => Array.isArray(c) && c[0] === 'request'), false)
  const next = fixture()
  const retry = requestTossCheckout(checkout)
  await next.rendered; next.handlers.paymentRequest(); await retry
})

test('SDK 요청 실패를 전달하고 창을 해제한다', async () => {
  const f = fixture(true)
  const pending = requestTossCheckout(checkout)
  const rejected = assert.rejects(pending, /PAYMENT_WINDOW \/ INVALID_API_KEY/)
  await f.rendered; f.handlers.paymentRequest(); await rejected
  assert.equal(f.calls.at(-1), 'destroy')
})

test('개별 키는 기존 카드 결제 경로를 유지한다', async () => {
  const f = fixture()
  await requestTossCheckout({ ...checkout, clientKey: 'test_ck_fixture' })
  assert.equal((f.calls[0] as unknown[])[0], 'legacy')
})
