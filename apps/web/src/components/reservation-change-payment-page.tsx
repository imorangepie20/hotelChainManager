import { useEffect, useState } from 'react'
import { CalendarDays, Clock3, CreditCard, ShieldCheck } from 'lucide-react'

import { ApiFailure, api, type ReservationChangePayment } from '../lib/api'
import { captureReservationChangePaymentToken } from '../lib/reservation-change-payment-session'

const money = new Intl.NumberFormat('ko-KR')
const date = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
const time = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
})

function messageFor(error: unknown) {
  if (error instanceof ApiFailure) return error.message
  return '결제 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function ReservationChangePaymentPage() {
  const [token] = useState(() => captureReservationChangePaymentToken(
    window.location,
    path => window.history.replaceState({}, '', path),
  ))
  const [payment, setPayment] = useState<ReservationChangePayment | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    async function load() {
      try {
        if (token) await api.exchangeReservationChangePaymentToken(token)
        const current = await api.reservationChangePayment()
        if (active) setPayment(current)
      } catch (cause) {
        if (active) setError(messageFor(cause))
      } finally {
        if (active) setLoading(false)
      }
    }
    void load()
    return () => { active = false }
  }, [token])

  async function checkout() {
    setBusy(true)
    setError('')
    try {
      const result = await api.reservationChangeCheckout()
      window.location.assign(result.checkoutUrl)
    } catch (cause) {
      setError(messageFor(cause))
      setBusy(false)
    }
  }

  const canPay = payment?.status === 'AWAITING_PAYMENT'
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
        {error && <div className="change-payment-state is-error" role="alert">{error}</div>}

        {!loading && payment && (
          <>
            <div className={`change-payment-status ${completed ? 'is-complete' : ''}`} role="status">
              <strong>{completed ? '결제가 확인되었습니다' : '결제 대기 중'}</strong>
              <span>{completed ? '예약 변경을 최종 반영하고 있습니다.' : payment.environmentLabel}</span>
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
              <button className="change-payment-action" type="button" onClick={checkout} disabled={busy}>
                <CreditCard size={19} aria-hidden="true" />
                {busy ? '결제 화면을 준비하는 중…' : `${money.format(payment.additionalAmountKrw)}원 결제하기`}
              </button>
            )}
            <p className="change-payment-note">결제 금액과 예약 변경 확정은 호텔 서버에서 검증합니다. 카드 정보는 이 화면에 저장되지 않습니다.</p>
          </>
        )}
      </section>
    </main>
  )
}
