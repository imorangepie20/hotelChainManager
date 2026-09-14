export type TossCheckout = {
  orderId: string; amountKrw: number; currency: string; clientKey: string
  successUrl: string; failUrl: string; environmentLabel: string
}
export type ConfirmationInput = { paymentKey: string; orderId: string; amountKrw: number }
export type TossStatus = { reservationId: string; orderId: string | null; status: string; paymentStatus: string }
export type TossReturn = { kind: 'success'; input: ConfirmationInput } | { kind: 'fail' | 'invalid' | 'none' }

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
  if (!params.has('result')) return { kind: 'none' }
  if (params.get('result') === 'fail') return { kind: 'fail' }
  try { return { kind: 'success', input: toConfirmationInput(params) } } catch { return { kind: 'invalid' } }
}

export function validateTossCheckout(checkout: TossCheckout, origin: string) {
  if (!checkout.clientKey.startsWith('test_') || !Number.isSafeInteger(checkout.amountKrw)
    || checkout.amountKrw <= 0 || checkout.currency !== 'KRW' || !/^[A-Za-z0-9_-]{1,160}$/.test(checkout.orderId)) {
    throw new Error('유효한 토스 테스트 결제 설정이 아닙니다.')
  }
  for (const [value, result] of [[checkout.successUrl, 'success'], [checkout.failUrl, 'fail']]) {
    const url = new URL(value)
    if (url.origin !== origin || url.username || url.password || url.hash
      || !/^\/(reservation-change-payment|reservations\/[A-Za-z0-9-]+\/payment-result)$/.test(url.pathname)
      || url.search !== `?result=${result}`) throw new Error('안전한 결제 복귀 주소가 아닙니다.')
  }
}

type PaymentRequest = {
  method: 'CARD'; amount: { currency: 'KRW'; value: number }; orderId: string; orderName: string
  successUrl: string; failUrl: string
}
type TossFactory = ((clientKey: string) => { payment: (options: { customerKey: string }) => { requestPayment: (request: PaymentRequest) => Promise<void> } }) & { ANONYMOUS: string }
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

export async function requestTossCheckout(checkout: TossCheckout) {
  validateTossCheckout(checkout, window.location.origin)
  const sdk = await loadTossSdk()
  await sdk(checkout.clientKey).payment({ customerKey: sdk.ANONYMOUS }).requestPayment({
    method: 'CARD', amount: { currency: 'KRW', value: checkout.amountKrw }, orderId: checkout.orderId,
    orderName: '호텔 예약 테스트 결제', successUrl: checkout.successUrl, failUrl: checkout.failUrl,
  })
}

export function paymentMessage(state: Pick<TossStatus, 'status' | 'paymentStatus'>) {
  if (state.status === 'CONFIRMED' && state.paymentStatus === 'SUCCEEDED') return { completed: true, text: '결제가 확인되어 예약이 확정되었습니다.' }
  if (state.paymentStatus === 'FAILED') return { completed: false, text: '결제가 승인되지 않았습니다. 예약 확보 상태를 확인해 주세요.' }
  return { completed: false, text: '결제 결과를 확인 중입니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.' }
}
