import { FormEvent, useEffect, useMemo, useRef, useState } from 'react'

import { ArrowRight, BedDouble, Bot, CalendarDays, Check, Coffee, Menu, ShieldCheck, Users, Waves, X } from 'lucide-react'

import { ApiFailure, Hotel, Offer, Reservation, WebsiteNavigationItem, api, createManagementToken } from './lib/api'
import { parseContentPage, type ContentPageDocument } from './lib/content-page'
import { destinationContentByRegion, destinationContentFromPublished, type DestinationContent } from './lib/destination-content'
import { resolveCustomerRoute } from './lib/customer-route'
import { applyBookingIntent, availabilityRequestFields, filterOffersForRoomType, LatestAvailabilityRequest, type BookingIntent, type SearchState } from './lib/latest-availability-request'
import { ContentPage, ContentPageAfterHero, ContentPageHero } from './components/content-page'
import { ContentCollectionPage } from './components/content-collection-page'
import { GuestSelector } from './components/guest-selector'
import { HotelSelector } from './components/hotel-selector'

import { ConciergePanel, type ConciergeCriteria } from './components/concierge-panel'
import { StayDatePicker } from './components/stay-date-picker'



const money = new Intl.NumberFormat('ko-KR')

const iso = (date: Date) => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
const addDays = (count: number) => { const date = new Date(); date.setDate(date.getDate() + count); return iso(date) }
type AvailabilityCriteria = SearchState
const availabilityCriteriaKey = (criteria: AvailabilityCriteria) => [criteria.hotelId, criteria.checkIn, criteria.checkOut, criteria.adults, criteria.children, criteria.rooms, criteria.breakfastOnly, criteria.roomTypeId ?? ''].join('|')


export default function App() {

  const [hotels, setHotels] = useState<Hotel[]>([]); const [hotelId, setHotelId] = useState(''); const [checkIn, setCheckIn] = useState(addDays(7)); const [checkOut, setCheckOut] = useState(addDays(9)); const [adults, setAdults] = useState(2); const [children, setChildren] = useState(0); const [rooms, setRooms] = useState(1)
  const [offers, setOffers] = useState<Offer[]>([]); const [breakfastOnly, setBreakfastOnly] = useState(false); const [roomTypeId, setRoomTypeId] = useState<string | undefined>(); const [selected, setSelected] = useState<Offer | null>(null); const [guest, setGuest] = useState({ name: '', email: '' }); const [reservation, setReservation] = useState<Reservation | null>(null)
  const [publishedDestination, setPublishedDestination] = useState<DestinationContent | null>(null)
  const [contentPage, setContentPage] = useState<ContentPageDocument | null>(null)
  const [homeContentPage, setHomeContentPage] = useState<ContentPageDocument | null>(null)
  const [websiteNavigation, setWebsiteNavigation] = useState<WebsiteNavigationItem[]>([])
  const [pathname, setPathname] = useState(() => window.location.pathname)
  const locale = resolveCustomerRoute(pathname)?.locale ?? 'ko'
  const [websiteLoading, setWebsiteLoading] = useState(true)
  const [pageUnavailable, setPageUnavailable] = useState(false)
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [mobileMenuOpen, setMobileMenuOpen] = useState(false); const reservationPanelRef = useRef<HTMLElement>(null); const bookingFormRef = useRef<HTMLElement>(null)
  const currentAvailabilityKey = availabilityCriteriaKey({ hotelId, checkIn, checkOut, adults, children, rooms, breakfastOnly, roomTypeId })
  const availabilityRequests = useRef<LatestAvailabilityRequest | null>(null)
  if (!availabilityRequests.current) availabilityRequests.current = new LatestAvailabilityRequest(currentAvailabilityKey)
  useEffect(() => {
    const onPopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])
  useEffect(() => {
    api.hotels().then(data => setHotels(data)).catch(showError)
  }, [])
  useEffect(() => {
    let active = true
    setWebsiteNavigation([])
    api.websiteNavigation(locale).then(items => { if (active) setWebsiteNavigation(items) }).catch(() => { if (active) setWebsiteNavigation([]) })
    return () => { active = false }
  }, [locale])
  useEffect(() => {
    let active = true
    const route = resolveCustomerRoute(pathname)
    setPublishedDestination(null)
    setContentPage(null)
    setHomeContentPage(null)
    setPageUnavailable(false)
    setWebsiteLoading(true)
    if (!route) {
      setPageUnavailable(true)
    } else if (route.kind === 'page') {
      api.websitePage(route.pathname, locale)
        .then(page => {
          if (page.type === 'CONTENT_PAGE') {
            const parsed = parseContentPage(page, locale)
            if (!parsed) throw new Error('콘텐츠 페이지 형식이 올바르지 않습니다.')
            if (active) setContentPage(parsed)
            return
          }
          if (page.type !== 'HOTEL_LANDING' || !page.hotelId) throw new Error('페이지 유형과 호텔 연결 정보가 일치하지 않습니다.')
          if (!hotels.length) return
          const hotel = hotels.find(item => item.id === page.hotelId)
          if (!hotel) throw new Error('페이지에 연결된 호텔을 찾을 수 없습니다.')
          if (!active) return
          setHotelId(hotel.id)
          const destination = locale === 'en' ? destinationContentFromPublished(hotel.region, page.content, 'en') : destinationContentFromPublished(hotel.region, page.content)
          if (!destination) throw new Error('영어 콘텐츠 문서가 올바르지 않습니다.')
          setPublishedDestination(destination)
        })
        .catch(() => { if (active) setPageUnavailable(true) })
        .finally(() => { if (active) setWebsiteLoading(false) })
    } else if (route.kind === 'home') {
      api.websitePage(route.pathname, locale)
        .then(page => {
          if (page.type !== 'HOME_PAGE' || page.hotelId !== null) throw new Error('홈페이지 형식이 올바르지 않습니다.')
          const parsed = parseContentPage(page, locale)
          if (!parsed) throw new Error('홈페이지 콘텐츠를 읽을 수 없습니다.')
          if (active) setHomeContentPage(parsed)
        })
        .catch(() => { if (active) { setHomeContentPage(null); if (locale === 'en') setPageUnavailable(true) } })
        .finally(() => { if (active) setWebsiteLoading(false) })
      if (locale === 'en') return () => { active = false }
      if (!hotels.length) return () => { active = false }
      const fallbackHotelId = hotelId || hotels.find(item => item.region === '속초')?.id || hotels[0]?.id || ''
      const hotel = hotels.find(item => item.id === fallbackHotelId)
      if (!hotel) return () => { active = false }
      setHotelId(hotel.id)
      api.hotelContent(hotel.id)
        .then(content => { if (active) setPublishedDestination(destinationContentFromPublished(hotel.region, content)) })
        .catch(() => { if (active) setPublishedDestination(null) })
    }
    return () => { active = false }
  }, [pathname, hotels, locale])
  useEffect(() => { const id = sessionStorage.getItem('latestReservation'); const token = id && sessionStorage.getItem(`reservation:${id}`); if (id && token) api.getReservation(id, token).then(setReservation).catch(showError) }, [])
  useEffect(() => {
    if (!availabilityRequests.current?.criteriaChanged(currentAvailabilityKey)) return
    setOffers([])
    setSelected(null)
    setBusy(false)
  }, [currentAvailabilityKey])
  const selectedHotel = useMemo(() => hotels.find(h => h.id === hotelId), [hotels, hotelId]); const destination = publishedDestination ?? destinationContentByRegion(selectedHotel?.region ?? '속초')
  const pageSeo = contentPage?.seo ?? homeContentPage?.seo ?? (locale === 'en' ? publishedDestination?.seo ?? { title: 'STAY HANEUL | English content', description: 'English content is not yet available.' } : destination.seo)
  useEffect(() => {
    document.documentElement.lang = locale
    document.title = pageSeo.title
    let description = document.querySelector('meta[name="description"]')
    if (!description) {
      description = document.createElement('meta')
      description.setAttribute('name', 'description')
      document.head.append(description)
    }
    description.setAttribute('content', pageSeo.description)
  }, [pageSeo.description, pageSeo.title])
  function showError(reason: unknown) { setError(reason instanceof ApiFailure ? reason.message : '서버에 연결할 수 없습니다.'); setBusy(false) }

  async function searchAvailability(criteria: AvailabilityCriteria, successNotice?: string): Promise<boolean> {
    const request = availabilityRequests.current!.start(availabilityCriteriaKey(criteria))
    setBusy(true); setError(''); setNotice(''); setOffers([]); setSelected(null)
    try {
      const fields = availabilityRequestFields(criteria)
      const params = new URLSearchParams(Object.entries(fields).map(([key, value]) => [key, String(value)]))
      const result = await api.availability(params)
      if (!availabilityRequests.current?.isCurrent(request)) return false
      const visibleOffers = filterOffersForRoomType(criteria.breakfastOnly ? result.offers.filter(offer => offer.breakfastIncluded) : result.offers, criteria.roomTypeId)
      setOffers(visibleOffers)
      if (!visibleOffers.length) setNotice(criteria.breakfastOnly ? '조식 포함 조건의 예약 가능 객실이 없습니다.' : '선택한 날짜에는 예약 가능한 객실이 없습니다.')
      else if (successNotice) setNotice(successNotice)
      return true
    } catch (reason) {
      if (!availabilityRequests.current?.isCurrent(request)) return false
      if (successNotice) setError('최신 객실 조회에 실패했습니다. 예약 바에서 다시 검색해 주세요.')
      else setError(reason instanceof ApiFailure ? reason.message : '서버에 연결할 수 없습니다.')
      return false
    } finally {
      if (availabilityRequests.current?.complete(request)) setBusy(false)
    }
  }
  async function search(event: FormEvent) { event.preventDefault(); await searchAvailability({ hotelId, checkIn, checkOut, adults, children, rooms, breakfastOnly, roomTypeId }) }
  async function applyConciergeCriteria(criteria: ConciergeCriteria): Promise<boolean> {
    const hotel = criteria.region ? hotels.find(item => item.region === criteria.region) : undefined
    if (!hotel || !criteria.checkIn || !criteria.checkOut || !criteria.adults) {
      setOffers([]); setSelected(null); setNotice(''); setError('최신 객실 조회에 실패했습니다. 예약 바에서 다시 검색해 주세요.')
      return false
    }
    const nextChildren = criteria.children ?? 0
    const nextRooms = criteria.rooms ?? 1
    const nextBreakfastOnly = Boolean(criteria.breakfastIncluded)
    setHotelId(hotel.id); setCheckIn(criteria.checkIn); setCheckOut(criteria.checkOut); setAdults(criteria.adults); setChildren(nextChildren); setRooms(nextRooms); setBreakfastOnly(nextBreakfastOnly); setRoomTypeId(undefined)
    return searchAvailability({ hotelId: hotel.id, checkIn: criteria.checkIn, checkOut: criteria.checkOut, adults: criteria.adults, children: nextChildren, rooms: nextRooms, breakfastOnly: nextBreakfastOnly, roomTypeId: undefined }, 'AI 조건을 적용해 최신 판매 가능 객실을 다시 조회했습니다.')
  }
  function applyContentBookingIntent(intent: BookingIntent) {
    const next = applyBookingIntent(intent, { hotelId, checkIn, checkOut, adults, children, rooms, breakfastOnly, roomTypeId })
    setHotelId(next.hotelId); setRoomTypeId(next.roomTypeId); setSelected(null); setReservation(null)
    navigateToPath('/')
    requestAnimationFrame(() => requestAnimationFrame(() => { bookingFormRef.current?.focus(); bookingFormRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }))
    void searchAvailability(next, '선택한 객실 조건으로 최신 판매 가능 객실을 다시 조회했습니다.')
  }
  async function reserve(event: FormEvent) { event.preventDefault(); if (!selected) return; setBusy(true); setError(''); const body = { roomTypeId: selected.roomTypeId, ratePlanId: selected.ratePlanId, checkIn, checkOut, adults, children, rooms, expectedTotal: selected.total, guest }; const saved = sessionStorage.getItem('pendingReservation'); const previous = saved ? JSON.parse(saved) : null; const pending = previous && JSON.stringify(previous.body) === JSON.stringify(body) ? previous : { token: createManagementToken(), key: crypto.randomUUID(), body }; const { token } = pending; sessionStorage.setItem('pendingReservation', JSON.stringify(pending)); try { const created = await api.reserve(pending.body, token, pending.key); sessionStorage.setItem(`reservation:${created.id}`, token); sessionStorage.setItem('latestReservation', created.id); sessionStorage.removeItem('pendingReservation'); setReservation(created); setNotice('객실이 10분 동안 확보되었습니다. 테스트 결제를 진행해 주세요.') } catch (reason) { if (reason instanceof ApiFailure && reason.status < 500) sessionStorage.removeItem('pendingReservation'); if (reason instanceof ApiFailure && ['PRICE_CHANGED', 'SOLD_OUT'].includes(reason.code)) { setSelected(null); setOffers([]) }; showError(reason) } finally { setBusy(false) } }

  async function pay(outcome: 'SUCCESS' | 'FAILURE') { if (!reservation) return; setBusy(true); setError(''); try { const token = sessionStorage.getItem(`reservation:${reservation.id}`)!; const result = await api.pay(reservation.id, token, crypto.randomUUID(), outcome); setReservation(await api.getReservation(reservation.id, token)); setNotice(result.paymentStatus === 'FAILED' ? '테스트 결제가 실패했습니다. 객실 확보 시간 안에 다시 시도할 수 있습니다.' : '테스트 결제가 완료되어 예약이 확정되었습니다.') } catch (reason) { if (reason instanceof ApiFailure && reason.code === 'HOLD_EXPIRED') { const token = sessionStorage.getItem(`reservation:${reservation.id}`)!; setReservation(await api.getReservation(reservation.id, token).catch(() => reservation)); setOffers([]); setSelected(null); setNotice('객실 확보 시간이 만료되었습니다. 날짜와 객실을 다시 검색해 주세요.') } else showError(reason) } finally { setBusy(false) } }

  async function cancel() { if (!reservation) return; setBusy(true); setError(''); try { const token = sessionStorage.getItem(`reservation:${reservation.id}`)!; const result = await api.cancel(reservation.id, token, crypto.randomUUID()); setReservation(await api.getReservation(reservation.id, token)); setNotice(`예약이 취소되었습니다. 테스트 환불액은 ₩${money.format(result.refundAmount)}입니다.`) } catch (reason) { showError(reason) } finally { setBusy(false) } }

  const closeMenu = () => setMobileMenuOpen(false)

  function chooseOffer(offer: Offer) {
    setSelected(offer); setReservation(null); setNotice('예약자 정보를 입력하면 객실을 10분 동안 확보합니다.')

    requestAnimationFrame(() => reservationPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }))

  }
  const pageNavigation = (locale === 'en' ? websiteNavigation.flatMap(item => [item, ...item.children]) : websiteNavigation.flatMap(item => item.children)).flatMap(item => {
    const route = resolveCustomerRoute(item.path)
    return route?.kind === 'page' ? [{ ...item, path: route.pathname }] : []
  })
  function navigateToPath(path: string) {
    const route = resolveCustomerRoute(path)
    if (!route) return
    if (window.location.pathname === route.pathname) return
    window.history.pushState({}, '', route.pathname)
    setPathname(route.pathname)
    setMobileMenuOpen(false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  function selectHotelForStay(nextHotelId: string) {
    const page = pageNavigation.find(item => item.hotelId === nextHotelId)
    if (page) {
      navigateToPath(page.path)
      return
    }
    const hotel = hotels.find(item => item.id === nextHotelId)
    if (!hotel) return
    setHotelId(nextHotelId); setRoomTypeId(undefined)
    setPublishedDestination(null)
    api.hotelContent(nextHotelId)
      .then(content => setPublishedDestination(destinationContentFromPublished(hotel.region, content)))
      .catch(() => setPublishedDestination(null))
  }
  function renderHeader(contentOnly: boolean) {
    return <header className="topbar"><a className="brand" href={locale === 'en' ? '/en' : contentOnly ? '/' : '#top'}><span>STAY</span> HANEUL</a><nav className={mobileMenuOpen ? 'open' : ''} aria-label={locale === 'en' ? 'Main navigation' : '주요 메뉴'}>{!contentOnly && <a onClick={closeMenu} href="#stays">객실</a>}{pageNavigation.map(item => <a key={item.id} onClick={event => { event.preventDefault(); navigateToPath(item.path) }} href={item.path}>{item.label}</a>)}{!contentOnly && <><a onClick={closeMenu} href="#experiences">경험</a><a onClick={closeMenu} href="#offers">오퍼</a><a onClick={closeMenu} href="#booking">예약</a></>}</nav><div className="header-actions"><a href="/#reservation-management" className="manage-link">{locale === 'en' ? 'Booking (Korean)' : '예약 조회'}</a><span className="lang"><a href={locale === 'en' ? pathname.slice(3) || '/' : pathname} lang="ko" aria-current={locale === 'ko' ? 'page' : undefined}>KO</a><span> / </span><a href={locale === 'en' ? pathname : pathname === '/' ? '/en' : '/en' + pathname} lang="en" aria-current={locale === 'en' ? 'page' : undefined}>EN</a></span><button className="menu" aria-label={locale === 'en' ? 'Menu' : '메뉴'} aria-expanded={mobileMenuOpen} onClick={() => setMobileMenuOpen(open => !open)}>{mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}</button></div></header>
  }
  if (locale === 'en') {
    const route = resolveCustomerRoute(pathname)
    const englishFooter = <footer><div className="brand"><span>STAY</span> HANEUL</div><p>Fictional hotel chain · Portfolio demo</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer>
    if (pageUnavailable) return <>{renderHeader(true)}<main id="top"><section className="content-section"><h1>English content is not available.</h1><p>This page has not been published in English, or its address has changed.</p><a className="text-link dark" href="/en">English home <ArrowRight size={18} /></a><p><a href={pathname.slice(3) || '/'} lang="ko">View the Korean website</a></p></section></main>{englishFooter}</>
    if (route?.kind === 'collection') return <>{renderHeader(true)}<ContentCollectionPage key={pathname} locale="en" contentKind={route.contentKind} hotelSlug={route.hotelSlug} />{englishFooter}</>
    if (websiteLoading || (!contentPage && !homeContentPage && !publishedDestination)) return <>{renderHeader(true)}<main className="content-section" aria-live="polite"><p>Loading English content…</p></main></>
    const page = contentPage ?? homeContentPage
    if (page) return <>{renderHeader(true)}<main id="top"><ContentPage page={page} locale="en" onBookingIntent={applyContentBookingIntent} /></main>{englishFooter}</>
    if (publishedDestination) return <>{renderHeader(true)}<main id="top">
      <section className="hero"><img src={publishedDestination.heroImage} alt={publishedDestination.heroAlt} /><div className="hero-shade" /><div className="hero-copy"><p>{publishedDestination.eyebrow}</p><h1>{publishedDestination.title}</h1><p>{publishedDestination.description}</p><a href="/#booking" className="text-link">Book a room (Korean) <ArrowRight size={18} /></a></div></section>
      <section className="content-section editorial"><h2>Experiences</h2><div className="experience-grid">{publishedDestination.experiences.map((item, index) => <article className={`experience-card experience-${index}`} key={index}><p>{item.category}</p><h3>{item.title}</h3><span>{item.description}</span></article>)}</div></section>
      <section className="offers-band"><div className="content-section"><h2>Offers</h2><div className="story-offers">{publishedDestination.offers.map((item, index) => <article key={index}><h3>{item.title}</h3><p>{item.detail}</p><dl><div><dt>Booking period</dt><dd>{item.bookingPeriod}</dd></div><div><dt>Stay period</dt><dd>{item.stayPeriod}</dd></div></dl></article>)}</div></div></section>
      <section className="arrival-section"><div><h2>Arrival guide</h2><p>{publishedDestination.arrival.highlight}</p></div><dl><div><dt>Address</dt><dd>{publishedDestination.arrival.address}</dd></div><div><dt>Check-in / Check-out</dt><dd>{publishedDestination.arrival.checkInOut}</dd></div></dl></section>
    </main>{englishFooter}</>
  }
  if (pageUnavailable) return <>{renderHeader(true)}<main id="top"><section className="content-section"><p className="section-kicker">PAGE NOT FOUND</p><h1>요청한 페이지를 찾을 수 없습니다.</h1><p>발행되지 않았거나 주소가 변경되었습니다.</p><a className="text-link dark" href="/">메인으로 돌아가기 <ArrowRight size={18} /></a></section></main></>
  const activeRoute = resolveCustomerRoute(pathname)
  if (activeRoute?.kind === 'collection') return <>{renderHeader(true)}<ContentCollectionPage contentKind={activeRoute.contentKind} hotelSlug={activeRoute.hotelSlug} /><footer><div className="brand"><span>STAY</span> HANEUL</div><p>가상의 호텔 체인 예약·운영 플랫폼 포트폴리오</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer></>
  if (contentPage) return <>{renderHeader(true)}<main id="top"><ContentPage page={contentPage} onBookingIntent={applyContentBookingIntent} /></main><footer><div className="brand"><span>STAY</span> HANEUL</div><p>가상의 호텔 체인 예약·운영 플랫폼 포트폴리오</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer></>
  return <>{renderHeader(false)}
    <main id="top">{homeContentPage ? <ContentPageHero page={homeContentPage} headingId="hero-title" /> : <section className="hero" aria-labelledby="hero-title"><img src={destination.heroImage} alt={destination.heroAlt} /><div className="hero-shade" /><div className="hero-copy" key={destination.title}><p className="eyebrow">{destination.eyebrow}</p><h1 id="hero-title">{destination.title.split('\n').map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</h1><p>{destination.description}</p><a href="#booking" className="text-link">객실 예약하기 <ArrowRight size={18} /></a></div><div className="fiction">PORTFOLIO DEMO · 가상의 호텔입니다</div></section>}
      <section id="booking" ref={bookingFormRef} className="booking-shell" aria-labelledby="booking-title" tabIndex={-1}><div className="booking-heading"><div><p className="section-kicker">BOOK YOUR STAY</p><h2 id="booking-title">여정을 예약하세요</h2></div><ConciergePanel criteria={{ region: selectedHotel?.region, checkIn, checkOut, adults, children, rooms, breakfastIncluded: breakfastOnly }} onApply={applyConciergeCriteria} /></div><form className="search-grid" onSubmit={search}><HotelSelector hotels={hotels} value={hotelId} onChange={selectHotelForStay} /><StayDatePicker checkIn={checkIn} checkOut={checkOut} onChange={(nextCheckIn, nextCheckOut) => { setCheckIn(nextCheckIn); setCheckOut(nextCheckOut) }} /><GuestSelector adults={adults} children={children} rooms={rooms} onChange={({ adults: nextAdults, children: nextChildren, rooms: nextRooms }) => { setAdults(nextAdults); setChildren(nextChildren); setRooms(nextRooms) }} /><button className="primary search-button" disabled={busy || !hotelId}>{busy ? '확인 중…' : '객실 검색'} <ArrowRight size={18} /></button></form>{(error || notice) && <div role="status" className={error ? 'message error' : 'message'}>{error || notice}<button aria-label="알림 닫기" onClick={() => { setError(''); setNotice('') }}><X size={16} /></button></div>}</section>
      {homeContentPage && <ContentPageAfterHero page={homeContentPage} />}
      <section id="experiences" className="content-section editorial"><div className="section-head"><div><p className="section-kicker">A STAY TO REMEMBER</p><h2>{selectedHotel?.region ?? '속초'}에서 만나는 세 가지 장면</h2></div><p>머무는 시간에 따라<br />장소는 더 깊어집니다.</p></div><div className="experience-grid">{destination.experiences.map((item, index) => <article className={`experience-card experience-${index}`} key={item.title}><p>{item.category}</p><h3>{item.title}</h3><span>{item.description}</span><a href="#booking">{item.title}로 예약하기 <ArrowRight size={15} /></a></article>)}</div></section>
      <section id="offers" className="offers-band"><div className="content-section"><div className="section-head"><div><p className="section-kicker">SPECIAL OFFERS</p><h2>머무름을 위한 제안</h2></div><a href="#booking" className="text-link dark">전체 객실 보기 <ArrowRight size={17} /></a></div><div className="story-offers">{destination.offers.map((offer, index) => <article className="story-offer" key={offer.title}><span>0{index + 1}</span><h3>{offer.title}</h3><p>{offer.detail}</p><dl><div><dt>예약 기간</dt><dd>{offer.bookingPeriod}</dd></div><div><dt>투숙 기간</dt><dd>{offer.stayPeriod}</dd></div></dl><a href="#booking">예약하기 <ArrowRight size={16} /></a></article>)}</div></div></section>

      <section id="stays" className="content-section"><div className="section-head"><div><p className="section-kicker">ROOMS & RATES</p><h2>{selectedHotel?.region ?? '속초'}에서의 하룻밤</h2></div><p>표시 요금은 전체 숙박 기간 기준이며<br />세금이 포함된 테스트 금액입니다.</p></div><div className="results-summary" aria-live="polite">{offers.length > 0 && <><strong>{offers.length}개 객실 유형을 찾았습니다.</strong><span>{checkIn} — {checkOut} · 성인 {adults}명{children > 0 && <> · 아동 {children}명</>} · 객실 {rooms}개{breakfastOnly && <> · 조식 포함</>}</span></>}</div><div className="offers">{offers.map((offer, index) => <article className={`offer-card${selected?.ratePlanId === offer.ratePlanId ? ' selected' : ''}`} key={offer.ratePlanId}><div className={`room-visual visual-${index % 3}`}><span>{String(index + 1).padStart(2, '0')}</span><Waves size={42} /></div><div className="offer-body"><div className="offer-top"><div><p>{offer.breakfastIncluded ? 'BREAKFAST INCLUDED' : 'ROOM ONLY'}</p><h3>{offer.roomTypeName}</h3></div><span className="remaining">잔여 {offer.remaining}실</span></div><div className="amenities"><span><BedDouble /> 성인 {adults}명{children > 0 && <> · 아동 {children}명</>} 기준</span>{offer.breakfastIncluded && <span><Coffee /> 조식 포함</span>}<span><ShieldCheck /> 유연 취소</span></div><div className="price-row"><div><small>객실 {rooms}개 · {offer.nightlyPrices.length}박 총액</small><strong>₩{money.format(offer.total)}</strong></div><button type="button" disabled={busy} onClick={() => chooseOffer(offer)}>선택 <ArrowRight size={17} /></button></div></div></article>)}{!offers.length && <div className="empty-state"><CalendarDays /><h3>날짜를 선택해 객실을 찾아보세요</h3><p>실제 판매 가능 객실과 요금만 표시됩니다.</p></div>}</div></section>

      <section className="arrival-section"><div><p className="section-kicker">ARRIVAL GUIDE</p><h2>{selectedHotel?.region ?? '속초'}에 도착하는 시간</h2><p>{destination.arrival.highlight}</p></div><dl><div><dt>주소</dt><dd>{destination.arrival.address}</dd></div><div><dt>체크인 / 아웃</dt><dd>{destination.arrival.checkInOut}</dd></div></dl><a href="#booking" className="outline">객실 예약하기 <ArrowRight size={17} /></a></section>

      {selected && !reservation && <section id="reservation" className="reservation-panel" ref={reservationPanelRef} tabIndex={-1}><div><p className="section-kicker">GUEST DETAILS</p><h2>{selected.roomTypeName} 예약</h2><p>{checkIn} — {checkOut} · 성인 {adults}명 · 아동 {children}명 · 객실 {rooms}개</p></div><form onSubmit={reserve}><label>예약자 이름<input value={guest.name} onChange={e => setGuest({ ...guest, name: e.target.value })} required placeholder="홍길동" /></label><label>이메일<input type="email" value={guest.email} onChange={e => setGuest({ ...guest, email: e.target.value })} required placeholder="guest@example.com" /></label><div className="reservation-total"><span>결제 예정 금액</span><strong>₩{money.format(selected.total)}</strong></div><button className="primary" disabled={busy}>{busy ? '확보 중…' : '객실 확보하기'} <ArrowRight size={18} /></button></form></section>}

      {reservation && <section id="reservation-management" className="reservation-panel confirmed"><div className="status-icon"><Check /></div><div><p className="section-kicker">RESERVATION {reservation.status}</p><h2>{reservation.status === 'CONFIRMED' ? '예약이 확정되었습니다' : reservation.status === 'CANCELLED' ? '예약이 취소되었습니다' : reservation.status === 'EXPIRED' ? '예약 확보 시간이 만료되었습니다' : '객실을 확보했습니다'}</h2><p>예약 번호 {reservation.id}</p><p>{reservation.checkIn} — {reservation.checkOut} · {reservation.guest.name}</p></div><div className="manage-actions"><strong>₩{money.format(reservation.total)}</strong>{reservation.status === 'PENDING_PAYMENT' && <><button className="primary" onClick={() => pay('SUCCESS')} disabled={busy}>테스트 결제</button><button className="test-failure" onClick={() => pay('FAILURE')} disabled={busy}>결제 실패 시험</button></>}{reservation.status === 'CONFIRMED' && <button className="outline" onClick={cancel} disabled={busy}>예약 취소</button>}{reservation.status === 'EXPIRED' && <a className="outline" href="#booking">다시 검색</a>}</div><p className="session-note">예약 관리 정보는 현재 브라우저 세션에만 저장됩니다. 창을 닫으면 복구할 수 없습니다.</p></section>}

      <section className="values"><div><Bot /><p>COMING NEXT</p><h2>말로 찾는<br />나만의 스테이</h2></div><p>“다음 주 토요일, 아이와 함께 조식이 포함된 속초 호텔을 찾아줘.”<br />LangGraph 기반 AI 컨시어지가 실제 재고와 요금만 확인해 추천합니다.</p></section></main><footer><div className="brand"><span>STAY</span> HANEUL</div><p>가상의 호텔 체인 예약·운영 플랫폼 포트폴리오</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer></>

}
