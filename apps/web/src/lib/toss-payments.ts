export type TossCheckout = {
  orderId: string
  amountKrw: number
  currency: string
  clientKey: string
  successUrl: string
  failUrl: string
  environmentLabel: string
}

export type ConfirmationInput = { paymentKey: string; orderId: string; amountKrw: number }
export type TossStatus = { reservationId: string; orderId: string | null; status: string; paymentStatus: string }
export type TossReturn = { kind: 'success'; input: ConfirmationInput } | { kind: 'fail' | 'invalid' | 'none' }

export class TossCheckoutFailure extends Error {}

type CheckoutStage = 'CONFIG' | 'SDK' | 'PAYMENT_WINDOW'

function checkoutFailureMessage(stage: CheckoutStage, reason: unknown) {
  const code = reason && typeof reason === 'object' && 'code' in reason ? reason.code : undefined
  if (code === 'USER_CANCEL') return '결제창을 닫았습니다. 확보 시간 안에 다시 시도해 주세요.'
  if (code === 'NOT_SUPPORTED_WIDGET_KEY' || code === 'INVALID_CLIENT_KEY') {
    return '토스 테스트 키와 결제창 방식을 확인해 주세요.'
  }
  if (stage === 'CONFIG') return '결제 설정을 확인할 수 없습니다. 서버 상태를 다시 확인해 주세요.'
  if (stage === 'SDK') return '결제창 프로그램을 불러오지 못했습니다. 네트워크 연결을 확인해 주세요.'
  return '결제창을 열지 못했습니다. 확보 시간 안에 다시 시도하거나 서버 상태를 확인해 주세요.'
}

export function captureTossReturn(location: Pick<Location, 'pathname' | 'search' | 'hash'>, replace: (path: string) => void): TossReturn {
  const params = new URLSearchParams(location.search)
  replace(location.pathname + location.hash)
  if (!params.size) return { kind: 'none' }
  if (params.get('result') === 'fail') return { kind: 'fail' }
  if (params.has('code') || params.has('errorCode')) return { kind: 'fail' }
  try {
    return { kind: 'success', input: toConfirmationInput(params) }
  } catch {
    return { kind: 'invalid' }
  }
}

export function paymentMessage(state: Pick<TossStatus, 'status' | 'paymentStatus'>) {
  if (state.status === 'CONFIRMED' && state.paymentStatus === 'SUCCEEDED') return { completed: true, text: '결제가 확인되어 예약이 확정되었습니다.' }
  if (state.status === 'EXPIRED') return { completed: false, text: '객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.' }
  if (state.paymentStatus === 'FAILED') return { completed: false, text: '결제가 완료되지 않았습니다. 확보 시간 안에 다시 시도하거나 서버 상태를 확인해 주세요.' }
  return { completed: false, text: '결제 결과를 확인하고 있습니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.' }
}

export async function requestTossCheckout(checkout: TossCheckout, locale: 'ko' | 'en' = 'ko'): Promise<void> {
  let stage: CheckoutStage = 'CONFIG'
  try {
    validateTossCheckout(checkout, window.location.origin)
    stage = 'SDK'
    const sdk = await loadTossSdk()
    stage = 'PAYMENT_WINDOW'
    if (checkout.clientKey.startsWith('test_gck_')) {
      const widgets = sdk(checkout.clientKey).widgets({ customerKey: sdk.ANONYMOUS })
      await widgets.setAmount({ currency: 'KRW', value: checkout.amountKrw })
      const paymentWindow = await widgets.renderPaymentWindow()
      try {
        await new Promise<void>((resolve, reject) => {
          let requested = false
          let cancelled = false
          paymentWindow.on('paymentRequest', () => {
            if (requested || cancelled) return
            requested = true
            void widgets.requestPayment({
              orderId: checkout.orderId,
              orderName: locale === 'en' ? 'Hotel reservation test payment' : '호텔 예약 테스트 결제',
              successUrl: checkout.successUrl,
              failUrl: checkout.failUrl,
            }).then(resolve, reject)
          })
          paymentWindow.on('cancel', () => {
            if (requested || cancelled) return
            cancelled = true
            reject({ code: 'USER_CANCEL' })
          })
        })
      } finally {
        await paymentWindow.destroy()
      }
      return
    }
    await sdk(checkout.clientKey).payment({ customerKey: sdk.ANONYMOUS }).requestPayment({
      method: 'CARD', amount: { currency: 'KRW', value: checkout.amountKrw }, orderId: checkout.orderId,
      orderName: locale === 'en' ? 'Hotel reservation test payment' : '호텔 예약 테스트 결제', successUrl: checkout.successUrl, failUrl: checkout.failUrl,
    })
  } catch (reason) {
    if (reason instanceof TossCheckoutFailure) throw reason
    throw new TossCheckoutFailure(locale === 'en' ? 'The payment window did not complete. Retry within the hold time or check the server status.' : checkoutFailureMessage(stage, reason))
  }
}

export function toConfirmationInput(params: URLSearchParams): ConfirmationInput {
  const paymentKey = params.get('paymentKey') ?? ''
  const orderId = params.get('orderId') ?? ''
  const amount = params.get('amount') ?? ''
  if (['paymentKey', 'orderId', 'amount'].some(key => params.getAll(key).length !== 1)
    || !/^[A-Za-z0-9_-]{1,160}$/.test(paymentKey)
    || !/^[A-Za-z0-9_-]{1,160}$/.test(orderId)
    || !/^[1-9][0-9]*$/.test(amount)
    || !Number.isSafeInteger(Number(amount))) throw new Error('invalid payment return')
  return { paymentKey, orderId, amountKrw: Number(amount) }
}

export function validateTossCheckout(checkout: TossCheckout, origin: string) {
  if (!checkout.clientKey.startsWith('test_') || !Number.isSafeInteger(checkout.amountKrw) || checkout.amountKrw <= 0
    || checkout.currency !== 'KRW' || !/^[A-Za-z0-9_-]{1,160}$/.test(checkout.orderId)) {
    throw new TossCheckoutFailure('결제 설정을 확인할 수 없습니다. 서버 상태를 다시 확인해 주세요.')
  }
  for (const value of [checkout.successUrl, checkout.failUrl]) {
    const url = new URL(value)
    const bookingComplete = /^\/(?:en\/)?booking\/complete$/.test(url.pathname) && url.search === ''
    const reservationResult = /^\/reservations\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/payment-result$/.test(url.pathname)
      && /^\?result=(success|fail)$/.test(url.search)
    if (url.origin !== origin || url.username || url.password || url.hash || (!bookingComplete && !reservationResult)) {
      throw new TossCheckoutFailure('안전한 결제 복귀 주소를 확인할 수 없습니다. 서버 상태를 다시 확인해 주세요.')
    }
  }
}

type PaymentRequest = {
  method: 'CARD'
  amount: { currency: 'KRW'; value: number }
  orderId: string
  orderName: string
  successUrl: string
  failUrl: string
}
type WidgetRequest = Pick<PaymentRequest, 'orderId' | 'orderName' | 'successUrl' | 'failUrl'>
type PaymentWindow = {
  on: (event: 'paymentRequest' | 'cancel', callback: () => void) => void
  destroy: () => Promise<void>
}
type TossFactory = ((clientKey: string) => {
  payment: (options: { customerKey: string }) => { requestPayment: (request: PaymentRequest) => Promise<void> }
  widgets: (options: { customerKey: string }) => {
    setAmount: (amount: PaymentRequest['amount']) => Promise<void>
    renderPaymentWindow: () => Promise<PaymentWindow>
    requestPayment: (request: WidgetRequest) => Promise<void>
  }
}) & { ANONYMOUS: string }

declare global { interface Window { TossPayments?: TossFactory } }

let sdkPromise: Promise<TossFactory> | null = null

function loadTossSdk(): Promise<TossFactory> {
  if (window.TossPayments) return Promise.resolve(window.TossPayments)
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://js.tosspayments.com/v2/standard'
    script.async = true
    script.referrerPolicy = 'no-referrer'
    const timeout = window.setTimeout(() => reject(new Error('timeout')), 15_000)
    script.onload = () => { clearTimeout(timeout); window.TossPayments ? resolve(window.TossPayments) : reject(new Error('unavailable')) }
    script.onerror = () => { clearTimeout(timeout); reject(new Error('load failure')) }
    document.head.appendChild(script)
  })
  return sdkPromise
}

export function localizedTossCheckout(checkout: TossCheckout, locale: 'ko' | 'en'): TossCheckout {
  // Validate the server origin and return route before preserving the selected UI locale.
  validateTossCheckout(checkout, new URL(checkout.successUrl).origin)
  const localize = (value: string) => { const url = new URL(value); if (/^\/(?:en\/)?booking\/complete$/.test(url.pathname)) url.pathname = `${locale === 'en' ? '/en' : ''}/booking/complete`; return url.toString() }
  return { ...checkout, successUrl: localize(checkout.successUrl), failUrl: localize(checkout.failUrl) }
}
