export type TossCheckout = {
  orderId: string; amountKrw: number; currency: string; clientKey: string
  successUrl: string; failUrl: string; environmentLabel: string
}
export type ConfirmationInput = { paymentKey: string; orderId: string; amountKrw: number }
export type TossStatus = { reservationId: string; orderId: string | null; status: string; paymentStatus: string }
export type TossReturn = { kind: 'success'; input: ConfirmationInput } | { kind: 'fail' | 'invalid' | 'none' }

type CheckoutStage = 'API' | 'CONFIG' | 'SDK' | 'PAYMENT_WINDOW' | 'CONFIRM' | 'STATUS'
export class TossCheckoutFailure extends Error {}
export function checkoutFailureMessage(stage: CheckoutStage, reason: unknown): string {
  const code = reason && typeof reason === 'object' && 'code' in reason ? reason.code : undefined
  const known: Record<string, string> = {
    HOLD_EXPIRED: '객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.',
    INVALID_CLIENT_KEY: '결제창 방식에 맞는 테스트 클라이언트 키인지 확인해 주세요.',
    INVALID_API_KEY: '결제 연동 키 설정을 확인해 주세요.',
    NOT_SUPPORTED_WIDGET_KEY: '결제창과 키 종류가 일치하지 않습니다. 페이지를 새로고침해 주세요.',
    NOT_REGISTERED_PAYMENT_WIDGET: '토스 상점에 결제 UI가 등록되지 않았습니다. 결제 어드민 설정을 확인해 주세요.',
    INVALID_CUSTOMER_KEY: '결제 고객 식별자 설정을 확인해 주세요.',
    USER_CANCEL: '결제창을 닫았습니다. 결제가 완료되지 않았습니다.',
    RESERVATION_STATE_CONFLICT: '현재 예약 상태에서는 결제할 수 없습니다. 예약 상태를 다시 확인해 주세요.',
  }
  if (typeof code === 'string' && Object.hasOwn(known, code)) return `[${stage} / ${code}] ${known[code]}`
  const fallback = {
    API: '결제 주문을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    CONFIG: '결제 금액·테스트 키·복귀 주소 설정을 확인해 주세요.',
    SDK: '결제창 프로그램을 불러오지 못했습니다. 네트워크 연결과 브라우저 차단 여부를 확인해 주세요.',
    PAYMENT_WINDOW: '결제창 호출이 거절되었습니다. 결제창 방식과 테스트 키 설정을 확인해 주세요.',
    CONFIRM: '결제 승인 결과를 확인하지 못했습니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.',
    STATUS: '결제 상태를 조회하지 못했습니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.',
  }
  const status = reason && typeof reason === 'object' && 'status' in reason ? reason.status : undefined
  if ((stage === 'CONFIRM' || stage === 'STATUS') && typeof status === 'number' && Number.isInteger(status) && status >= 400 && status <= 599) {
    return `[${stage} / HTTP ${status}] ${fallback[stage]}`
  }
  return `[${stage}] ${fallback[stage]}`
}

export function toConfirmationInput(params: URLSearchParams): ConfirmationInput {
  const paymentKey = params.get('paymentKey') ?? ''
  const orderId = params.get('orderId') ?? ''
  const amount = params.get('amount') ?? ''
  if (['paymentKey', 'orderId', 'amount'].some(key => params.getAll(key).length !== 1)
    || !/^[A-Za-z0-9_-]{1,160}$/.test(paymentKey) || !/^[A-Za-z0-9_-]{1,160}$/.test(orderId)
    || !/^[1-9][0-9]*$/.test(amount) || !Number.isSafeInteger(Number(amount))) {
    throw new Error('결제 복귀 정보를 확인할 수 없습니다. 서버 상태를 다시 확인해 주세요.')
  }
  return { paymentKey, orderId, amountKrw: Number(amount) }
}

export function captureTossReturn(location: Pick<Location, 'pathname' | 'search' | 'hash'>, replace: (path: string) => void): TossReturn {
  const params = new URLSearchParams(location.search)
  // Query is consumed only in memory, before API or SDK requests.
  replace(location.pathname + (params.has('result') ? '' : location.hash))
  if (!params.size) return { kind: 'none' }
  if (params.has('code') || params.has('errorCode')) return { kind: 'fail' }
  if (params.get('result') === 'fail') return { kind: 'fail' }
  try { return { kind: 'success', input: toConfirmationInput(params) } } catch { return { kind: 'invalid' } }
}

export function validateTossCheckout(checkout: TossCheckout, origin: string) {
  if (!/^(?:test|live)_(?:gck|ck)_.+/.test(checkout.clientKey) || !Number.isSafeInteger(checkout.amountKrw)
    || checkout.amountKrw <= 0 || checkout.currency !== 'KRW' || !/^[A-Za-z0-9_-]{1,160}$/.test(checkout.orderId)) {
    throw new Error('유효한 토스 결제 설정이 아닙니다.')
  }
  for (const [value, result] of [[checkout.successUrl, 'success'], [checkout.failUrl, 'fail']]) {
    const url = new URL(value)
    if (url.origin !== origin || url.username || url.password || url.hash
      || !((/^\/(?:en\/)?(reservation-change-payment|reservations\/[A-Za-z0-9-]+\/payment-result)$/.test(url.pathname) && url.search === `?result=${result}`)
        || (/^\/(?:en\/)?booking\/complete$/.test(url.pathname) && url.search === ''))) throw new Error('안전한 결제 복귀 주소가 아닙니다.')
  }
}

type PaymentRequest = {
  method: 'CARD'; amount: { currency: 'KRW'; value: number }; orderId: string; orderName: string
  successUrl: string; failUrl: string
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
    const timeout = window.setTimeout(() => reject(new Error('결제창 로딩 시간이 초과되었습니다. 새로고침 후 다시 시도해 주세요.')), 15000)
    script.onload = () => { clearTimeout(timeout); window.TossPayments ? resolve(window.TossPayments) : reject(new Error('결제 SDK를 사용할 수 없습니다.')) }
    script.onerror = () => { clearTimeout(timeout); reject(new Error('결제 SDK를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.')) }
    document.head.appendChild(script)
  })
  return sdkPromise
}

export async function requestTossCheckout(checkout: TossCheckout, locale: 'ko' | 'en' = 'ko') {
  let stage: CheckoutStage = 'CONFIG'
  try {
    validateTossCheckout(checkout, window.location.origin)
    stage = 'SDK'
    const sdk = await loadTossSdk()
    stage = 'PAYMENT_WINDOW'
    if (/^(?:test|live)_gck_/.test(checkout.clientKey)) {
      const widgets = sdk(checkout.clientKey).widgets({ customerKey: sdk.ANONYMOUS })
      await widgets.setAmount({ currency: 'KRW', value: checkout.amountKrw })
      const paymentWindow = await widgets.renderPaymentWindow()
      try {
        await new Promise<void>((resolve, reject) => {
          let requested = false
          let closed = false
          paymentWindow.on('paymentRequest', () => {
            if (requested || closed) return
            requested = true
            Promise.resolve().then(() => widgets.requestPayment({
              orderId: checkout.orderId, orderName: checkout.clientKey.startsWith('test_')
                ? (locale === 'en' ? 'Hotel reservation test payment' : '호텔 예약 테스트 결제')
                : (locale === 'en' ? 'Hotel reservation payment' : '호텔 예약 결제'),
              successUrl: checkout.successUrl, failUrl: checkout.failUrl,
            })).then(resolve, reject)
          })
          paymentWindow.on('cancel', () => {
            closed = true
            reject({ code: 'USER_CANCEL' })
          })
        })
      } finally { await paymentWindow.destroy() }
      return
    }
    await sdk(checkout.clientKey).payment({ customerKey: sdk.ANONYMOUS }).requestPayment({
      method: 'CARD', amount: { currency: 'KRW', value: checkout.amountKrw }, orderId: checkout.orderId,
      orderName: checkout.clientKey.startsWith('test_')
        ? (locale === 'en' ? 'Hotel reservation test payment' : '호텔 예약 테스트 결제')
        : (locale === 'en' ? 'Hotel reservation payment' : '호텔 예약 결제'),
      successUrl: checkout.successUrl, failUrl: checkout.failUrl,
    })
  } catch (reason) { throw new TossCheckoutFailure(locale === 'en' ? 'The payment window did not complete. Check the server status before retrying.' : checkoutFailureMessage(stage, reason)) }
}

export function paymentMessage(state: Pick<TossStatus, 'status' | 'paymentStatus'>) {
  if (state.status === 'CONFIRMED' && state.paymentStatus === 'SUCCEEDED') return { completed: true, text: '결제가 확인되어 예약이 확정되었습니다.' }
  if (state.status === 'EXPIRED' && ['NEW', 'NOT_STARTED', 'FAILED'].includes(state.paymentStatus)) return { completed: false, text: '객실 확보 시간이 만료되어 예약이 확정되지 않았습니다. 객실을 다시 검색해 주세요.' }
  if (state.paymentStatus === 'FAILED') return { completed: false, text: '결제가 승인되지 않았습니다. 예약 확보 상태를 확인해 주세요.' }
  return { completed: false, text: '결제 결과를 확인 중입니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.' }
}

export function localizedTossCheckout(checkout: TossCheckout, locale: 'ko' | 'en'): TossCheckout {
  // Validate the server origin and return route before preserving the selected UI locale.
  validateTossCheckout(checkout, new URL(checkout.successUrl).origin)
  const localize = (value: string) => {
    const url = new URL(value)
    // Both allowed server return forms have already been checked above.
    const path = url.pathname.replace(/^\/en(?=\/)/, '')
    url.pathname = `${locale === 'en' ? '/en' : ''}${path}`
    return url.toString()
  }
  return { ...checkout, successUrl: localize(checkout.successUrl), failUrl: localize(checkout.failUrl) }
}
