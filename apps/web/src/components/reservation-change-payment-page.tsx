import { useEffect, useState } from 'react'
import { Clock3, CreditCard, ShieldCheck } from 'lucide-react'
import { ApiFailure, api, type ReservationChangePayment } from '../lib/api'
import { captureReservationChangePaymentToken } from '../lib/reservation-change-payment-session'
import { customerPaymentFailureStatus, describeReservationChangePayment, shouldPollReservationChangePayment } from '../lib/reservation-change-payment-state'

type Props = { locale?: 'ko' | 'en' }
const copy = {
  ko: { kicker: '예약 변경 추가 결제', title: '변경 내용을 확인해 주세요', loading: '안전한 결제 정보를 불러오는 중입니다.', failure: '결제 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', number: '예약 번호', before: '기존 예약', after: '변경 예약', expires: '결제 링크 만료', preparing: '결제 화면을 준비하는 중…', pay: '결제하기', note: '결제 금액과 예약 변경 확정은 호텔 서버에서 검증합니다. 카드 정보는 이 화면에 저장되지 않습니다.' },
  en: { kicker: 'RESERVATION CHANGE PAYMENT', title: 'Review your change', loading: 'Loading your secure payment details.', failure: 'We could not load your payment details. Please try again shortly.', number: 'Reservation number', before: 'Current reservation', after: 'Updated reservation', expires: 'Payment link expires', preparing: 'Preparing payment…', pay: 'Pay now', note: 'The hotel server verifies the payment amount and reservation change. Card details are not stored on this page.' },
} as const
function formatMoney(value: number, currency: string, locale: 'ko' | 'en') { return new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'ko-KR', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value) }
function formatPaymentAmount(value: number, currency: string, locale: 'ko' | 'en') { return locale === 'ko' && currency === 'KRW' ? `${new Intl.NumberFormat('ko-KR').format(value)}원` : formatMoney(value, currency, locale) }
function formatDate(value: string, locale: 'ko' | 'en', withTime = false) { return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'ko-KR', { year: 'numeric', month: 'long', day: 'numeric', ...(withTime ? { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Seoul' } : { timeZone: 'UTC' }) }).format(new Date(withTime ? value : `${value}T12:00:00Z`)) }

export function ReservationChangePaymentPage({ locale = 'ko' }: Props) {
  const text = copy[locale]
  const [token] = useState(() => captureReservationChangePaymentToken(window.location, path => window.history.replaceState({}, '', path)))
  const [payment, setPayment] = useState<ReservationChangePayment | null>(null); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [sessionExpired, setSessionExpired] = useState(false)
  useEffect(() => { let active = true; async function load() { try { if (token) await api.exchangeReservationChangePaymentToken(token); const current = await api.reservationChangePayment(); if (active) { setPayment(current); setSessionExpired(false) } } catch (cause) { if (active) { if (cause instanceof ApiFailure && customerPaymentFailureStatus(cause.status)) setSessionExpired(true); else setError(cause instanceof ApiFailure ? cause.message : text.failure) } } finally { if (active) setLoading(false) } } void load(); return () => { active = false } }, [token, text.failure])
  useEffect(() => {
    if (!payment || !shouldPollReservationChangePayment(payment.status)) return
    let active = true; let inFlight = false
    async function refresh() {
      if (inFlight) return
      inFlight = true
      try { const current = await api.reservationChangePayment(); if (active) { setPayment(current); setSessionExpired(false) } }
      catch (cause) { if (active) { if (cause instanceof ApiFailure && customerPaymentFailureStatus(cause.status)) { setPayment(previous => previous ? { ...previous, status: 'EXPIRED' } : previous); setSessionExpired(true); setError('') } else setError(cause instanceof ApiFailure ? cause.message : text.failure) } }
      finally { inFlight = false }
    }
    const timer = window.setInterval(() => void refresh(), 2000)
    const refetchOnReturn = () => { if (document.visibilityState === 'visible') void refresh() }
    window.addEventListener('focus', refetchOnReturn); window.addEventListener('pageshow', refetchOnReturn); document.addEventListener('visibilitychange', refetchOnReturn)
    return () => { active = false; window.clearInterval(timer); window.removeEventListener('focus', refetchOnReturn); window.removeEventListener('pageshow', refetchOnReturn); document.removeEventListener('visibilitychange', refetchOnReturn) }
  }, [payment?.status, text.failure])
  async function checkout() { setBusy(true); setError(''); try { const result = await api.reservationChangeCheckout(); window.location.assign(result.checkoutUrl) } catch (cause) { if (cause instanceof ApiFailure && customerPaymentFailureStatus(cause.status)) { setPayment(previous => previous ? { ...previous, status: 'EXPIRED' } : previous); setSessionExpired(true) } else setError(cause instanceof ApiFailure ? cause.message : text.failure); setBusy(false) } }
  const display = payment ? describeReservationChangePayment(payment) : null
  const statusText = display ? paymentStatusText(display.kind, locale) : ''
  const amountLabel = display ? paymentAmountLabel(display.direction, locale) : ''
  return <main className="change-payment-shell"><section className="change-payment-card" aria-labelledby="change-payment-title"><header className="change-payment-header"><span className="change-payment-mark" aria-hidden="true"><ShieldCheck size={24} /></span><div><p>{text.kicker}</p><h1 id="change-payment-title">{text.title}</h1></div></header>{loading && <div className="change-payment-state" role="status">{text.loading}</div>}{sessionExpired && !payment && <div className="change-payment-state" role="status">{paymentStatusText('EXPIRED', locale)}</div>}{error && <div className="change-payment-state is-error" role="alert">{error}</div>}{!loading && payment && <><div className={`change-payment-status ${display?.kind === 'COMPLETED' ? 'is-complete' : ''}`} role="status"><strong>{statusText}</strong><span>{locale === 'en' && payment.environmentLabel === '테스트 결제' ? 'Test payment' : payment.environmentLabel}</span></div>{display?.kind === 'CANCELLED' && <p className="change-payment-state is-error" role="status">{locale === 'ko' ? '결제가 실패했거나 취소되었습니다. 호텔 직원에게 새 결제 링크를 요청해 주세요.' : 'The payment failed or was cancelled. Please ask hotel staff for a new payment link.'}</p>}<dl className="change-payment-details"><div><dt>{text.number}</dt><dd>•••• {payment.reservationNumberSuffix}</dd></div><div><dt aria-label={text.before}>{text.before}</dt><dd>{formatDate(payment.previousCheckIn, locale)} – {formatDate(payment.previousCheckOut, locale)}<span>{payment.previousRoomTypeName} · {payment.previousRatePlanName}</span><strong>{formatMoney(payment.previousTotalKrw, payment.currency, locale)}</strong></dd></div><div className="is-changed"><dt aria-label={text.after}>{text.after}</dt><dd>{formatDate(payment.checkIn, locale)} – {formatDate(payment.checkOut, locale)}<span>{payment.roomTypeName} · {payment.ratePlanName}</span><strong>{formatMoney(payment.totalKrw, payment.currency, locale)}</strong></dd></div><div><dt><Clock3 size={17} aria-hidden="true" /> {text.expires}</dt><dd>{formatDate(payment.expiresAt, locale, true)}</dd></div></dl><div className="change-payment-total"><span>{amountLabel}</span><strong>{amountLabel} {formatPaymentAmount(Math.abs(payment.differenceKrw), payment.currency, locale)}</strong></div>{display?.canCheckout && <button className="change-payment-action" type="button" onClick={checkout} disabled={busy}><CreditCard size={19} aria-hidden="true" />{busy ? text.preparing : `${formatPaymentAmount(payment.additionalAmountKrw, payment.currency, locale)} ${text.pay}`}</button>}{display?.kind === 'COMPLETED' && <div className="booking-result-actions"><a className="primary" href={`${locale === 'en' ? '/en' : ''}/reservations/${payment.reservationId}`}>{locale === 'ko' ? '예약 상세 보기' : 'View reservation'}</a><a className="outline" href={locale === 'en' ? '/en' : '/'}>{locale === 'ko' ? '홈으로' : 'Home'}</a></div>}<p className="change-payment-note">{text.note}</p></>}</section></main>
}

function paymentStatusText(status: string, locale: 'ko' | 'en') {
  const labels = locale === 'ko'
    ? { AWAITING_PAYMENT: '추가 결제 대기 중', READY_TO_APPLY: '결제 확인 완료', APPLYING: '예약 변경을 반영 중입니다', COMPLETED: '예약 변경이 완료되었습니다', CANCELLED: '결제가 취소되었습니다', EXPIRED: '결제 링크가 만료되었습니다', RECONCILIATION_REQUIRED: '결제 결과를 확인 중입니다', UNKNOWN: '결제 상태를 확인 중입니다' }
    : { AWAITING_PAYMENT: 'Payment pending', READY_TO_APPLY: 'Payment confirmed', APPLYING: 'Applying your reservation change', COMPLETED: 'Reservation change complete', CANCELLED: 'Payment cancelled', EXPIRED: 'Payment link expired', RECONCILIATION_REQUIRED: 'Confirming the payment result', UNKNOWN: 'Checking payment status' }
  return labels[status as keyof typeof labels] ?? labels.UNKNOWN
}

function paymentAmountLabel(direction: 'CHARGE' | 'REFUND' | 'NONE', locale: 'ko' | 'en') {
  const labels = locale === 'ko' ? { CHARGE: '추가 결제', REFUND: '환불 예정', NONE: '금액 차이 없음' } : { CHARGE: 'Additional payment', REFUND: 'Refund pending', NONE: 'No price difference' }
  return labels[direction]
}
