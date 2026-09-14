import { useEffect, useMemo, useState } from 'react'

import { ArrowRight, Bot, Menu, X } from 'lucide-react'

import { Hotel, WebsiteNavigationItem, api } from './lib/api'
import { parseContentPage, type ContentPageDocument } from './lib/content-page'
import { destinationContentByRegion, destinationContentFromPublished, type DestinationContent } from './lib/destination-content'
import { captureWebsitePreview, storedWebsitePreviewForPath, clearWebsitePreview } from './lib/website-preview'
import { WebsitePreviewBanner } from './components/website-preview-banner'
import { resolveCustomerRoute } from './lib/customer-route'
import { parseBookingCriteria, serializeBookingCriteria } from './lib/booking-query'
import { websiteMetadata } from './lib/website-metadata'
import { type BookingIntent } from './lib/latest-availability-request'
import { ContentPage, ContentPageAfterHero, ContentPageHero } from './components/content-page'
import { ResponsiveCmsImage } from './components/responsive-cms-image'
import { ContentCollectionPage } from './components/content-collection-page'
import { GuestSelector } from './components/guest-selector'
import { HotelSelector } from './components/hotel-selector'
import { ReservationChangePaymentPage } from './components/reservation-change-payment-page'
import { BookingSearchPage } from './components/booking-search-page'
import { BookingCheckoutPage } from './components/booking-checkout-page'
import { BookingResultPage } from './components/booking-result-page'
import { ReservationManagementPage } from './components/reservation-management-page'
import { CustomerBookingShell } from './components/customer-shell'
import { captureTossReturn } from './lib/toss-payments'

import { ConciergePanel, type ConciergeCriteria } from './components/concierge-panel'
import { StayDatePicker } from './components/stay-date-picker'



const iso = (date: Date) => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
const addDays = (count: number) => { const date = new Date(); date.setDate(date.getDate() + count); return iso(date) }
// getter 자체가 SecurityError를 던져도 fragment 제거가 저장소 접근보다 먼저 실행된다.
const previewStorage = {
  getItem: (key: string) => window.sessionStorage.getItem(key),
  setItem: (key: string, value: string) => window.sessionStorage.setItem(key, value),
  removeItem: (key: string) => window.sessionStorage.removeItem(key),
}
const initialCustomerRoute = resolveCustomerRoute(window.location.pathname)
const reservationChangePaymentRoute = initialCustomerRoute?.kind === 'reservation-change-payment'
const bookingCompleteRoute = initialCustomerRoute?.kind === 'booking-complete'
const reservationPaymentResultRoute = initialCustomerRoute?.kind === 'reservation-payment-result'
const paymentReturnRoute = bookingCompleteRoute || reservationPaymentResultRoute
const initialTossReturn = paymentReturnRoute
  ? captureTossReturn(window.location, path => window.history.replaceState({}, '', path))
  : { kind: 'none' as const }
const initialPreview = reservationChangePaymentRoute || paymentReturnRoute
  ? null
  : captureWebsitePreview(window.location, previewStorage, url => window.history.replaceState({}, '', url))
    ?? storedWebsitePreviewForPath(window.location.pathname, previewStorage)

export default function App() {
  if (reservationChangePaymentRoute) return <ReservationChangePaymentPage locale={initialCustomerRoute?.locale ?? 'ko'} />
  return <BookingApp />
}

function BookingApp() {

  const [hotels, setHotels] = useState<Hotel[]>([]); const [hotelsReady, setHotelsReady] = useState(false); const [hotelId, setHotelId] = useState(''); const [checkIn, setCheckIn] = useState(addDays(7)); const [checkOut, setCheckOut] = useState(addDays(9)); const [adults, setAdults] = useState(2); const [children, setChildren] = useState(0); const [rooms, setRooms] = useState(1)
  const [publishedDestination, setPublishedDestination] = useState<DestinationContent | null>(null)
  const [contentPage, setContentPage] = useState<ContentPageDocument | null>(null)
  const [homeContentPage, setHomeContentPage] = useState<ContentPageDocument | null>(null)
  const [websiteNavigation, setWebsiteNavigation] = useState<WebsiteNavigationItem[]>([])
  const [previewSession, setPreviewSession] = useState(initialPreview)
  const [previewExpiresAt, setPreviewExpiresAt] = useState<string | null>(null)
  const [previewUnavailable, setPreviewUnavailable] = useState(false)
  const previewMode = previewSession !== null
  const [pathname, setPathname] = useState(() => window.location.pathname)
  const locale = previewSession ? (previewSession.path === '/en' || previewSession.path.startsWith('/en/') ? 'en' : 'ko') : resolveCustomerRoute(pathname)?.locale ?? 'ko'
  const [websiteLoading, setWebsiteLoading] = useState(true)
  const [pageUnavailable, setPageUnavailable] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  useEffect(() => {
    const onPopState = () => {
      setPreviewSession(storedWebsitePreviewForPath(window.location.pathname, previewStorage))
      setPreviewUnavailable(false); setPathname(window.location.pathname)
    }
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])
  useEffect(() => {
    api.hotels().then(data => setHotels(data)).catch(() => setHotels([])).finally(() => setHotelsReady(true))
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
    if (previewSession) {
      if (!hotelsReady) return () => { active = false }
      if (previewUnavailable) { setWebsiteLoading(false); return () => { active = false } }
      api.websitePreviewPage(previewSession.path, locale, previewSession.token).then(({ page, expiresAt }) => {
        if (!active) return
        if (Date.parse(expiresAt) <= Date.now()) throw new Error('미리보기가 만료됐습니다.')
        setPreviewExpiresAt(expiresAt)
        if (page.type === 'HOME_PAGE' || page.type === 'CONTENT_PAGE') {
          const parsed = parseContentPage(page, locale)
          if (!parsed) throw new Error('콘텐츠를 읽을 수 없습니다.')
          if (page.type === 'HOME_PAGE') setHomeContentPage(parsed)
          else setContentPage(parsed)
        } else {

          const hotel = hotels.find(item => item.id === page.hotelId)
          if (!hotel) throw new Error('호텔 연결이 올바르지 않습니다.')
          const draftContent = { ...page.content, mediaVariants: page.mediaVariants }
          const draft = locale === 'en' ? destinationContentFromPublished(hotel.region, draftContent, 'en') : destinationContentFromPublished(hotel.region, draftContent)
          if (!draft) throw new Error('콘텐츠를 읽을 수 없습니다.')
          setHotelId(hotel.id); setPublishedDestination(draft)
        }
      }).catch(() => { if (active) setPreviewUnavailable(true) }).finally(() => { if (active) setWebsiteLoading(false) })
      return () => { active = false }
    }
    if (route?.kind === 'booking-results' || route?.kind === 'booking-checkout' || route?.kind === 'booking-complete' || route?.kind === 'reservations' || route?.kind === 'reservation-detail') {
      setWebsiteLoading(false)
    } else if (!route) {
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

          const hotel = hotels.find(item => item.id === page.hotelId)
          if (!hotel) throw new Error('페이지에 연결된 호텔을 찾을 수 없습니다.')
          if (!active) return
          setHotelId(hotel.id)
          const publishedContent = { ...page.content, mediaVariants: page.mediaVariants }
          const destination = locale === 'en' ? destinationContentFromPublished(hotel.region, publishedContent, 'en') : destinationContentFromPublished(hotel.region, publishedContent)
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
  }, [pathname, hotels, hotelsReady, locale, previewSession, previewUnavailable])
  const selectedHotel = useMemo(() => hotels.find(h => h.id === hotelId), [hotels, hotelId]); const destination = publishedDestination ?? destinationContentByRegion(selectedHotel?.region ?? '속초')
  const pageSeo = contentPage?.seo ?? homeContentPage?.seo ?? (locale === 'en' ? publishedDestination?.seo ?? { title: 'STAY HANEUL | English content', description: 'English content is not yet available.' } : destination.seo)
  const canonicalPathname = previewSession?.path ?? resolveCustomerRoute(pathname)?.pathname ?? pathname
  useEffect(() => {
    const metadata = websiteMetadata({
      origin: window.location.origin,
      pathname: canonicalPathname,
      title: pageSeo.title,
      description: pageSeo.description,
      previewMode,
    })
    document.documentElement.lang = locale
    document.title = metadata.title
    const namedMeta = (name: string, content: string) => {
      let element = document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)
      if (!element) { element = document.createElement('meta'); element.name = name; document.head.append(element) }
      element.content = content
    }
    const propertyMeta = (property: string, content: string) => {
      let element = document.querySelector<HTMLMetaElement>(`meta[property="${property}"]`)
      if (!element) { element = document.createElement('meta'); element.setAttribute('property', property); document.head.append(element) }
      element.content = content
    }
    let canonical = document.querySelector<HTMLLinkElement>('link[rel="canonical"]')
    if (!canonical) { canonical = document.createElement('link'); canonical.rel = 'canonical'; document.head.append(canonical) }
    canonical.href = metadata.canonicalUrl
    namedMeta('description', metadata.description)
    namedMeta('robots', metadata.robots)
    propertyMeta('og:title', metadata.openGraphTitle)
    propertyMeta('og:description', metadata.openGraphDescription)
    propertyMeta('og:url', metadata.openGraphUrl)
  }, [canonicalPathname, locale, pageSeo.description, pageSeo.title, previewMode])
  useEffect(() => {
    if (!previewMode) return
    const links = Array.from(document.querySelectorAll<HTMLAnchorElement>('a[href]')).filter(link => /#booking|#reservation-management|^\/booking|^\/reservations/.test(link.getAttribute('href') ?? ''))
    const block = (event: Event) => event.preventDefault()
    links.forEach(link => { link.setAttribute('aria-disabled', 'true'); link.tabIndex = -1; link.addEventListener('click', block) })
    return () => { links.forEach(link => { link.removeAttribute('aria-disabled'); link.removeAttribute('tabindex'); link.removeEventListener('click', block) }) }
  }, [previewMode, contentPage, homeContentPage, publishedDestination])
  useEffect(() => {
    if (!previewMode) return
    const leaveForNavigation = (event: MouseEvent) => {
      const link = event.target instanceof Element ? event.target.closest<HTMLAnchorElement>('a[href]') : null
      if (!link || link.getAttribute('aria-disabled') === 'true') return
      const url = new URL(link.href, window.location.href)
      if (!['http:', 'https:'].includes(url.protocol)) return
      // 새 탭은 현재 검토 세션을 유지하고, 현재 탭의 일반 이동은 공개본으로 전환한다.
      if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || link.target === '_blank') return
      exitPreview()
    }
    document.addEventListener('click', leaveForNavigation, true)
    return () => document.removeEventListener('click', leaveForNavigation, true)
  }, [previewMode])
  function expirePreview() { setContentPage(null); setHomeContentPage(null); setPublishedDestination(null); setPreviewUnavailable(true) }
  function exitPreview() { clearWebsitePreview(previewStorage); setPreviewSession(null); setPreviewExpiresAt(null); setPreviewUnavailable(false) }
  function search(event: React.FormEvent) { event.preventDefault(); navigateToBookingResults({ hotelId, checkIn, checkOut, adults, children, rooms }) }
  async function applyConciergeCriteria(criteria: ConciergeCriteria): Promise<boolean> {
    if (previewMode) return false
    const hotel = criteria.region ? hotels.find(item => item.region === criteria.region) : undefined
    if (!hotel || !criteria.checkIn || !criteria.checkOut || !criteria.adults) {
      return false
    }
    const nextChildren = criteria.children ?? 0
    const nextRooms = criteria.rooms ?? 1
    setHotelId(hotel.id); setCheckIn(criteria.checkIn); setCheckOut(criteria.checkOut); setAdults(criteria.adults); setChildren(nextChildren); setRooms(nextRooms)
    navigateToBookingResults({ hotelId: hotel.id, checkIn: criteria.checkIn, checkOut: criteria.checkOut, adults: criteria.adults, children: nextChildren, rooms: nextRooms })
    return true
  }
  function applyContentBookingIntent(intent: BookingIntent) {
    if (previewMode) return
    setHotelId(intent.hotelId)
    navigateToBookingResults({ hotelId: intent.hotelId, checkIn, checkOut, adults, children, rooms })
  }

  const closeMenu = () => setMobileMenuOpen(false)

  const pageNavigation = (locale === 'en' ? websiteNavigation.flatMap(item => [item, ...item.children]) : websiteNavigation.flatMap(item => item.children)).flatMap(item => {
    const route = resolveCustomerRoute(item.path)
    return route?.kind === 'page' ? [{ ...item, path: route.pathname }] : []
  })
  function navigateToPath(path: string) {
    const route = resolveCustomerRoute(path)
    if (!route) return
    if (previewMode) exitPreview()
    if (window.location.pathname === route.pathname && !previewMode) return
    window.history.pushState({}, '', route.pathname)
    setPathname(route.pathname)
    setMobileMenuOpen(false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  function navigateToBookingResults(criteria: { hotelId: string; checkIn: string; checkOut: string; adults: number; children: number; rooms: number }) {
    const route = locale === 'en' ? '/en/booking/results' : '/booking/results'
    window.history.pushState({}, '', `${route}?${serializeBookingCriteria(criteria)}`)
    setPathname(route)
    setMobileMenuOpen(false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  function selectHotelForStay(nextHotelId: string) {
    if (previewMode) return
    const page = pageNavigation.find(item => item.hotelId === nextHotelId)
    if (page) {
      navigateToPath(page.path)
      return
    }
    const hotel = hotels.find(item => item.id === nextHotelId)
    if (!hotel) return
    setHotelId(nextHotelId)
    setPublishedDestination(null)
    api.hotelContent(nextHotelId)
      .then(content => setPublishedDestination(destinationContentFromPublished(hotel.region, content)))
      .catch(() => setPublishedDestination(null))
  }
  function renderHeader(contentOnly: boolean) {
    return <>{previewMode && previewExpiresAt && <WebsitePreviewBanner expiresAt={previewExpiresAt} locale={locale} onExpire={expirePreview} onExit={exitPreview} />}{renderPublicHeader(contentOnly)}</>
  }
  function renderPublicHeader(contentOnly: boolean) {
    return <header className="topbar"><a className="brand" href={locale === 'en' ? '/en' : contentOnly ? '/' : '#top'}><span>STAY</span> HANEUL</a><nav className={mobileMenuOpen ? 'open' : ''} aria-label={locale === 'en' ? 'Main navigation' : '주요 메뉴'}>{!contentOnly && <a onClick={closeMenu} href="/#booking">객실</a>}{pageNavigation.map(item => <a key={item.id} onClick={event => { event.preventDefault(); navigateToPath(item.path) }} href={item.path}>{item.label}</a>)}{!contentOnly && <><a onClick={closeMenu} href="#experiences">경험</a><a onClick={closeMenu} href="#offers">오퍼</a><a onClick={closeMenu} href="#booking">예약</a></>}</nav><div className="header-actions"><a href={locale === 'en' ? '/en/reservations' : '/reservations'} className="manage-link">{locale === 'en' ? 'My reservations' : '예약 조회'}</a><span className="lang"><a href={locale === 'en' ? pathname.slice(3) || '/' : pathname} lang="ko" aria-current={locale === 'ko' ? 'page' : undefined}>KO</a><span> / </span><a href={locale === 'en' ? pathname : pathname === '/' ? '/en' : '/en' + pathname} lang="en" aria-current={locale === 'en' ? 'page' : undefined}>EN</a></span><button className="menu" aria-label={locale === 'en' ? 'Menu' : '메뉴'} aria-expanded={mobileMenuOpen} onClick={() => setMobileMenuOpen(open => !open)}>{mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}</button></div></header>
  }
  if (previewMode && previewUnavailable) return <>{renderHeader(true)}<main className="content-section website-preview-error"><h1>{locale === 'en' ? 'Preview is unavailable.' : '미리보기를 사용할 수 없습니다.'}</h1><p>{locale === 'en' ? 'The link expired, was revoked, or the draft changed.' : '링크가 만료·폐기됐거나 저장 초안이 변경되었습니다. 관리자에서 새 링크를 발급해 주세요.'}</p><button type="button" className="outline" onClick={exitPreview}>{locale === 'en' ? 'Exit preview' : '미리보기 종료'}</button></main></>
  if (previewMode && websiteLoading) return <>{renderHeader(true)}<main className="content-section" aria-live="polite">{locale === 'en' ? 'Loading draft…' : '저장 초안을 불러오는 중…'}</main></>
  const bookingRoute = resolveCustomerRoute(pathname)
  if (previewMode && (bookingRoute?.kind === 'booking-results' || bookingRoute?.kind === 'booking-checkout' || bookingRoute?.kind === 'booking-complete' || bookingRoute?.kind === 'reservation-payment-result')) {
    return <CustomerBookingShell step="search" locale={bookingRoute.locale ?? 'ko'}><section className="booking-route-state"><h1>{bookingRoute?.locale === 'en' ? 'Booking is unavailable in preview' : '미리보기에서는 예약을 진행할 수 없습니다'}</h1><p>{bookingRoute?.locale === 'en' ? 'Use room search on the published website.' : '발행된 페이지에서 예약 검색을 이용해 주세요.'}</p></section></CustomerBookingShell>
  }
  if (bookingRoute?.kind === 'booking-results') {
    const criteria = parseBookingCriteria(window.location.search)
    if (criteria) return <BookingSearchPage criteria={criteria} locale={bookingRoute.locale ?? 'ko'} />
    return <CustomerBookingShell step="search" locale={bookingRoute.locale ?? 'ko'}><section className="booking-route-state"><h1>{bookingRoute?.locale === 'en' ? 'Check your search criteria' : '검색 조건을 확인해 주세요'}</h1><p>{bookingRoute?.locale === 'en' ? 'Enter a valid hotel, dates, guests and room count, then search again.' : '유효한 지점, 날짜, 인원과 객실 수를 입력한 뒤 다시 검색해 주세요.'}</p><a className="primary" href={bookingRoute.locale === 'en' ? '/en' : '/#booking'}>{bookingRoute?.locale === 'en' ? 'Back to room search' : '예약 검색으로 돌아가기'}</a></section></CustomerBookingShell>
  }
  if (bookingRoute?.kind === 'booking-checkout') return <BookingCheckoutPage locale={bookingRoute.locale ?? 'ko'} />
  if (bookingRoute?.kind === 'booking-complete') return <BookingResultPage locale={bookingRoute.locale ?? 'ko'} returned={initialTossReturn} />
  if (bookingRoute?.kind === 'reservation-payment-result') return <BookingResultPage locale={bookingRoute.locale ?? 'ko'} reservationId={bookingRoute.reservationId} returned={initialTossReturn} />
  if (bookingRoute?.kind === 'reservations') return <ReservationManagementPage locale={bookingRoute.locale ?? 'ko'} />
  if (bookingRoute?.kind === 'reservation-detail') return <ReservationManagementPage reservationId={bookingRoute.reservationId} locale={bookingRoute.locale ?? 'ko'} />
  if (locale === 'en') {
    const route = resolveCustomerRoute(pathname)
    const englishFooter = <footer><div className="brand"><span>STAY</span> HANEUL</div><p>Fictional hotel chain · Portfolio demo</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer>
    if (pageUnavailable) return <>{renderHeader(true)}<main id="top"><section className="content-section"><h1>English content is not available.</h1><p>This page has not been published in English, or its address has changed.</p><a className="text-link dark" href="/en">English home <ArrowRight size={18} /></a><p><a href={pathname.slice(3) || '/'} lang="ko">View the Korean website</a></p></section></main>{englishFooter}</>
    if (route?.kind === 'collection') return <>{renderHeader(true)}<ContentCollectionPage key={pathname} locale="en" contentKind={route.contentKind} hotelSlug={route.hotelSlug} />{englishFooter}</>
    if (websiteLoading || (!contentPage && !homeContentPage && !publishedDestination)) return <>{renderHeader(true)}<main className="content-section" aria-live="polite"><p>Loading English content…</p></main></>
    const page = contentPage ?? homeContentPage
    if (page) return <>{renderHeader(true)}<main id="top"><ContentPage page={page} previewMode={previewMode} locale="en" onBookingIntent={applyContentBookingIntent} /></main>{englishFooter}</>
    if (publishedDestination) return <>{renderHeader(true)}<main id="top">
      <section className="hero"><ResponsiveCmsImage src={publishedDestination.heroImage} imageVariants={publishedDestination.heroVariants} sizes="100vw" alt={publishedDestination.heroAlt} /><div className="hero-shade" /><div className="hero-copy"><p>{publishedDestination.eyebrow}</p><h1>{publishedDestination.title}</h1><p>{publishedDestination.description}</p><a href="/#booking" className="text-link">Book a room (Korean) <ArrowRight size={18} /></a></div></section>
      <section className="content-section editorial"><h2>Experiences</h2><div className="experience-grid">{publishedDestination.experiences.map((item, index) => <article className={`experience-card experience-${index}`} key={index}><p>{item.category}</p><h3>{item.title}</h3><span>{item.description}</span></article>)}</div></section>
      <section className="offers-band"><div className="content-section"><h2>Offers</h2><div className="story-offers">{publishedDestination.offers.map((item, index) => <article key={index}><h3>{item.title}</h3><p>{item.detail}</p><dl><div><dt>Booking period</dt><dd>{item.bookingPeriod}</dd></div><div><dt>Stay period</dt><dd>{item.stayPeriod}</dd></div></dl></article>)}</div></div></section>
      <section className="arrival-section"><div><h2>Arrival guide</h2><p>{publishedDestination.arrival.highlight}</p></div><dl><div><dt>Address</dt><dd>{publishedDestination.arrival.address}</dd></div><div><dt>Check-in / Check-out</dt><dd>{publishedDestination.arrival.checkInOut}</dd></div></dl></section>
    </main>{englishFooter}</>
  }
  if (pageUnavailable) return <>{renderHeader(true)}<main id="top"><section className="content-section"><p className="section-kicker">PAGE NOT FOUND</p><h1>요청한 페이지를 찾을 수 없습니다.</h1><p>발행되지 않았거나 주소가 변경되었습니다.</p><a className="text-link dark" href="/">메인으로 돌아가기 <ArrowRight size={18} /></a></section></main></>
  const activeRoute = resolveCustomerRoute(pathname)
  if (activeRoute?.kind === 'collection') return <>{renderHeader(true)}<ContentCollectionPage contentKind={activeRoute.contentKind} hotelSlug={activeRoute.hotelSlug} /><footer><div className="brand"><span>STAY</span> HANEUL</div><p>가상의 호텔 체인 예약·운영 플랫폼 포트폴리오</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer></>
  if (contentPage) return <>{renderHeader(true)}<main id="top"><ContentPage page={contentPage} previewMode={previewMode} onBookingIntent={applyContentBookingIntent} /></main><footer><div className="brand"><span>STAY</span> HANEUL</div><p>가상의 호텔 체인 예약·운영 플랫폼 포트폴리오</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer></>
  return <>{renderHeader(false)}
    <main id="top">{homeContentPage ? <ContentPageHero page={homeContentPage} previewMode={previewMode} headingId="hero-title" /> : <section className="hero" aria-labelledby="hero-title"><ResponsiveCmsImage src={destination.heroImage} imageVariants={destination.heroVariants} sizes="100vw" alt={destination.heroAlt} /><div className="hero-shade" /><div className="hero-copy" key={destination.title}><p className="eyebrow">{destination.eyebrow}</p><h1 id="hero-title">{destination.title.split('\n').map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</h1><p>{destination.description}</p><a href="#booking" className="text-link">객실 예약하기 <ArrowRight size={18} /></a></div><div className="fiction">PORTFOLIO DEMO · 가상의 호텔입니다</div></section>}
      <section id="booking" className="booking-shell" aria-labelledby="booking-title"><div className="booking-heading"><div><p className="section-kicker">BOOK YOUR STAY</p><h2 id="booking-title">여정을 예약하세요</h2></div><ConciergePanel previewMode={previewMode} criteria={{ region: selectedHotel?.region, checkIn, checkOut, adults, children, rooms }} onApply={applyConciergeCriteria} /></div><form onSubmit={search}><fieldset className="search-grid" disabled={previewMode} aria-label="예약 검색 조건"><HotelSelector hotels={hotels} value={hotelId} onChange={selectHotelForStay} /><StayDatePicker checkIn={checkIn} checkOut={checkOut} onChange={(nextCheckIn, nextCheckOut) => { if (previewMode) return; setCheckIn(nextCheckIn); setCheckOut(nextCheckOut) }} /><GuestSelector adults={adults} children={children} rooms={rooms} onChange={({ adults: nextAdults, children: nextChildren, rooms: nextRooms }) => { if (previewMode) return; setAdults(nextAdults); setChildren(nextChildren); setRooms(nextRooms) }} /><button className="primary search-button" disabled={previewMode || !hotelId}>객실 검색 <ArrowRight size={18} /></button></fieldset></form></section>
      {homeContentPage && <ContentPageAfterHero page={homeContentPage} previewMode={previewMode} />}
      <section id="experiences" className="content-section editorial"><div className="section-head"><div><p className="section-kicker">A STAY TO REMEMBER</p><h2>{selectedHotel?.region ?? '속초'}에서 만나는 세 가지 장면</h2></div><p>머무는 시간에 따라<br />장소는 더 깊어집니다.</p></div><div className="experience-grid">{destination.experiences.map((item, index) => <article className={`experience-card experience-${index}`} key={item.title}><p>{item.category}</p><h3>{item.title}</h3><span>{item.description}</span><a href="#booking">{item.title}로 예약하기 <ArrowRight size={15} /></a></article>)}</div></section>
      <section id="offers" className="offers-band"><div className="content-section"><div className="section-head"><div><p className="section-kicker">SPECIAL OFFERS</p><h2>머무름을 위한 제안</h2></div><a href="#booking" className="text-link dark">전체 객실 보기 <ArrowRight size={17} /></a></div><div className="story-offers">{destination.offers.map((offer, index) => <article className="story-offer" key={offer.title}><span>0{index + 1}</span><h3>{offer.title}</h3><p>{offer.detail}</p><dl><div><dt>예약 기간</dt><dd>{offer.bookingPeriod}</dd></div><div><dt>투숙 기간</dt><dd>{offer.stayPeriod}</dd></div></dl><a href="#booking">예약하기 <ArrowRight size={16} /></a></article>)}</div></div></section>

      <section className="arrival-section"><div><p className="section-kicker">ARRIVAL GUIDE</p><h2>{selectedHotel?.region ?? '속초'}에 도착하는 시간</h2><p>{destination.arrival.highlight}</p></div><dl><div><dt>주소</dt><dd>{destination.arrival.address}</dd></div><div><dt>체크인 / 아웃</dt><dd>{destination.arrival.checkInOut}</dd></div></dl><a href="#booking" className="outline">객실 예약하기 <ArrowRight size={17} /></a></section>
      <section className="values"><div><Bot /><p>COMING NEXT</p><h2>말로 찾는<br />나만의 스테이</h2></div><p>“다음 주 토요일, 아이와 함께 조식이 포함된 속초 호텔을 찾아줘.”<br />LangGraph 기반 AI 컨시어지가 실제 재고와 요금만 확인해 추천합니다.</p></section></main><footer><div className="brand"><span>STAY</span> HANEUL</div><p>가상의 호텔 체인 예약·운영 플랫폼 포트폴리오</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer></>

}
