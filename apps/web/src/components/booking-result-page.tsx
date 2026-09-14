import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, Clock3 } from 'lucide-react'

import { api, type Reservation } from '../lib/api'
import { BookingSessionStore, type ReservationAccess } from '../lib/booking-session'
import { paymentMessage, type TossReturn, type TossStatus } from '../lib/toss-payments'
import { shouldPollPaymentStatus } from '../lib/booking-checkout-state'
import { CustomerBookingShell } from './customer-shell'

const money = new Intl.NumberFormat('ko-KR')
type Props = { locale?: 'ko' | 'en'; reservationId?: string; returned: TossReturn }

function messageForResult(returned: TossReturn) {
  if (returned.kind === 'fail') return '결제창에서 결제를 완료하지 않았습니다. 같은 객실 확보 건에서 다시 시도할 수 있습니다.'
  if (returned.kind === 'invalid') return '결제 복귀 정보를 확인할 수 없습니다. 중복 결제하지 말고 서버 상태를 확인해 주세요.'
  return ''
}

export function BookingResultPage({ locale = 'ko', reservationId, returned }: Props) {
  const session = useRef(new BookingSessionStore())
  const [access] = useState<ReservationAccess | null>(() => {
    if (reservationId) return session.current.listReservationAccess().find(item => item.reservationId === reservationId) ?? null
    const selection = session.current.loadSelection()
    const progress = selection ? session.current.loadCheckoutProgress(selection) : null
    return progress ?? null
  })
  const [reservation, setReservation] = useState<Reservation | null>(null)
  const [payment, setPayment] = useState<TossStatus | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState(() => messageForResult(returned))
  const confirmationHandled = useRef(false)
  const pollAttempts = useRef(0)

  async function checkServerState(reconcile = false) {
    if (!access) return
    setBusy(true)
    try {
      const next = returned.kind === 'success' && !confirmationHandled.current
        ? await api.tossConfirm(access.reservationId, access.managementToken, returned.input)
        : reconcile ? await api.tossReconcile(access.reservationId, access.managementToken)
          : await api.tossStatus(access.reservationId, access.managementToken)
      confirmationHandled.current = true
      setPayment(next)
      setReservation(await api.getReservation(access.reservationId, access.managementToken))
      if (next.status === 'CONFIRMED') setError('')
    } catch {
      setError('결제 결과를 아직 확인하지 못했습니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.')
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => { void checkServerState(); }, [])

  useEffect(() => {
    if (!payment || !shouldPollPaymentStatus(payment) || pollAttempts.current >= 5) return
    const timeout = window.setTimeout(() => {
      pollAttempts.current += 1
      void checkServerState(true)
    }, 3_000)
    return () => window.clearTimeout(timeout)
  }, [payment])

  const confirmed = payment?.status === 'CONFIRMED' && payment.paymentStatus === 'SUCCEEDED'
  const statusText = payment ? paymentMessage(payment).text : '서버에서 결제 결과를 확인하고 있습니다.'
  const detailPath = `${locale === 'en' ? '/en' : ''}/reservations/${access?.reservationId ?? ''}`

  if (!access) return <CustomerBookingShell step="complete" locale={locale}><section className="booking-route-state"><h1>확인할 결제 정보가 없습니다</h1><p>예약을 진행한 같은 브라우저에서 예약 상태를 다시 확인해 주세요.</p><a className="primary" href={locale === 'en' ? '/en' : '/'}>홈으로</a></section></CustomerBookingShell>

  return <CustomerBookingShell step="complete" locale={locale}>
    <section className="booking-result-page" aria-labelledby="booking-result-title">
      {confirmed ? <CheckCircle2 className="booking-result-icon" size={42} aria-hidden="true" /> : <Clock3 className="booking-result-icon" size={42} aria-hidden="true" />}
      <p className="section-kicker">TEST PAYMENT RESULT</p>
      <h1 id="booking-result-title">{confirmed ? '예약이 확정되었습니다' : '결제 결과를 확인하고 있습니다'}</h1>
      <p className="booking-result-lead">실제 과금 없는 테스트 결제입니다.</p>
      {busy && <p className="booking-result-status" role="status">서버에서 결제 결과를 확인하고 있습니다…</p>}
      {error && <p className="booking-checkout-error" role="alert">{error}</p>}
      {!busy && payment && <p className={`booking-result-status${confirmed ? ' is-confirmed' : ''}`} role="status">{statusText}</p>}
      {confirmed && reservation && <dl className="booking-result-details"><div><dt>예약 번호</dt><dd>{reservation.id}</dd></div><div><dt>숙박 일정</dt><dd>{reservation.checkIn} — {reservation.checkOut}</dd></div><div><dt>객실 수</dt><dd>{reservation.rooms}개</dd></div><div><dt>결제 금액</dt><dd>₩{money.format(reservation.total)}</dd></div></dl>}
      {confirmed ? <div className="booking-result-actions"><a className="primary" href={detailPath}>예약 상세 보기</a><a className="outline" href={locale === 'en' ? '/en' : '/'}>홈으로</a></div> : <div className="booking-result-actions"><button className="primary" type="button" onClick={() => void checkServerState(true)} disabled={busy}>서버 상태 다시 확인</button><a className="outline" href={locale === 'en' ? '/en/booking/checkout' : '/booking/checkout'}>예약 화면으로 돌아가기</a></div>}
    </section>
  </CustomerBookingShell>
}
