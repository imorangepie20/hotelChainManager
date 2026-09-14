import { useEffect, useRef, useState } from 'react'
import { CalendarDays, Clock3, CreditCard, ShieldCheck } from 'lucide-react'

import { ApiFailure, api, type ReservationChangePayment } from '../lib/api'
import { captureReservationChangePaymentToken } from '../lib/reservation-change-payment-session'
import { requestTossCheckout, type TossReturn, type TossStatus } from '../lib/toss-payments'
import { paymentActions, type PaymentProvider } from '../lib/payment-recovery'

const money = new Intl.NumberFormat('ko-KR')
const date = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
const time = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
})

function messageFor(error: unknown) {
  if (error instanceof ApiFailure) return error.message
  return '결제 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function ReservationChangePaymentPage({ returned }: { returned: TossReturn }) {
  const [token] = useState(() => captureReservationChangePaymentToken(
    window.location,
    path => window.history.replaceState({}, '', path),
  ))
  const [payment, setPayment] = useState<ReservationChangePayment | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [tossState, setTossState] = useState<TossStatus | null>(null)
  const [provider, setProvider] = useState<PaymentProvider | null>(null)
  const alert = useRef<HTMLDivElement>(null)
  const busyRef = useRef(false)
  const initialRequest = useRef<Promise<{ payment: ReservationChangePayment; status: TossStatus | null; provider: PaymentProvider }> | null>(null)
  const returnedFromToss = returned.kind !== 'none'

  useEffect(() => { if (error) alert.current?.focus() }, [error])

  useEffect(() => {
    let active = true
    async function load() {
      try {
        if (!initialRequest.current) initialRequest.current = (async () => {
          const modes = await api.paymentModes()
          if (token) { await api.exchangeReservationChangePaymentToken(token); sessionStorage.removeItem('tossChangeStarted') }
          if (returnedFromToss) sessionStorage.setItem('tossChangeStarted', '1')
          const status = modes.changeProvider !== 'toss-test' ? null : returned.kind === 'success' ? await api.tossChangeConfirm(returned.input)
            : returnedFromToss || sessionStorage.getItem('tossChangeStarted') ? await api.tossChangeStatus() : null
          return { payment: await api.reservationChangePayment(), status, provider: modes.changeProvider }
        })()
        const current = await initialRequest.current
        if (active) { setPayment(current.payment); setTossState(current.status); setProvider(current.provider) }
      } catch (cause) {
        if (active) setError(returnedFromToss ? '결제 세션 또는 결과를 확인하지 못했습니다. 원래 결제 링크를 다시 열어 인증해 주세요. 중복 결제하지 마세요.' : messageFor(cause))
      } finally {
        if (active) setLoading(false)
      }
    }
    void load()
    return () => { active = false }
  }, [token, returned, returnedFromToss])

  async function checkout() {
    if (provider !== 'fake' || busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setError('')
    try {
      const result = await api.reservationChangeCheckout()
      window.location.assign(result.checkoutUrl)
    } catch (cause) {
      setError(messageFor(cause))
      setBusy(false)
      busyRef.current = false
    }
  }

  async function checkoutToss() {
    if (provider !== 'toss-test' || busyRef.current) return
    busyRef.current = true; setBusy(true); setError('')
    try {
      const checkout = await api.tossChangeCheckout()
      sessionStorage.setItem('tossChangeStarted', '1')
      await requestTossCheckout(checkout)
    } catch { setError('토스 테스트 결제창을 열지 못했습니다. 결제 설정과 링크 상태를 확인해 주세요.') }
    finally { busyRef.current = false; setBusy(false) }
  }

  async function refreshToss() {
    if (busyRef.current) return
    busyRef.current = true; setBusy(true); setError('')
    try { setTossState(await api.tossChangeStatus()); setPayment(await api.reservationChangePayment()) }
    catch { setError('결제 세션 또는 결과를 확인하지 못했습니다. 원래 결제 링크를 다시 열어 인증해 주세요.') }
    finally { busyRef.current = false; setBusy(false) }
  }

  const canPay = !returnedFromToss && (!tossState || tossState.paymentStatus === 'NEW') && payment?.status === 'AWAITING_PAYMENT'
  const actions = paymentActions(provider)
  const completed = payment?.status === 'READY_TO_APPLY' || payment?.status === 'APPLYING'
    || payment?.status === 'COMPLETED'

  return (
    <main className="change-payment-shell">
      <section className="change-payment-card" aria-labelledby="change-payment-title">
        <header className="change-payment-header">
          <span className="change-payment-mark" aria-hidden="true"><ShieldCheck size={24} /></span>
          <div>
            <p>예약 변경 추가 결제</p>
            <h1 id="change-payment-title">변경 내용을 확인해 주세요</h1>
          </div>
        </header>

        {loading && <div className="change-payment-state" role="status">안전한 결제 정보를 불러오는 중입니다.</div>}
        {error && <div className="change-payment-state is-error" role="alert" ref={alert} tabIndex={-1}>{error}</div>}
        {returned.kind === 'fail' && <p role="status">결제창에서 결제가 완료되지 않았습니다. 서버 상태를 확인해 주세요.</p>}
        {(returnedFromToss || tossState) && <><p role="status">{tossState?.paymentStatus === 'SUCCEEDED' ? '추가 결제가 확인되었습니다. 최종 예약 변경 상태는 아래에서 확인해 주세요.' : '결제 결과를 확인 중입니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.'}</p><button className="change-payment-action" onClick={refreshToss} disabled={busy || loading}>서버 상태 다시 확인</button></>}

        {!loading && payment && (
          <>
            <div className={`change-payment-status ${completed ? 'is-complete' : ''}`} role="status">
              <strong>{completed ? payment.status === 'COMPLETED' ? '예약 변경이 완료되었습니다' : '결제가 확인되었습니다' : payment.status === 'AWAITING_PAYMENT' ? '결제 대기 중' : '예약 변경 상태 확인 필요'}</strong>
              <span>{completed ? payment.status === 'COMPLETED' ? '최종 변경 내용을 확인해 주세요.' : '예약 변경을 최종 반영하고 있습니다.' : payment.environmentLabel}</span>
            </div>

            <dl className="change-payment-details">
              <div>
                <dt>예약 번호</dt>
                <dd>•••• {payment.reservationNumberSuffix}</dd>
              </div>
              <div>
                <dt><CalendarDays size={17} aria-hidden="true" /> 변경 일정</dt>
                <dd>{date.format(new Date(`${payment.checkIn}T00:00:00`))} – {date.format(new Date(`${payment.checkOut}T00:00:00`))}</dd>
              </div>
              <div>
                <dt>객실 · 요금</dt>
                <dd>{payment.roomTypeName}<span>{payment.ratePlanName}</span></dd>
              </div>
              <div>
                <dt><Clock3 size={17} aria-hidden="true" /> 결제 링크 만료</dt>
                <dd>{time.format(new Date(payment.expiresAt))}</dd>
              </div>
            </dl>

            <div className="change-payment-total">
              <span>추가 결제 금액</span>
              <strong>{money.format(payment.additionalAmountKrw)}원</strong>
            </div>

            {canPay && (
              <>{actions.toss && <button className="change-payment-action" type="button" onClick={checkoutToss} disabled={busy}>토스 테스트 결제</button>}
              {actions.fake && <button className="change-payment-action" type="button" onClick={checkout} disabled={busy}>
                <CreditCard size={19} aria-hidden="true" />
                {busy ? '결제 화면을 준비하는 중…' : `${money.format(payment.additionalAmountKrw)}원 결제하기`}
              </button>}</>
            )}
            <p className="change-payment-note">결제 금액과 예약 변경 확정은 호텔 서버에서 검증합니다. 카드 정보는 이 화면에 저장되지 않습니다.</p>
          </>
        )}
      </section>
    </main>
  )
}
