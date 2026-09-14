import { requestTossCheckout } from './toss-payments.ts'

function equal(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${message}: expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`)
}

const checkout = {
  orderId: 'hotel_widget_order', amountKrw: 120000, currency: 'KRW', clientKey: 'test_gck_fixture',
  successUrl: 'http://localhost:4000/booking/complete', failUrl: 'http://localhost:4000/booking/complete', environmentLabel: '테스트',
}

async function widgetCheckoutContract() {
  const calls: unknown[] = []
  const handlers: Record<string, () => void> = {}
  let rendered!: () => void
  const ready = new Promise<void>(resolve => { rendered = resolve })
  const sdk = Object.assign((key: string) => ({
    widgets: (options: unknown) => ({
      setAmount: async (amount: unknown) => { calls.push(['amount', key, options, amount]) },
      renderPaymentWindow: async () => {
        calls.push('render')
        return { on: (event: string, handler: () => void) => { handlers[event] = handler; if (event === 'cancel') rendered() }, destroy: async () => { calls.push('destroy') } }
      },
      requestPayment: async (request: unknown) => { calls.push(['request', request]) },
    }),
    payment: () => ({ requestPayment: async (request: unknown) => { calls.push(['legacy', request]) } }),
  }), { ANONYMOUS: '@@ANONYMOUS' })
  const previous = (globalThis as { window?: unknown }).window
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { location: { origin: 'http://localhost:4000' }, TossPayments: sdk } })
  try {
    const pending = requestTossCheckout(checkout)
    await ready
    equal(calls, [['amount', 'test_gck_fixture', { customerKey: '@@ANONYMOUS' }, { currency: 'KRW', value: 120000 }], 'render'], '위젯 키는 결제 금액을 설정한 뒤 결제창을 렌더링해야 한다')
    handlers.paymentRequest()
    handlers.paymentRequest()
    await pending
    equal(calls.slice(2), [['request', { orderId: checkout.orderId, orderName: '호텔 예약 테스트 결제', successUrl: checkout.successUrl, failUrl: checkout.failUrl }], 'destroy'], '구매자 요청 한 번만 결제 요청으로 전달하고 창을 해제해야 한다')
  } finally {
    Object.defineProperty(globalThis, 'window', { configurable: true, value: previous })
  }
}

await widgetCheckoutContract()
console.log('toss widget checkout contract passed')
