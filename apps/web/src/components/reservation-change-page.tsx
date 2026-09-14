import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, BedDouble, Coffee } from 'lucide-react'
import { ApiFailure, api, type CustomerChangeQuote, type Offer, type Reservation } from '../lib/api'
import { BookingSessionStore } from '../lib/booking-session'
import { StayDatePicker } from './stay-date-picker'
import { GuestSelector } from './guest-selector'
import { changeAction } from '../lib/reservation-change-state'
import { changeText } from '../lib/reservation-change-copy'

type Props = { reservationId: string; locale?: 'ko' | 'en' }
type ChangeOffer = Offer & { difference: number }

const copy = {
  ko: { back: '예약 상세로', kicker: 'RESERVATION CHANGE', title: '예약을 변경하세요', help: '새 일정과 인원을 선택하면 서버가 가능한 객실과 최종 차액을 계산합니다.', quote: '변경 가능한 객실 조회', current: '현재 결제 금액', total: '변경 후 총액', charge: '추가 결제', refund: '예상 환불', same: '차액 없음', select: '이 조건으로 변경', loading: '예약 정보를 불러오는 중입니다.', quoting: '최신 재고와 금액을 확인하는 중입니다…', starting: '대상 객실을 확보하고 정산을 준비하는 중입니다…', missing: '이 브라우저에서 예약을 확인할 수 없습니다.', noOffers: '선택한 조건에 예약 가능한 객실이 없습니다.', adults: '성인', children: '아동', rooms: '객실' },
  en: { back: 'Reservation details', kicker: 'RESERVATION CHANGE', title: 'Change your reservation', help: 'Choose new dates and guests. The server will calculate available rooms and the final difference.', quote: 'Find available rooms', current: 'Current payment', total: 'New total', charge: 'Additional payment', refund: 'Expected refund', same: 'No difference', select: 'Change to this option', loading: 'Loading your reservation.', quoting: 'Checking current inventory and price…', starting: 'Holding the room and preparing settlement…', missing: 'This reservation is not available in this browser.', noOffers: 'No rooms are available for these conditions.', adults: 'Adults', children: 'Children', rooms: 'Rooms' },
} as const

export function ReservationChangePage({ reservationId, locale = 'ko' }: Props) {
  const text = { ...copy[locale], title: changeText(locale, 'changeReservation') }
  const session = useRef(new BookingSessionStore())
  const access = session.current.loadReservationAccess(reservationId)
  const [reservation, setReservation] = useState<Reservation | null>(null)
  const [checkIn, setCheckIn] = useState('')
  const [checkOut, setCheckOut] = useState('')
  const [adults, setAdults] = useState(1)
  const [children, setChildren] = useState(0)
  const [quote, setQuote] = useState<CustomerChangeQuote | null>(null)
  const [busy, setBusy] = useState<'LOAD' | 'QUOTE' | 'START' | null>('LOAD')
  const [error, setError] = useState('')
  const alert = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!access) { setBusy(null); return }
    let active = true
    api.getReservation(reservationId, access.managementToken).then(value => {
      if (!active) return
      setReservation(value); setCheckIn(value.checkIn); setCheckOut(value.checkOut)
      setAdults(value.adults ?? 1); setChildren(value.children ?? 0); setBusy(null)
    }).catch(reason => { if (active) { setError(message(reason)); setBusy(null) } })
    return () => { active = false }
  }, [reservationId])

  function message(reason: unknown) {
    if (reason instanceof ApiFailure && reason.code === 'PRICE_CHANGED') return changeText(locale, 'priceChanged')
    return reason instanceof ApiFailure ? reason.message : (locale === 'ko' ? '요청을 처리하지 못했습니다.' : 'We could not process the request.')
  }
  function showError(reason: unknown) { setError(message(reason)); requestAnimationFrame(() => alert.current?.focus()) }

  async function findOffers() {
    if (!access) return
    setBusy('QUOTE'); setError(''); setQuote(null)
    try { setQuote(await api.changeQuote(reservationId, access.managementToken, { checkIn, checkOut, adults, children })) }
    catch (reason) { showError(reason) }
    finally { setBusy(null) }
  }

  async function choose(offer: ChangeOffer) {
    if (!access || !quote || busy) return
    setBusy('START'); setError('')
    const storageKey = `reservation-change-idempotency:${reservationId}:${quote.quoteId}:${offer.roomTypeId}:${offer.ratePlanId}`
    const idempotencyKey = sessionStorage.getItem(storageKey) ?? crypto.randomUUID()
    sessionStorage.setItem(storageKey, idempotencyKey)
    try {
      const result = await api.startChange(reservationId, access.managementToken, idempotencyKey, {
        quoteId: quote.quoteId, roomTypeId: offer.roomTypeId, ratePlanId: offer.ratePlanId, expectedTotal: offer.total,
      })
      sessionStorage.removeItem(storageKey)
      if (changeAction(result.status) === 'PAY') window.location.assign(locale === 'en' ? '/en/reservation-change-payment' : '/reservation-change-payment')
      else window.location.assign(`${locale === 'en' ? '/en' : ''}/reservations/${reservationId}`)
    } catch (reason) { showError(reason); setBusy(null) }
  }

  const money = new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'ko-KR', { style: 'currency', currency: quote?.currency ?? reservation?.currency ?? 'KRW', maximumFractionDigits: 0 })
  if (!access) return <main className="reservation-change-shell"><section className="reservation-management-empty"><h1>{text.missing}</h1><a className="primary" href={`${locale === 'en' ? '/en' : ''}/reservations`}>{text.back}</a></section></main>
  if (busy === 'LOAD' || !reservation) return <main className="reservation-change-shell"><p role="status">{text.loading}</p></main>

  return <main className="reservation-change-shell" aria-labelledby="reservation-change-page-title">
    <a className="reservation-back" href={`${locale === 'en' ? '/en' : ''}/reservations/${reservationId}`}><ArrowLeft size={17} /> {text.back}</a>
    <header><p className="section-kicker">{text.kicker}</p><h1 id="reservation-change-page-title">{text.title}</h1><p>{text.help}</p></header>
    {error && <div className="booking-checkout-error" role="alert" tabIndex={-1} ref={alert}>{error}</div>}
    <section className="reservation-change-controls" aria-label={text.title}>
      <StayDatePicker checkIn={checkIn} checkOut={checkOut} onChange={(start, end) => { setCheckIn(start); setCheckOut(end); setQuote(null) }} />
      <GuestSelector adults={adults} children={children} rooms={reservation.rooms} onChange={value => { setAdults(value.adults); setChildren(value.children); setQuote(null) }} />
      <button className="primary" type="button" onClick={() => void findOffers()} disabled={busy !== null}>{busy === 'QUOTE' ? text.quoting : text.quote} <ArrowRight size={17} /></button>
    </section>
    {busy === 'START' && <p className="booking-inline-status" role="status">{text.starting}</p>}
    {quote && <section className="reservation-change-results" aria-live="polite">
      <div className="reservation-change-current"><span>{text.current}</span><strong>{money.format(quote.previousTotal)}</strong><small>{text.adults} {reservation.adults} · {text.children} {reservation.children} · {text.rooms} {reservation.rooms}</small></div>
      {quote.offers.length === 0 ? <p>{text.noOffers}</p> : <div className="offers">{quote.offers.map(raw => {
        const offer = raw as ChangeOffer
        const delta = offer.difference
        return <article className="offer-card" key={`${offer.roomTypeId}:${offer.ratePlanId}`}>
          <div className="offer-body"><div className="offer-top"><div><p>{offer.breakfastIncluded ? 'BREAKFAST INCLUDED' : 'ROOM ONLY'}</p><h2>{offer.roomTypeName}</h2><h3>{offer.ratePlanName}</h3></div><span className="remaining">{offer.remaining}</span></div>
            <div className="amenities"><span><BedDouble /> {text.adults} {adults} · {text.children} {children}</span>{offer.breakfastIncluded && <span><Coffee /> BREAKFAST</span>}</div>
            <div className="reservation-change-price"><span>{text.total}</span><strong>{money.format(offer.total)}</strong><em>{delta > 0 ? `${text.charge} ${money.format(delta)}` : delta < 0 ? `${text.refund} ${money.format(Math.abs(delta))}` : text.same}</em></div>
            <button type="button" className="primary" onClick={() => void choose(offer)} disabled={busy !== null}>{text.select} <ArrowRight size={17} /></button>
          </div>
        </article>
      })}</div>}
    </section>}
  </main>
}
