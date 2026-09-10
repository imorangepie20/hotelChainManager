import { FormEvent, useEffect, useMemo, useState } from 'react'
import { ArrowRight, BedDouble, Bot, CalendarDays, Check, Coffee, MapPin, Menu, ShieldCheck, Sparkles, Users, Waves, X } from 'lucide-react'
import { ApiFailure, Hotel, Offer, Reservation, api, createManagementToken } from './lib/api'

const money = new Intl.NumberFormat('ko-KR')
const iso = (date: Date) => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
const addDays = (count: number) => { const date = new Date(); date.setDate(date.getDate() + count); return iso(date) }

export default function App() {
  const [hotels, setHotels] = useState<Hotel[]>([])
  const [hotelId, setHotelId] = useState('')
  const [checkIn, setCheckIn] = useState(addDays(7))
  const [checkOut, setCheckOut] = useState(addDays(9))
  const [adults, setAdults] = useState(2)
  const [offers, setOffers] = useState<Offer[]>([])
  const [selected, setSelected] = useState<Offer | null>(null)
  const [guest, setGuest] = useState({ name: '', email: '' })
  const [reservation, setReservation] = useState<Reservation | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  useEffect(() => { api.hotels().then(data => { setHotels(data); setHotelId(data.find(h => h.region === '속초')?.id ?? data[0]?.id ?? '') }).catch(showError) }, [])
  useEffect(() => {
    const id = sessionStorage.getItem('latestReservation')
    const token = id && sessionStorage.getItem(`reservation:${id}`)
    if (id && token) api.getReservation(id, token).then(setReservation).catch(showError)
  }, [])
  useEffect(() => { setOffers([]); setSelected(null) }, [hotelId, checkIn, checkOut, adults])
  const selectedHotel = useMemo(() => hotels.find(h => h.id === hotelId), [hotels, hotelId])

  function showError(reason: unknown) {
    setError(reason instanceof ApiFailure ? reason.message : '서버에 연결할 수 없습니다.')
    setBusy(false)
  }

  async function search(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setNotice(''); setSelected(null)
    try {
      const params = new URLSearchParams({ hotelId, checkIn, checkOut, adults: String(adults), children: '0', rooms: '1' })
      const result = await api.availability(params); setOffers(result.offers)
      if (!result.offers.length) setNotice('선택한 날짜에는 예약 가능한 객실이 없습니다.')
    } catch (reason) { showError(reason) } finally { setBusy(false) }
  }

  async function reserve(event: FormEvent) {
    event.preventDefault(); if (!selected) return
    setBusy(true); setError('')
    const body = { roomTypeId: selected.roomTypeId, ratePlanId: selected.ratePlanId,
      checkIn, checkOut, adults, children: 0, rooms: 1, expectedTotal: selected.total, guest }
    const saved = sessionStorage.getItem('pendingReservation')
    const previous = saved ? JSON.parse(saved) : null
    const pending = previous && JSON.stringify(previous.body) === JSON.stringify(body)
      ? previous
      : { token: createManagementToken(), key: crypto.randomUUID(), body }
    const { token, key } = pending
    sessionStorage.setItem('pendingReservation', JSON.stringify(pending))
    try {
      const created = await api.reserve(pending.body, token, key)
      sessionStorage.setItem(`reservation:${created.id}`, token); sessionStorage.setItem('latestReservation', created.id)
      sessionStorage.removeItem('pendingReservation')
      setReservation(created); setNotice('객실이 10분 동안 확보되었습니다. 테스트 결제를 진행해 주세요.')
    } catch (reason) {
      if (reason instanceof ApiFailure && reason.status < 500) sessionStorage.removeItem('pendingReservation')
      if (reason instanceof ApiFailure && ['PRICE_CHANGED', 'SOLD_OUT'].includes(reason.code)) { setSelected(null); setOffers([]) }
      showError(reason)
    } finally { setBusy(false) }
  }

  async function pay(outcome: 'SUCCESS' | 'FAILURE') {
    if (!reservation) return; setBusy(true); setError('')
    try {
      const token = sessionStorage.getItem(`reservation:${reservation.id}`)!;
      const result = await api.pay(reservation.id, token, crypto.randomUUID(), outcome)
      setReservation(await api.getReservation(reservation.id, token))
      setNotice(result.paymentStatus === 'FAILED'
        ? '테스트 결제가 실패했습니다. 객실 확보 시간 안에 다시 시도할 수 있습니다.'
        : '테스트 결제가 완료되어 예약이 확정되었습니다.')
    } catch (reason) {
      if (reason instanceof ApiFailure && reason.code === 'HOLD_EXPIRED') {
        const token = sessionStorage.getItem(`reservation:${reservation.id}`)!
        setReservation(await api.getReservation(reservation.id, token).catch(() => reservation))
        setOffers([]); setSelected(null); setNotice('객실 확보 시간이 만료되었습니다. 날짜와 객실을 다시 검색해 주세요.')
      } else showError(reason)
    } finally { setBusy(false) }
  }

  async function cancel() {
    if (!reservation) return; setBusy(true); setError('')
    try {
      const token = sessionStorage.getItem(`reservation:${reservation.id}`)!;
      const result = await api.cancel(reservation.id, token, crypto.randomUUID())
      setReservation(await api.getReservation(reservation.id, token));
      setNotice(`예약이 취소되었습니다. 테스트 환불액은 ₩${money.format(result.refundAmount)}입니다.`)
    } catch (reason) { showError(reason) } finally { setBusy(false) }
  }

  return <>
    <header className="topbar"><a className="brand" href="#top"><span>STAY</span> HANEUL</a>
      <nav className={mobileMenuOpen ? 'open' : ''} aria-label="주요 메뉴"><a onClick={()=>setMobileMenuOpen(false)} href="#stays">호텔</a><a onClick={()=>setMobileMenuOpen(false)} href="#story">브랜드 스토리</a><a onClick={()=>setMobileMenuOpen(false)} href="#booking">예약</a></nav>
      <div className="header-actions"><span className="lang" aria-label="현재 언어 한국어">KO <span>/ EN</span></span><button className="menu" aria-label="메뉴" aria-expanded={mobileMenuOpen} onClick={()=>setMobileMenuOpen(open => !open)}>{mobileMenuOpen ? <X size={20}/> : <Menu size={20}/>}</button></div>
    </header>

    <main id="top">
      <section className="hero">
        <img src="/images/sokcho-coast-hero.png" alt="동해와 설악산을 바라보는 가상의 속초 해안 호텔" />
        <div className="hero-shade"/><div className="hero-copy"><p className="eyebrow">SOKCHO · SEORAK · JEJU</p>
          <h1>머무는 동안,<br/>자연과 가까워지는 곳</h1><p>바다와 산, 섬의 고요를 담은 세 곳의 스테이.</p>
          <a href="#booking" className="text-link">나만의 여정 찾기 <ArrowRight size={18}/></a></div>
        <div className="fiction">PORTFOLIO DEMO · 가상의 호텔입니다</div>
      </section>

      <section id="booking" className="booking-shell" aria-labelledby="booking-title">
        <div className="booking-heading"><div><p className="section-kicker">BOOK YOUR STAY</p><h2 id="booking-title">어디로 떠나시나요?</h2></div>
          <button className="ai-button"><Sparkles size={17}/> AI 예약 도우미 <span>준비 중</span></button></div>
        <form className="search-grid" onSubmit={search}>
          <label><span><MapPin size={16}/> 지점</span><select value={hotelId} onChange={e=>setHotelId(e.target.value)}>{hotels.map(h=><option key={h.id} value={h.id}>{h.region} · {h.name}</option>)}</select></label>
          <label><span><CalendarDays size={16}/> 체크인</span><input type="date" value={checkIn} min={iso(new Date())} onChange={e=>setCheckIn(e.target.value)} required/></label>
          <label><span><CalendarDays size={16}/> 체크아웃</span><input type="date" value={checkOut} min={checkIn} onChange={e=>setCheckOut(e.target.value)} required/></label>
          <label><span><Users size={16}/> 인원</span><select value={adults} onChange={e=>setAdults(Number(e.target.value))}><option value={1}>성인 1명</option><option value={2}>성인 2명</option><option value={3}>성인 3명</option><option value={4}>성인 4명</option></select></label>
          <button className="primary search-button" disabled={busy || !hotelId}>{busy ? '확인 중…' : '객실 검색'} <ArrowRight size={18}/></button>
        </form>
        {(error || notice) && <div role="status" className={error ? 'message error' : 'message'}>{error || notice}<button aria-label="알림 닫기" onClick={()=>{setError('');setNotice('')}}><X size={16}/></button></div>}
      </section>

      <section id="stays" className="content-section">
        <div className="section-head"><div><p className="section-kicker">ROOMS & OFFERS</p><h2>{selectedHotel?.region ?? '속초'}에서의 하룻밤</h2></div><p>표시 요금은 전체 숙박 기간 기준이며<br/>세금이 포함된 테스트 금액입니다.</p></div>
        <div className="offers">
          {offers.map((offer, index)=><article className="offer-card" key={offer.ratePlanId}>
            <div className={`room-visual visual-${index%3}`}><span>{String(index+1).padStart(2,'0')}</span><Waves size={42}/></div>
            <div className="offer-body"><div className="offer-top"><div><p>{offer.breakfastIncluded ? 'BREAKFAST INCLUDED' : 'ROOM ONLY'}</p><h3>{offer.roomTypeName}</h3></div><span className="remaining">잔여 {offer.remaining}실</span></div>
              <div className="amenities"><span><BedDouble/> 성인 {adults}명 기준</span>{offer.breakfastIncluded&&<span><Coffee/> 조식 포함</span>}<span><ShieldCheck/> 유연 취소</span></div>
              <div className="price-row"><div><small>{offer.nightlyPrices.length}박 총액</small><strong>₩{money.format(offer.total)}</strong></div><button disabled={busy} onClick={()=>{setSelected(offer); setReservation(null)}}>선택 <ArrowRight size={17}/></button></div>
            </div></article>)}
          {!offers.length && <div className="empty-state"><CalendarDays/><h3>날짜를 선택해 객실을 찾아보세요</h3><p>속초 지점은 현재 날짜부터 90일 동안 예약할 수 있습니다.</p></div>}
        </div>
      </section>

      {selected && !reservation && <section className="reservation-panel"><div><p className="section-kicker">GUEST DETAILS</p><h2>{selected.roomTypeName} 예약</h2><p>{checkIn} — {checkOut} · 성인 {adults}명</p></div>
        <form onSubmit={reserve}><label>예약자 이름<input value={guest.name} onChange={e=>setGuest({...guest,name:e.target.value})} required placeholder="홍길동"/></label><label>이메일<input type="email" value={guest.email} onChange={e=>setGuest({...guest,email:e.target.value})} required placeholder="guest@example.com"/></label>
          <div className="reservation-total"><span>결제 예정 금액</span><strong>₩{money.format(selected.total)}</strong></div><button className="primary" disabled={busy}>{busy?'확보 중…':'객실 확보하기'} <ArrowRight size={18}/></button></form></section>}

      {reservation && <section className="reservation-panel confirmed"><div className="status-icon"><Check/></div><div><p className="section-kicker">RESERVATION {reservation.status}</p><h2>{reservation.status==='CONFIRMED'?'예약이 확정되었습니다':reservation.status==='CANCELLED'?'예약이 취소되었습니다':reservation.status==='EXPIRED'?'예약 확보 시간이 만료되었습니다':'객실을 확보했습니다'}</h2><p>예약 번호 {reservation.id}</p><p>{reservation.checkIn} — {reservation.checkOut} · {reservation.guest.name}</p></div>
        <div className="manage-actions"><strong>₩{money.format(reservation.total)}</strong>{reservation.status==='PENDING_PAYMENT'&&<><button className="primary" onClick={()=>pay('SUCCESS')} disabled={busy}>테스트 결제</button><button className="test-failure" onClick={()=>pay('FAILURE')} disabled={busy}>결제 실패 시험</button></>}{reservation.status==='CONFIRMED'&&<button className="outline" onClick={cancel} disabled={busy}>예약 취소</button>}{reservation.status==='EXPIRED'&&<a className="outline" href="#booking">다시 검색</a>}</div>
        <p className="session-note">예약 관리 정보는 현재 브라우저 세션에만 저장됩니다. 창을 닫으면 복구할 수 없습니다.</p></section>}

      <section id="story" className="values"><div><Bot/><p>COMING NEXT</p><h2>말로 찾는<br/>나만의 스테이</h2></div><p>“다음 주 토요일, 아이와 함께 조식이 포함된 속초 호텔을 찾아줘.”<br/>LangGraph 기반 AI 컨시어지가 실제 재고와 요금만 확인해 추천합니다.</p></section>
    </main>
    <footer><div className="brand"><span>STAY</span> HANEUL</div><p>가상의 호텔 체인 예약·운영 플랫폼 포트폴리오</p><p>© 2026 HOTEL CHAIN PROJECT</p></footer>
  </>
}
