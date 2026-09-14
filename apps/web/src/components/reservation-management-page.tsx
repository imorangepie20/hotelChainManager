import { useEffect, useRef, useState } from 'react'
import { CalendarDays, CreditCard, ReceiptText, X } from 'lucide-react'

import { ApiFailure, api, type CancellationPreview, type Reservation, type ReservationChangePayment } from '../lib/api'
import { BookingSessionStore, type ReservationAccess } from '../lib/booking-session'
import { canContinueChangePayment, changeStatusLabel, reservationStatusLabel } from '../lib/reservation-management-state'

type Props = { reservationId?: string; locale?: 'ko' | 'en' }
type LoadedReservation = { access: ReservationAccess; reservation: Reservation }

const money = new Intl.NumberFormat('ko-KR')
const date = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
const dateTime = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })

function failureMessage(error: unknown, fallback: string) {
  return error instanceof ApiFailure ? error.message : fallback
}

function detailPath(reservationId: string, locale: 'ko' | 'en') {
  return `${locale === 'en' ? '/en' : ''}/reservations/${reservationId}`
}

function reservationName(reservation: Reservation) {
  return reservation.roomTypeName ?? `예약 ${reservation.id.slice(-8)}`
}

export function ReservationManagementPage({ reservationId, locale = 'ko' }: Props) {
  const session = useRef(new BookingSessionStore())
  const cancelButton = useRef<HTMLButtonElement>(null)
  const dialog = useRef<HTMLDivElement>(null)
  const [loading, setLoading] = useState(true)
  const [entries, setEntries] = useState<LoadedReservation[]>([])
  const [preview, setPreview] = useState<CancellationPreview | null>(null)
  const [change, setChange] = useState<ReservationChangePayment | null>(null)
  const [error, setError] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const access = reservationId
    ? session.current.listReservationAccess().find(item => item.reservationId === reservationId) ?? null
    : null
  const reservation = reservationId ? entries.find(item => item.access.reservationId === reservationId)?.reservation ?? null : null

  useEffect(() => {
    let active = true
    const accesses = reservationId ? (access ? [access] : []) : session.current.listReservationAccess()
    Promise.all(accesses.map(async item => {
      try { return { access: item, reservation: await api.getReservation(item.reservationId, item.managementToken) } }
      catch { return null }
    })).then(results => {
      if (!active) return
      setEntries(results.filter((item): item is LoadedReservation => item !== null))
      setLoading(false)
    })
    return () => { active = false }
  }, [reservationId])

  useEffect(() => {
    if (!reservation || !access || reservation.status !== 'CONFIRMED') { setPreview(null); return }
    let active = true
    api.cancellationPreview(reservation.id, access.managementToken)
      .then(result => { if (active) setPreview(result) })
      .catch(() => { if (active) setPreview(null) })
    return () => { active = false }
  }, [reservation?.id, reservation?.status, access?.managementToken])

  useEffect(() => {
    if (!reservation) { setChange(null); return }
    let active = true
    api.reservationChangePayment().then(result => {
      if (active && reservation.id.endsWith(result.reservationNumberSuffix)) setChange(result)
    }).catch(() => { if (active) setChange(null) })
    return () => { active = false }
  }, [reservation?.id])

  useEffect(() => {
    if (!dialogOpen) return
    dialog.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') closeDialog() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [dialogOpen])

  function closeDialog() {
    setDialogOpen(false)
    window.setTimeout(() => cancelButton.current?.focus(), 0)
  }

  async function cancelReservation() {
    if (!reservation || !access || !preview?.cancellable) return
    setCancelling(true); setError('')
    try {
      await api.cancel(reservation.id, access.managementToken, crypto.randomUUID())
      const next = await api.getReservation(reservation.id, access.managementToken)
      setEntries(current => current.map(item => item.reservation.id === next.id ? { ...item, reservation: next } : item))
      setPreview(null)
      closeDialog()
    } catch (cause) {
      setError(failureMessage(cause, '예약 취소를 완료하지 못했습니다. 현재 예약 상태를 다시 확인해 주세요.'))
    } finally {
      setCancelling(false)
    }
  }

  if (loading) return <main className="reservation-management-shell"><p className="reservation-management-state" role="status">예약 정보를 안전하게 불러오는 중입니다.</p></main>

  if (!reservationId) return <main className="reservation-management-shell" aria-labelledby="reservation-management-title">
    <header className="reservation-management-header"><a className="brand" href={locale === 'en' ? '/en' : '/'}><span>STAY</span> HANEUL</a><a className="manage-link" href={locale === 'en' ? '/en' : '/'}>홈으로</a></header>
    <section className="reservation-management-list">
      <p className="section-kicker">MY RESERVATIONS</p><h1 id="reservation-management-title">내 예약</h1>
      {entries.length === 0 ? <div className="reservation-management-empty"><h2>이 브라우저에서 확인할 예약이 없습니다</h2><p>예약을 진행한 같은 브라우저에서 다시 확인해 주세요. 다른 기기에서는 예약 완료 이메일의 보안 링크를 사용해 주세요.</p><a className="primary" href={locale === 'en' ? '/en' : '/'}>예약 검색하기</a></div> : <div className="reservation-card-list">{entries.map(({ reservation: item }) => <article className="reservation-card" key={item.id}>
        <div><p className="reservation-status">{reservationStatusLabel(item.status)}</p><h2>{reservationName(item)}</h2><p>{item.checkIn} – {item.checkOut} · 객실 {item.rooms}개</p></div>
        <a className="outline" href={detailPath(item.id, locale)} aria-label={`${reservationName(item)} 예약 상세`}>예약 상세 보기</a>
      </article>)}</div>}
    </section>
  </main>

  if (!access) return <main className="reservation-management-shell"><section className="reservation-management-empty"><h1>확인할 예약 정보가 없습니다</h1><p>개인정보 보호를 위해 이 브라우저에서 만든 예약만 표시합니다.</p><a className="primary" href={locale === 'en' ? '/en/reservations' : '/reservations'}>내 예약으로</a></section></main>
  if (!reservation) return <main className="reservation-management-shell"><section className="reservation-management-empty"><h1>예약 정보를 불러올 수 없습니다</h1><p>예약 정보가 만료되었거나 더 이상 이 브라우저에서 확인할 수 없습니다.</p><a className="primary" href={locale === 'en' ? '/en/reservations' : '/reservations'}>내 예약으로</a></section></main>

  const changeCanContinue = change && canContinueChangePayment(change.status, reservation.id, change.reservationNumberSuffix)
  return <main className="reservation-management-shell" aria-labelledby="reservation-detail-title">
    <header className="reservation-management-header"><a className="brand" href={locale === 'en' ? '/en' : '/'}><span>STAY</span> HANEUL</a><a className="manage-link" href={locale === 'en' ? '/en/reservations' : '/reservations'}>내 예약</a></header>
    <section className="reservation-detail">
      <a className="reservation-back" href={locale === 'en' ? '/en/reservations' : '/reservations'}>← 내 예약</a>
      <p className="reservation-status">{reservationStatusLabel(reservation.status)}</p><h1 id="reservation-detail-title">{reservationName(reservation)}</h1>
      <dl className="reservation-detail-list"><div><dt><CalendarDays size={17} aria-hidden="true" /> 숙박 일정</dt><dd>{date.format(new Date(`${reservation.checkIn}T00:00:00`))} – {date.format(new Date(`${reservation.checkOut}T00:00:00`))}</dd></div><div><dt>객실 수</dt><dd>{reservation.rooms}개</dd></div><div><dt><ReceiptText size={17} aria-hidden="true" /> 결제 상태</dt><dd>{reservation.paymentStatus ?? '서버 확인 필요'}</dd></div><div><dt>결제 금액</dt><dd>{money.format(reservation.total)}원</dd></div></dl>
      <section className="reservation-policy"><h2>취소 정책</h2><p>{reservation.cancellationPolicy || '서버에서 저장한 취소 정책을 확인합니다.'}</p>{preview && <p>{preview.cancellable ? `취소 가능 시각: ${dateTime.format(new Date(preview.cutoffAt))} · 예상 환불액 ${money.format(preview.refundAmount)}원` : (preview.unavailableReason ?? '현재 예약은 취소할 수 없습니다.')}</p>}</section>
      {change && <section className="reservation-change-card" aria-labelledby="reservation-change-title"><p className="section-kicker">RESERVATION CHANGE</p><h2 id="reservation-change-title">{changeStatusLabel(change.status)}</h2><p>{date.format(new Date(`${change.checkIn}T00:00:00`))} – {date.format(new Date(`${change.checkOut}T00:00:00`))} · {change.roomTypeName}</p><p>차액 {money.format(change.additionalAmountKrw)}원 · {dateTime.format(new Date(change.expiresAt))}까지</p>{changeCanContinue && <a className="primary" href="/reservation-change-payment"><CreditCard size={18} aria-hidden="true" />추가 결제 계속하기</a>}</section>}
      {error && <p className="reservation-management-error" role="alert">{error}</p>}
      {preview?.cancellable && <button className="outline reservation-cancel" type="button" ref={cancelButton} onClick={() => setDialogOpen(true)}>예약 취소</button>}
    </section>
    {dialogOpen && preview && <div className="reservation-dialog-backdrop" role="presentation"><div className="reservation-dialog" role="dialog" aria-modal="true" aria-labelledby="cancel-dialog-title" tabIndex={-1} ref={dialog}><button className="reservation-dialog-close" type="button" aria-label="닫기" onClick={closeDialog}><X size={20} /></button><h2 id="cancel-dialog-title">예약을 취소할까요?</h2><p>취소 정책에 따라 예상 환불액은 {money.format(preview.refundAmount)}원입니다. 취소를 실행하면 서버에서 최신 상태와 환불 가능 금액을 다시 확인합니다.</p><div className="reservation-dialog-actions"><button className="outline" type="button" onClick={closeDialog} disabled={cancelling}>돌아가기</button><button className="primary" type="button" onClick={() => void cancelReservation()} disabled={cancelling}>{cancelling ? '예약 취소를 처리하는 중…' : '예약 취소 확정'}</button></div></div></div>}
  </main>
}
