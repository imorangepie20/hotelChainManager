import { FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { ArrowRight, BedDouble, CalendarDays, Coffee, ShieldCheck, Waves } from 'lucide-react'

import { type Hotel, type Offer, api } from '../lib/api'
import { type BookingCriteria, serializeBookingCriteria } from '../lib/booking-query'
import { BookingSessionStore } from '../lib/booking-session'
import { LatestAvailabilityRequest, availabilityRequestFields } from '../lib/latest-availability-request'
import { GuestSelector } from './guest-selector'
import { HotelSelector } from './hotel-selector'
import { StayDatePicker } from './stay-date-picker'
import { CustomerBookingShell } from './customer-shell'

const money = new Intl.NumberFormat('ko-KR')

type BookingSearchPageProps = { criteria: BookingCriteria }

const criteriaKey = (criteria: BookingCriteria) => [criteria.hotelId, criteria.checkIn, criteria.checkOut, criteria.adults, criteria.children, criteria.rooms].join('|')

export function BookingSearchPage({ criteria }: BookingSearchPageProps) {
  const [hotels, setHotels] = useState<Hotel[]>([])
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(criteria)
  const [offers, setOffers] = useState<Offer[]>([])
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState('')
  const currentCriteriaKey = criteriaKey(criteria)
  const requests = useRef(new LatestAvailabilityRequest(currentCriteriaKey))
  const session = useRef(new BookingSessionStore())

  useEffect(() => { api.hotels().then(setHotels).catch(() => setHotels([])) }, [])

  useEffect(() => {
    const request = requests.current.start(currentCriteriaKey)
    setBusy(true)
    setError('')
    setOffers([])
    session.current.clearSelection()
    const fields = availabilityRequestFields({ ...criteria, breakfastOnly: false })
    api.availability(new URLSearchParams(Object.entries(fields).map(([key, value]) => [key, String(value)])))
      .then(result => { if (requests.current.isCurrent(request)) setOffers(result.offers) })
      .catch(() => { if (requests.current.isCurrent(request)) setError('객실 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.') })
      .finally(() => { if (requests.current.complete(request)) setBusy(false) })
  }, [currentCriteriaKey])

  const hotel = hotels.find(item => item.id === criteria.hotelId)
  const groupedOffers = useMemo(() => Array.from(offers.reduce((groups, offer) => {
    const group = groups.get(offer.roomTypeId) ?? []
    group.push(offer)
    groups.set(offer.roomTypeId, group)
    return groups
  }, new Map<string, Offer[]>()).entries()), [offers])

  function applyCriteria(event: FormEvent) {
    event.preventDefault()
    const query = serializeBookingCriteria(draft)
    window.location.assign(`/booking/results?${query}`)
  }

  function choose(offer: Offer) {
    session.current.saveSelection({ criteria, roomTypeId: offer.roomTypeId, ratePlanId: offer.ratePlanId })
    window.location.assign('/booking/checkout')
  }

  const summary = <><strong>{hotel?.region ?? '선택한 지점'}</strong><span>{criteria.checkIn} — {criteria.checkOut} · 성인 {criteria.adults}명{criteria.children > 0 && <> · 아동 {criteria.children}명</>} · 객실 {criteria.rooms}개</span></>

  return <CustomerBookingShell step="search" summary={summary}>
    <section className="booking-search-page" aria-labelledby="booking-results-title">
      <div className="booking-results-heading">
        <div><p className="section-kicker">AVAILABLE ROOMS</p><h1 id="booking-results-title">예약 가능한 객실</h1><p>표시 금액은 전체 숙박 기간의 서버 계산 총액입니다.</p></div>
        <button type="button" className="outline booking-edit-button" onClick={() => setEditing(current => !current)} aria-expanded={editing}>검색 조건 수정</button>
      </div>
      {editing && <form className="booking-edit-form" onSubmit={applyCriteria}>
        <fieldset className="search-grid" aria-label="예약 검색 조건">
          <HotelSelector hotels={hotels} value={draft.hotelId} onChange={hotelId => setDraft(current => ({ ...current, hotelId }))} />
          <StayDatePicker checkIn={draft.checkIn} checkOut={draft.checkOut} onChange={(checkIn, checkOut) => setDraft(current => ({ ...current, checkIn, checkOut }))} />
          <GuestSelector adults={draft.adults} children={draft.children} rooms={draft.rooms} onChange={next => setDraft(current => ({ ...current, ...next }))} />
          <button className="primary search-button">조건 적용 <ArrowRight size={18} /></button>
        </fieldset>
      </form>}
      {error && <p className="message error" role="alert">{error}</p>}
      {busy && <p className="booking-results-state" aria-live="polite">판매 가능 객실을 확인하고 있습니다…</p>}
      {!busy && !error && groupedOffers.length === 0 && <div className="empty-state"><CalendarDays /><h2>예약 가능한 객실이 없습니다</h2><p>날짜 또는 투숙 인원을 바꿔 다시 확인해 주세요.</p></div>}
      <div className="booking-room-groups">
        {groupedOffers.map(([roomTypeId, roomOffers], groupIndex) => <section key={roomTypeId} className="booking-room-group" aria-labelledby={`room-type-${roomTypeId}`}>
          <h2 id={`room-type-${roomTypeId}`}>{roomOffers[0]!.roomTypeName}</h2>
          <div className="offers">{roomOffers.map((offer, offerIndex) => <article className="offer-card" key={offer.ratePlanId}>
            <div className={`room-visual visual-${(groupIndex + offerIndex) % 3}`}><span>{String(groupIndex + 1).padStart(2, '0')}</span><Waves size={42} /></div>
            <div className="offer-body"><div className="offer-top"><div><p>{offer.breakfastIncluded ? 'BREAKFAST INCLUDED' : 'ROOM ONLY'}</p><h3>{offer.ratePlanName}</h3></div><span className="remaining">잔여 {offer.remaining}실</span></div>
              <div className="amenities"><span><BedDouble /> 성인 {criteria.adults}명{criteria.children > 0 && <> · 아동 {criteria.children}명</>} 기준</span>{offer.breakfastIncluded && <span><Coffee /> 조식 포함</span>}<span><ShieldCheck /> 유연 취소</span></div>
              <div className="price-row"><div><small>객실 {criteria.rooms}개 · {offer.nightlyPrices.length}박 총액</small><strong>₩{money.format(offer.total)}</strong></div><button type="button" onClick={() => choose(offer)} aria-label={`${offer.roomTypeName} 선택`}>선택 <ArrowRight size={17} /></button></div>
            </div>
          </article>)}</div>
        </section>)}
      </div>
    </section>
  </CustomerBookingShell>
}
