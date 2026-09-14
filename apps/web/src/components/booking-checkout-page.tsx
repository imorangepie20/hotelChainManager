import { FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { ArrowRight, Clock3, ShieldCheck } from 'lucide-react'

import { ApiFailure, type Offer, type Reservation, api, createManagementToken } from '../lib/api'
import { type BookingCriteria, serializeBookingCriteria } from '../lib/booking-query'
import { BookingSessionStore, type BookingSelection } from '../lib/booking-session'
import { checkoutStateFromReservation, formatHoldRemaining, type CheckoutState } from '../lib/booking-checkout-state'
import { requestTossCheckout } from '../lib/toss-payments'
import { availabilityRequestFields } from '../lib/latest-availability-request'
import { CustomerBookingShell } from './customer-shell'

const money = new Intl.NumberFormat('ko-KR')
type Props = { locale?: 'ko' | 'en' }

function resultsPath(criteria: BookingCriteria, locale: 'ko' | 'en') {
  return `${locale === 'en' ? '/en' : ''}/booking/results?${serializeBookingCriteria(criteria)}`
}

function messageFor(reason: unknown): string {
  if (reason instanceof ApiFailure) {
    if (reason.code === 'PRICE_CHANGED') return '요금이 변경되었습니다. 최신 객실과 금액을 다시 확인해 주세요.'
    if (reason.code === 'SOLD_OUT') return '선택한 객실이 매진되었습니다. 다른 객실을 다시 선택해 주세요.'
    if (reason.code === 'HOLD_EXPIRED') return '객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.'
  }
  return '현재 상태를 확인하지 못했습니다. 입력 내용은 유지되어 있습니다. 잠시 후 다시 시도해 주세요.'
}

export function BookingCheckoutPage({ locale = 'ko' }: Props) {
  const session = useRef(new BookingSessionStore())
  const [selection] = useState<BookingSelection | null>(() => session.current.loadSelection())
  const [offer, setOffer] = useState<Offer | null>(null)
  const [reservation, setReservation] = useState<Reservation | null>(null)
  const [state, setState] = useState<CheckoutState>('DETAILS')
  const [guest, setGuest] = useState({ name: '', email: '' })
  const [agreed, setAgreed] = useState(false)
  const [loadingOffer, setLoadingOffer] = useState(Boolean(selection))
  const [error, setError] = useState('')
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const interval = window.setInterval(() => setNow(new Date()), 1_000)
    return () => window.clearInterval(interval)
  }, [])

  useEffect(() => {
    if (!selection) return
    let active = true
    const fields = availabilityRequestFields({ ...selection.criteria, breakfastOnly: false })
    api.availability(new URLSearchParams(Object.entries(fields).map(([key, value]) => [key, String(value)])))
      .then(result => {
        if (!active) return
        const matched = result.offers.find(item => item.roomTypeId === selection.roomTypeId && item.ratePlanId === selection.ratePlanId) ?? null
        if (!matched) setError('선택한 객실을 현재 판매하지 않습니다. 객실 검색으로 돌아가 다른 객실을 선택해 주세요.')
        setOffer(matched)
      })
      .catch(() => { if (active) setError('선택한 객실 정보를 불러오지 못했습니다. 객실 검색으로 돌아가 다시 확인해 주세요.') })
      .finally(() => { if (active) setLoadingOffer(false) })

    const access = session.current.loadCheckoutProgress(selection)
    if (access) api.getReservation(access.reservationId, access.managementToken).then(saved => {
      if (!active) return
      setReservation(saved)
      setState(checkoutStateFromReservation(saved.status, saved.expiresAt))
    }).catch(() => { if (active) setError('이전 예약 확보 상태를 확인하지 못했습니다. 객실을 다시 검색해 주세요.') })
    return () => { active = false }
  }, [selection])

  useEffect(() => {
    if (!reservation || state === 'CONFIRMED') return
    const next = checkoutStateFromReservation(reservation.status, reservation.expiresAt, now)
    if (next === 'EXPIRED') setState('EXPIRED')
  }, [now, reservation, state])

  const criteriaSummary = useMemo(() => selection && <><strong>{selection.criteria.checkIn} — {selection.criteria.checkOut}</strong><span>성인 {selection.criteria.adults}명{selection.criteria.children > 0 && <> · 아동 {selection.criteria.children}명</>} · 객실 {selection.criteria.rooms}개</span></>, [selection])
  const holdRemaining = reservation ? formatHoldRemaining(reservation.expiresAt, now) : null
  const canSubmit = Boolean(selection && offer && guest.name.trim() && guest.email.trim() && agreed && state === 'DETAILS')

  async function hold(event: FormEvent) {
    event.preventDefault()
    if (!selection || !offer || !canSubmit) return
    const access = { reservationId: '', managementToken: createManagementToken() }
    setState('HOLDING')
    setError('')
    try {
      const created = await api.reserve({
        roomTypeId: offer.roomTypeId, ratePlanId: offer.ratePlanId,
        checkIn: selection.criteria.checkIn, checkOut: selection.criteria.checkOut,
        adults: selection.criteria.adults, children: selection.criteria.children, rooms: selection.criteria.rooms,
        expectedTotal: offer.total, guest: { name: guest.name.trim(), email: guest.email.trim() },
      }, access.managementToken, crypto.randomUUID())
      const savedAccess = { ...access, reservationId: created.id }
      session.current.saveReservationAccess(savedAccess)
      session.current.saveCheckoutProgress(selection, savedAccess)
      setReservation(created)
      setState(checkoutStateFromReservation(created.status, created.expiresAt))
    } catch (reason) {
      setState('DETAILS')
      setError(messageFor(reason))
    }
  }

  async function openPayment() {
    if (!reservation || !selection || state !== 'PAYMENT_READY') return
    const access = session.current.loadCheckoutProgress(selection)
    if (!access) { setError('예약 관리 정보를 찾을 수 없습니다. 객실 검색부터 다시 진행해 주세요.'); return }
    setState('PAYMENT_OPEN')
    setError('')
    try {
      await requestTossCheckout(await api.tossCheckout(reservation.id, access.managementToken, crypto.randomUUID()))
    } catch (reason) {
      setState('PAYMENT_READY')
      setError(reason instanceof Error ? reason.message : '결제창을 닫았습니다. 확보 시간 안에 다시 시도해 주세요.')
    }
  }

  if (!selection) return <CustomerBookingShell step="checkout" locale={locale}><section className="booking-route-state"><h1>선택한 객실이 없습니다</h1><p>객실 검색에서 요금제를 선택한 뒤 예약 정보를 입력해 주세요.</p><a className="primary" href={locale === 'en' ? '/en' : '/#booking'}>객실 검색으로 돌아가기</a></section></CustomerBookingShell>

  const returnToResults = resultsPath(selection.criteria, locale)
  return <CustomerBookingShell step="checkout" locale={locale} summary={criteriaSummary}>
    <section className="booking-checkout-page" aria-labelledby="booking-checkout-title">
      <header className="booking-checkout-heading"><div><p className="section-kicker">RESERVATION DETAILS</p><h1 id="booking-checkout-title">예약 정보를 확인해 주세요</h1><p>결제 전 서버가 객실 재고와 최종 금액을 다시 확인합니다.</p></div></header>
      <p className="test-payment-banner" role="status"><ShieldCheck size={18} aria-hidden="true" /> 테스트 환경입니다. 실제로 결제되지 않아요.</p>
      {error && <div className="booking-checkout-error" role="alert">{error}</div>}
      {loadingOffer && <div className="booking-checkout-loading" role="status">선택한 객실과 금액을 확인하고 있습니다…</div>}
      {!loadingOffer && !offer && <a className="outline booking-checkout-return" href={returnToResults}>객실 검색으로 돌아가기</a>}
      {offer && <div className="booking-checkout-layout">
        <form className="booking-guest-form" onSubmit={hold}>
          <fieldset disabled={state !== 'DETAILS'}>
            <legend>예약자 정보</legend>
            <label htmlFor="guest-name">예약자 이름<input id="guest-name" name="name" autoComplete="name" value={guest.name} onChange={event => setGuest(current => ({ ...current, name: event.target.value }))} required /></label>
            <label htmlFor="guest-email">이메일<input id="guest-email" name="email" type="email" autoComplete="email" value={guest.email} onChange={event => setGuest(current => ({ ...current, email: event.target.value }))} required /></label>
            <label className="booking-policy"><input type="checkbox" checked={agreed} onChange={event => setAgreed(event.target.checked)} required /><span>예약 및 결제 서비스 이용 약관과 개인정보 처리에 동의합니다.</span></label>
          </fieldset>
          {state === 'DETAILS' && <button className="primary booking-submit" disabled={!canSubmit}>예약 및 결제 진행 <ArrowRight size={18} aria-hidden="true" /></button>}
          {state === 'HOLDING' && <p className="booking-inline-status" role="status">서버에서 객실 재고와 최종 금액을 확인하고 있습니다…</p>}
          {state === 'EXPIRED' && <a className="primary booking-submit" href={returnToResults}>객실 다시 검색하기</a>}
        </form>
        <aside className="booking-order-summary" aria-label="선택한 객실 요약"><p className="section-kicker">YOUR STAY</p><h2>{offer.roomTypeName}</h2><p>{offer.ratePlanName}</p><dl><div><dt>숙박 일정</dt><dd>{selection.criteria.checkIn} — {selection.criteria.checkOut}</dd></div><div><dt>투숙 인원</dt><dd>성인 {selection.criteria.adults}명 · 객실 {selection.criteria.rooms}개</dd></div></dl><div className="booking-total"><span>총 결제 예정 금액</span><strong>₩{money.format(offer.total)}</strong><small>객실 {selection.criteria.rooms}개 · {offer.nightlyPrices.length}박 · 세금 포함</small></div></aside>
      </div>}
      {reservation && state !== 'EXPIRED' && state !== 'CONFIRMED' && <section className="booking-payment-ready" aria-labelledby="payment-ready-title"><div><p className="section-kicker">PAYMENT</p><h2 id="payment-ready-title">객실을 확보했습니다</h2><p><Clock3 size={18} aria-hidden="true" /> 남은 확보 시간 <strong>{holdRemaining}</strong></p><span>예약자 정보는 잠겨 있습니다. 확보 시간 안에 결제를 완료해 주세요.</span></div><button type="button" className="primary" onClick={openPayment} disabled={state === 'PAYMENT_OPEN'}>{state === 'PAYMENT_OPEN' ? '토스 결제창을 여는 중…' : '토스 테스트 결제'}</button></section>}
    </section>
  </CustomerBookingShell>
}
