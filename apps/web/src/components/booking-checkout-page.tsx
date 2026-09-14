import { bookingText } from '../lib/booking-copy'
import { FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { ArrowRight, Clock3, ShieldCheck } from 'lucide-react'

import { ApiFailure, type Offer, type PaymentMode, type Reservation, api, createManagementToken } from '../lib/api'
import { bookingCheckoutErrorMessage } from '../lib/booking-checkout-message'
import { type BookingCriteria, serializeBookingCriteria } from '../lib/booking-query'
import { BookingSessionStore, type BookingSelection } from '../lib/booking-session'
import { checkoutStateFromReservation, formatHoldRemaining, type CheckoutState } from '../lib/booking-checkout-state'
import { requestTossCheckout, localizedTossCheckout, TossCheckoutFailure, checkoutFailureMessage } from '../lib/toss-payments'
import { availabilityRequestFields } from '../lib/latest-availability-request'
import { CustomerBookingShell } from './customer-shell'

const money = new Intl.NumberFormat('ko-KR')
type Props = { locale?: 'ko' | 'en' }

function resultsPath(criteria: BookingCriteria, locale: 'ko' | 'en') {
  return `${locale === 'en' ? '/en' : ''}/booking/results?${serializeBookingCriteria(criteria)}`
}

function messageFor(reason: unknown, locale: 'ko' | 'en'): string {
  return bookingText(locale, bookingCheckoutErrorMessage(reason instanceof ApiFailure ? reason.code : undefined))
}

export function BookingCheckoutPage({ locale = 'ko' }: Props) {
  const session = useRef(new BookingSessionStore())
  const [selection] = useState<BookingSelection | null>(() => session.current.loadSelection())
  const [offer, setOffer] = useState<Offer | null>(null)
  const [reservation, setReservation] = useState<Reservation | null>(null)
  const [state, setState] = useState<CheckoutState>('DETAILS')
  const [guest, setGuest] = useState({ name: '', email: '', phone: '' })
  const [agreed, setAgreed] = useState(false)
  const [loadingOffer, setLoadingOffer] = useState(Boolean(selection))
  const [error, setError] = useState('')
  const paymentBusy = useRef(false)
  const alert = useRef<HTMLDivElement>(null)
  useEffect(() => { if (error) alert.current?.focus() }, [error])
  const [paymentMode, setPaymentMode] = useState<PaymentMode['provider']>('disabled')
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const interval = window.setInterval(() => setNow(new Date()), 1_000)
    return () => window.clearInterval(interval)
  }, [])

  useEffect(() => { api.paymentModes().then(mode => setPaymentMode(mode.provider)).catch(() => setPaymentMode('disabled')) }, [])

  useEffect(() => {
    if (!selection) return
    let active = true
    const access = session.current.loadCheckoutProgress(selection)
    const summary = session.current.loadCheckoutSummary(selection)
    const load = async () => {
      if (access) {
        try {
          const saved = await api.getReservation(access.reservationId, access.managementToken)
          if (!active) return
          if (saved.status === 'CONFIRMED') {
            session.current.clearCheckoutProgress()
            window.location.replace(`${locale === 'en' ? '/en' : ''}/reservations/${saved.id}`)
            return
          }
          if (saved.status !== 'PENDING_PAYMENT' || checkoutStateFromReservation(saved.status, saved.expiresAt) === 'EXPIRED') session.current.clearCheckoutProgress()
          setReservation(saved)
          setOffer({
            roomTypeId: selection.roomTypeId, ratePlanId: selection.ratePlanId,
            roomTypeName: summary?.roomTypeName ?? bookingText(locale, "선택한 객실"), ratePlanName: summary?.ratePlanName ?? bookingText(locale, "선택한 요금제"),
            breakfastIncluded: false, remaining: 0, nightlyPrices: saved.nightlyPrices, total: saved.total,
            currency: saved.currency, policyVersion: '',
          })
          setState(checkoutStateFromReservation(saved.status, saved.expiresAt))
          setLoadingOffer(false)
          return
        } catch {
          if (!active) return
        }
      }
      const fields = availabilityRequestFields({ ...selection.criteria, breakfastOnly: false })
      try {
        const result = await api.availability(new URLSearchParams(Object.entries(fields).map(([key, value]) => [key, String(value)])))
        if (!active) return
        const matched = result.offers.find(item => item.roomTypeId === selection.roomTypeId && item.ratePlanId === selection.ratePlanId) ?? null
        if (!matched) setError(bookingText(locale, "선택한 객실을 현재 판매하지 않습니다. 객실 검색으로 돌아가 다른 객실을 선택해 주세요."))
        setOffer(matched)
      } catch {
        if (active) setError(bookingText(locale, "선택한 객실 정보를 불러오지 못했습니다. 객실 검색으로 돌아가 다시 확인해 주세요."))
      } finally {
        if (active) setLoadingOffer(false)
      }
    }
    void load()
    return () => { active = false }
  }, [selection])

  useEffect(() => {
    if (!reservation || state === 'CONFIRMED') return
    const next = checkoutStateFromReservation(reservation.status, reservation.expiresAt, now)
    if (next === 'EXPIRED') { session.current.clearCheckoutProgress(); setState('EXPIRED') }
  }, [now, reservation, state])

  const criteriaSummary = useMemo(() => selection && <><strong>{selection.criteria.checkIn} — {selection.criteria.checkOut}</strong><span>{bookingText(locale, "성인")} {selection.criteria.adults}{bookingText(locale, "명")} {selection.criteria.children > 0 && <> · {bookingText(locale, "아동")} {selection.criteria.children}{bookingText(locale, "명")}</>} · {bookingText(locale, "객실")} {selection.criteria.rooms}{bookingText(locale, "개")}</span></>, [selection])
  const holdRemaining = reservation ? formatHoldRemaining(reservation.expiresAt, now) : null
  const canSubmit = Boolean(selection && offer && guest.name.trim() && guest.email.trim() && guest.phone.trim() && agreed && state === 'DETAILS')

  async function hold(event: FormEvent) {
    event.preventDefault()
    if (!selection || !offer || !canSubmit) return
    setState('HOLDING')
    setError('')
    try {
      const body = {
        roomTypeId: offer.roomTypeId, ratePlanId: offer.ratePlanId,
        checkIn: selection.criteria.checkIn, checkOut: selection.criteria.checkOut,
        adults: selection.criteria.adults, children: selection.criteria.children, rooms: selection.criteria.rooms,
        expectedTotal: offer.total, guest: { name: guest.name.trim(), email: guest.email.trim(), phone: guest.phone.trim() },
      }
      const fingerprint = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(body))))).map(value => value.toString(16).padStart(2, '0')).join('')
      const attempt = session.current.checkoutAttempt(fingerprint, { managementToken: createManagementToken(), idempotencyKey: crypto.randomUUID() })
      const created = await api.reserve(body, attempt.managementToken, attempt.idempotencyKey)
      const access = { reservationId: '', managementToken: attempt.managementToken }
      session.current.clearCheckoutAttempt()
      const savedAccess = { ...access, reservationId: created.id }
      session.current.saveReservationAccess(savedAccess)
      session.current.saveCheckoutProgress(selection, savedAccess, { roomTypeName: offer.roomTypeName, ratePlanName: offer.ratePlanName })
      setReservation(created)
      setState(checkoutStateFromReservation(created.status, created.expiresAt))
    } catch (reason) {
      setState('DETAILS')
      if (reason instanceof ApiFailure && reason.status >= 400 && reason.status < 500) session.current.clearCheckoutAttempt()
      setError(reason instanceof Error && reason.message === 'CHECKOUT_RETRY_INPUT_CHANGED' ? (locale === 'ko' ? '이전 요청의 결과가 불확실합니다. 동일한 예약자 정보와 객실 조건으로 다시 시도해 주세요.' : 'The previous request result is uncertain. Retry with exactly the same guest details and room selection.') : reason instanceof Error && reason.message === 'CHECKOUT_STORAGE_REQUIRED' ? (locale === 'ko' ? '안전한 재시도를 위해 브라우저 세션 저장소를 허용해 주세요.' : 'Allow browser session storage to retry safely.') : messageFor(reason, locale))
    }
  }

  async function openPayment() {
    if (paymentBusy.current || !reservation || !selection || state !== 'PAYMENT_READY') return
    const access = session.current.loadCheckoutProgress(selection)
    if (!access) { setError(bookingText(locale, "예약 관리 정보를 찾을 수 없습니다. 객실 검색부터 다시 진행해 주세요.")); return }
    paymentBusy.current = true
    setState('PAYMENT_OPEN')
    setError('')
    try {
      if (paymentMode === 'fake') {
        await api.pay(reservation.id, access.managementToken, `booking-payment-${reservation.id}`, 'SUCCESS')
        window.location.assign(`${locale === 'en' ? '/en' : ''}/booking/complete`)
      } else {
        await requestTossCheckout(localizedTossCheckout(await api.tossCheckout(reservation.id, access.managementToken, crypto.randomUUID()), locale), locale)
      }
    } catch (reason) {
      setState('PAYMENT_READY')
      setError(locale === 'en' ? 'Payment did not complete. Retry before the hold expires or check the server status.' : reason instanceof TossCheckoutFailure ? reason.message : checkoutFailureMessage('API', reason))
    } finally { paymentBusy.current = false }
  }

  if (!selection) return <CustomerBookingShell step="checkout" locale={locale}><section className="booking-route-state"><h1>{bookingText(locale, "선택한 객실이 없습니다")}</h1><p>{bookingText(locale, "객실 검색에서 요금제를 선택한 뒤 예약 정보를 입력해 주세요.")}</p><a className="primary" href={locale === 'en' ? '/en' : '/#booking'}>{bookingText(locale, "객실 검색으로 돌아가기")}</a></section></CustomerBookingShell>

  const returnToResults = resultsPath(selection.criteria, locale)
  return <CustomerBookingShell step="checkout" locale={locale} summary={criteriaSummary}>
    <section className="booking-checkout-page" aria-labelledby="booking-checkout-title">
      <header className="booking-checkout-heading"><div><p className="section-kicker">RESERVATION DETAILS</p><h1 id="booking-checkout-title">{bookingText(locale, "예약 정보를 확인해 주세요")}</h1><p>{bookingText(locale, "결제 전 서버가 객실 재고와 최종 금액을 다시 확인합니다.")}</p></div></header>
      <p className="test-payment-banner" role="status"><ShieldCheck size={18} aria-hidden="true" /> {bookingText(locale, "테스트 환경입니다. 실제로 결제되지 않아요.")}</p>
      {error && <div className="booking-checkout-error" role="alert" ref={alert} tabIndex={-1}>{error}</div>}
      {loadingOffer && <div className="booking-checkout-loading" role="status">{bookingText(locale, "선택한 객실과 금액을 확인하고 있습니다…")}</div>}
      {!loadingOffer && !offer && <a className="outline booking-checkout-return" href={returnToResults}>{bookingText(locale, "객실 검색으로 돌아가기")}</a>}
      {offer && <div className="booking-checkout-layout">
        <form className="booking-guest-form" onSubmit={hold}>
          <fieldset disabled={state !== 'DETAILS'}>
            <legend>{bookingText(locale, "예약자 정보")}</legend>
            <label htmlFor="guest-name">{bookingText(locale, "예약자 이름")}<input id="guest-name" name="name" autoComplete="name" value={guest.name} onChange={event => setGuest(current => ({ ...current, name: event.target.value }))} required /></label>
            <label htmlFor="guest-email">{bookingText(locale, "이메일")}<input id="guest-email" name="email" type="email" autoComplete="email" value={guest.email} onChange={event => setGuest(current => ({ ...current, email: event.target.value }))} required /></label>
            <label htmlFor="guest-phone">{bookingText(locale, "전화번호")}<input id="guest-phone" name="phone" type="tel" inputMode="tel" autoComplete="tel" maxLength={30} value={guest.phone} onChange={event => setGuest(current => ({ ...current, phone: event.target.value }))} required /></label>
            <label className="booking-policy"><input type="checkbox" checked={agreed} onChange={event => setAgreed(event.target.checked)} required /><span>{bookingText(locale, "예약 및 결제 서비스 이용 약관과 개인정보 처리에 동의합니다.")}</span></label>
          </fieldset>
          {state === 'DETAILS' && <button className="primary booking-submit" disabled={!canSubmit}>{bookingText(locale, "예약 및 결제 진행")} <ArrowRight size={18} aria-hidden="true" /></button>}
          {state === 'HOLDING' && <p className="booking-inline-status" role="status">{bookingText(locale, "서버에서 객실 재고와 최종 금액을 확인하고 있습니다…")}</p>}
          {state === 'EXPIRED' && <a className="primary booking-submit" href={returnToResults}>{bookingText(locale, "객실 다시 검색하기")}</a>}
        </form>
        <aside className="booking-order-summary" aria-label={bookingText(locale, "선택한 객실 요약")}><p className="section-kicker">YOUR STAY</p><h2>{offer.roomTypeName}</h2><p>{offer.ratePlanName}</p><dl><div><dt>{bookingText(locale, "숙박 일정")}</dt><dd>{selection.criteria.checkIn} — {selection.criteria.checkOut}</dd></div><div><dt>{bookingText(locale, "투숙 인원")}</dt><dd>{bookingText(locale, "성인")} {selection.criteria.adults}{bookingText(locale, "명")} · {bookingText(locale, "객실")} {selection.criteria.rooms}{bookingText(locale, "개")}</dd></div></dl><div className="booking-total"><span>{bookingText(locale, "총 결제 예정 금액")}</span><strong>₩{money.format(offer.total)}</strong><small>{bookingText(locale, "객실")} {selection.criteria.rooms}{bookingText(locale, "개")} · {offer.nightlyPrices.length}{bookingText(locale, "박 · 세금 포함")}</small></div></aside>
      </div>}
      {reservation && state !== 'EXPIRED' && state !== 'CONFIRMED' && <section className="booking-payment-ready" aria-labelledby="payment-ready-title"><div><p className="section-kicker">PAYMENT</p><h2 id="payment-ready-title">{bookingText(locale, "객실을 확보했습니다")}</h2><p><Clock3 size={18} aria-hidden="true" /> {bookingText(locale, "남은 확보 시간")} <strong>{holdRemaining}</strong></p><span>{bookingText(locale, "예약자 정보는 잠겨 있습니다. 확보 시간 안에 결제를 완료해 주세요.")}</span></div>{paymentMode === 'toss-test' || paymentMode === 'fake' ? <button type="button" className="primary" onClick={openPayment} disabled={state === 'PAYMENT_OPEN'}>{state === 'PAYMENT_OPEN' ? bookingText(locale, "토스 결제창을 여는 중…") : paymentMode === 'fake' ? bookingText(locale, "테스트 결제 완료하기") : bookingText(locale, "토스 테스트 결제")}</button> : <p className="booking-payment-unavailable" role="status">{bookingText(locale, "토스 테스트 키가 설정되지 않아 결제창을 열 수 없습니다. 확보 시간 안에 운영자에게 설정을 요청해 주세요.")}</p>}</section>}
    </section>
  </CustomerBookingShell>
}
