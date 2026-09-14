import { useEffect, useRef, useState } from 'react'
import { CalendarDays, CreditCard, ReceiptText, X } from 'lucide-react'
import { ApiFailure, api, type CancellationPreview, type CustomerChangeEligibility, type Reservation, type ReservationChangeSummary } from '../lib/api'
import { BookingSessionStore, type ReservationAccess } from '../lib/booking-session'
import { canContinueChangePayment, changeStatusLabel, formatReservationDateTime, reservationStatusLabel } from '../lib/reservation-management-state'

type Props = { reservationId?: string; locale?: 'ko' | 'en' }
type LoadedReservation = { access: ReservationAccess; reservation: Reservation }

const words = {
  ko: { home: '홈으로', title: '내 예약', loading: '예약 정보를 안전하게 불러오는 중입니다.', empty: '이 브라우저에서 확인할 예약이 없습니다', emptyHelp: '예약을 진행한 같은 브라우저에서 다시 확인해 주세요. 다른 기기에서의 예약 복구와 이메일 보안 링크는 아직 지원하지 않습니다. 호텔에 문의해 주세요.', search: '예약 검색하기', missing: '확인할 예약 정보가 없습니다', missingHelp: '개인정보 보호를 위해 이 브라우저에서 만든 예약만 표시합니다.', unavailable: '예약 정보를 불러올 수 없습니다', unavailableHelp: '예약 정보가 만료되었거나 더 이상 이 브라우저에서 확인할 수 없습니다.', mine: '내 예약', back: '← 내 예약', schedule: '숙박 일정', rooms: '객실 수', payment: '결제 상태', paymentUnknown: '서버 확인 필요', total: '결제 금액', policy: '취소 정책', policyUnknown: '서버에서 저장한 취소 정책을 확인합니다.', cutoff: '취소 가능 시각', refund: '예상 환불액', change: '예약 변경', difference: '차액', until: '까지', continue: '추가 결제 계속하기', cancel: '예약 취소', cancelTitle: '예약을 취소할까요?', cancelHelp: '취소 정책에 따라 예상 환불액은 {amount}입니다. 취소를 실행하면 서버에서 최신 상태와 환불 가능 금액을 다시 확인합니다.', confirm: '예약 취소 확정', cancelling: '예약 취소를 처리하는 중…', backAction: '돌아가기', close: '닫기', failure: '예약 취소를 완료하지 못했습니다. 현재 예약 상태를 다시 확인해 주세요.', fallback: '예약' },
  en: { home: 'Home', title: 'My reservations', loading: 'Loading your reservation securely.', empty: 'No reservations are available in this browser', emptyHelp: 'Use the same browser that made the reservation. Reservation recovery on another device and email links are not yet available. Please contact the hotel.', search: 'Search rooms', missing: 'No reservation information is available', missingHelp: 'For privacy, only reservations made in this browser are shown.', unavailable: 'We could not load this reservation', unavailableHelp: 'The reservation may have expired or is no longer available in this browser.', mine: 'My reservations', back: '← My reservations', schedule: 'Stay dates', rooms: 'Rooms', payment: 'Payment status', paymentUnknown: 'Check with the server', total: 'Payment total', policy: 'Cancellation policy', policyUnknown: 'The saved cancellation policy is being verified by the server.', cutoff: 'Cancellation deadline', refund: 'Expected refund', change: 'Reservation change', difference: 'Difference', until: 'until', continue: 'Continue additional payment', cancel: 'Cancel reservation', cancelTitle: 'Cancel this reservation?', cancelHelp: 'The expected refund under the cancellation policy is {amount}. The server will check the latest status and refundable amount again before cancellation.', confirm: 'Confirm cancellation', cancelling: 'Cancelling reservation…', backAction: 'Go back', close: 'Close', failure: 'We could not cancel the reservation. Please check the current reservation status again.', fallback: 'Reservation' },
} as const

function route(locale: 'ko' | 'en', suffix = '') { return `${locale === 'en' ? '/en' : ''}${suffix}` || '/' }
function money(amount: number, currency: string, locale: 'ko' | 'en') { return new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'ko-KR', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount) }
function date(value: string, locale: 'ko' | 'en') { return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'ko-KR', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' }).format(new Date(`${value}T12:00:00Z`)) }
function name(reservation: Reservation, locale: 'ko' | 'en') { return reservation.roomTypeName ?? `${words[locale].fallback} ${reservation.id.slice(-8)}` }

export function ReservationManagementPage({ reservationId, locale = 'ko' }: Props) {
  const text = words[locale]
  const session = useRef(new BookingSessionStore())
  const cancelButton = useRef<HTMLButtonElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)
  const dialog = useRef<HTMLDivElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)
  const [loading, setLoading] = useState(true); const [entries, setEntries] = useState<LoadedReservation[]>([])
  const [preview, setPreview] = useState<CancellationPreview | null>(null); const [change, setChange] = useState<ReservationChangeSummary | null>(null)
  const [linkedPayment, setLinkedPayment] = useState(false)
  const [changeEligibility, setChangeEligibility] = useState<CustomerChangeEligibility | null>(null)
  const [cancellationPending, setCancellationPending] = useState(false)
  const [dialogOpen, setDialogOpen] = useState(false); const [cancelling, setCancelling] = useState(false); const [error, setError] = useState('')
  const access = reservationId ? session.current.loadReservationAccess(reservationId) : null
  const reservation = reservationId ? entries.find(item => item.access.reservationId === reservationId)?.reservation ?? null : null

  useEffect(() => { let active = true; const accesses = reservationId ? (access ? [access] : []) : session.current.listReservationAccess(); Promise.all(accesses.map(async item => { try { return { access: item, reservation: await api.getReservation(item.reservationId, item.managementToken) } } catch { return null } })).then(results => { if (active) { setEntries(results.filter((item): item is LoadedReservation => item !== null)); setLoading(false) } }); return () => { active = false } }, [reservationId])
  useEffect(() => { if (!reservation || !access || reservation.status !== 'CONFIRMED') { setPreview(null); return }; setPreview(null); let active = true; api.cancellationPreview(reservation.id, access.managementToken).then(result => { if (active) setPreview(result) }).catch(() => { if (active) setPreview(null) }); return () => { active = false } }, [reservation, access?.managementToken])
  useEffect(() => { if (!reservation) { setChange(null); return }; let active = true; api.reservationChangeSummary(reservation.id, access!.managementToken).then(result => { if (active) setChange(result) }).catch(() => { if (active) setChange(null) }); api.reservationChangePayment().then(result => { if (active) setLinkedPayment(result.reservationId === reservation.id && result.status === 'AWAITING_PAYMENT') }).catch(() => { if (active) setLinkedPayment(false) }); return () => { active = false } }, [reservation?.id])
  useEffect(() => { if (!reservation || !access || reservation.status !== 'CONFIRMED') { setChangeEligibility(null); return }; let active = true; api.changeEligibility(reservation.id, access.managementToken).then(result => { if (active) setChangeEligibility(result) }).catch(() => { if (active) setChangeEligibility(null) }); return () => { active = false } }, [reservation?.id, reservation?.status, access?.managementToken, change?.status])

  useEffect(() => {
    if (!change || !access || ['COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'].includes(change.status)) return
    let active = true
    let inFlight = false
    const timer = window.setInterval(async () => {
      if (inFlight) return
      inFlight = true
      try {
        const next = await api.reservationChangeSummary(access.reservationId, access.managementToken)
        if (!active) return
        if (next?.status === 'COMPLETED') {
          const updated = await api.getReservation(access.reservationId, access.managementToken)
          if (!active) return
          setPreview(null)
          setEntries(current => current.map(item => item.reservation.id === updated.id ? { ...item, reservation: updated } : item))
        }
        // Publish the terminal status only after the refreshed reservation is ready.
        if (active) setChange(next)
      } catch { /* Keep the last server-confirmed summary until a successful refresh. */ }
      finally { inFlight = false }
    }, 3000)
    return () => { active = false; window.clearInterval(timer) }
  }, [change?.status, access?.reservationId, access?.managementToken])

  function closeDialog() { setDialogOpen(false); setError(''); window.setTimeout(() => cancelButton.current?.focus(), 0) }
  useEffect(() => { if (!dialogOpen) return; closeButton.current?.focus(); const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') { closeDialog(); return }; if (event.key !== 'Tab') return; const controls = dialog.current?.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'); if (!controls?.length) return; const first = controls[0]!; const last = controls[controls.length - 1]!; if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() } }; window.addEventListener('keydown', onKeyDown); return () => window.removeEventListener('keydown', onKeyDown) }, [dialogOpen])

  async function cancelReservation() { if (!reservation || !access || !preview?.cancellable) return; setCancelling(true); setError(''); try { const result = await api.cancel(reservation.id, access.managementToken, crypto.randomUUID()); setCancellationPending(result.status === 'CANCELLATION_PENDING' || result.status === 'CANCELLATION_FAILED'); const next = await api.getReservation(reservation.id, access.managementToken); setEntries(current => current.map(item => item.reservation.id === next.id ? { ...item, reservation: next } : item)); setPreview(null); closeDialog() } catch (cause) { setError(cause instanceof ApiFailure ? cause.message : text.failure); window.requestAnimationFrame(() => errorRef.current?.focus()) } finally { setCancelling(false) } }

  async function cancelCustomerChange() { if (!reservation || !access || !change || change.requestOrigin !== 'CUSTOMER') return; setCancelling(true); setError(''); try { await api.cancelChange(reservation.id, change.requestId, access.managementToken, crypto.randomUUID()); setChange(null) } catch (cause) { setError(cause instanceof ApiFailure ? cause.message : text.failure) } finally { setCancelling(false) } }

  async function refreshCancellation() {
    if (!access) return
    setCancelling(true)
    try {
      const updated = await api.getReservation(access.reservationId, access.managementToken)
      setEntries(current => current.map(item => item.reservation.id === updated.id ? { ...item, reservation: updated } : item))
      if (updated.status === 'CANCELLED') setCancellationPending(false)
    } finally { setCancelling(false) }
  }

  if (loading) return <main className="reservation-management-shell"><p className="reservation-management-state" role="status">{text.loading}</p></main>
  if (!reservationId) return <main className="reservation-management-shell" aria-labelledby="reservation-management-title"><header className="reservation-management-header"><a className="brand" href={route(locale)}><span>STAY</span> HANEUL</a><a className="manage-link" href={route(locale)}>{text.home}</a></header><section className="reservation-management-list"><p className="section-kicker">MY RESERVATIONS</p><h1 id="reservation-management-title">{text.title}</h1>{entries.length === 0 ? <div className="reservation-management-empty"><h2>{text.empty}</h2><p>{text.emptyHelp}</p><a className="primary" href={route(locale)}>{text.search}</a></div> : <div className="reservation-card-list">{entries.map(({ reservation: item }) => <article className="reservation-card" key={item.id}><div><p className="reservation-status">{reservationStatusLabel(item.status, locale)}</p><h2>{name(item, locale)}</h2><p>{item.checkIn} – {item.checkOut} · {text.rooms} {item.rooms}</p></div><a className="outline" href={route(locale, `/reservations/${item.id}`)} aria-label={`${name(item, locale)} ${text.mine}`}>{text.mine}</a></article>)}</div>}</section></main>
  if (!access) return <main className="reservation-management-shell"><section className="reservation-management-empty"><h1>{text.missing}</h1><p>{text.missingHelp}</p><a className="primary" href={route(locale, '/reservations')}>{text.mine}</a></section></main>
  if (!reservation) return <main className="reservation-management-shell"><section className="reservation-management-empty"><h1>{text.unavailable}</h1><p>{text.unavailableHelp}</p><a className="primary" href={route(locale, '/reservations')}>{text.mine}</a></section></main>
  const canContinue = linkedPayment && change && canContinueChangePayment(change.status, reservation.id, change.reservationId)
  return <main className="reservation-management-shell" aria-labelledby="reservation-detail-title"><header className="reservation-management-header"><a className="brand" href={route(locale)}><span>STAY</span> HANEUL</a><a className="manage-link" href={route(locale, '/reservations')}>{text.mine}</a></header><section className="reservation-detail"><a className="reservation-back" href={route(locale, '/reservations')}>{text.back}</a><p className="reservation-status">{reservationStatusLabel(reservation.status, locale)}</p><h1 id="reservation-detail-title">{name(reservation, locale)}</h1><dl className="reservation-detail-list"><div><dt><CalendarDays size={17} aria-hidden="true" /> {text.schedule}</dt><dd>{date(reservation.checkIn, locale)} – {date(reservation.checkOut, locale)}</dd></div><div><dt>{text.rooms}</dt><dd>{reservation.rooms}</dd></div><div><dt><ReceiptText size={17} aria-hidden="true" /> {text.payment}</dt><dd>{paymentStatusLabel(reservation.paymentStatus, locale)}</dd></div><div><dt>{locale === 'ko' ? '투숙 인원' : 'Guests'}</dt><dd>{locale === 'ko' ? `성인 ${reservation.adults ?? '—'} · 아동 ${reservation.children ?? '—'}` : `Adults ${reservation.adults ?? '—'} · Children ${reservation.children ?? '—'}`}</dd></div><div><dt>{text.total}</dt><dd>{money(reservation.total, reservation.currency, locale)}</dd></div></dl>{cancellationPending && <div role="status"><p>{locale === 'ko' ? '환불 결과를 확인 중입니다. 모든 환불이 확인된 후 예약 취소가 완료됩니다. 환불 실패 시 취소를 다시 요청하면 기존 환불 계획으로 재시도합니다.' : 'Refund results are being checked. Cancellation completes after all refunds are verified. If a refund fails, retry cancellation to reuse the existing refund plan.'}</p><button className="outline" type="button" disabled={cancelling} onClick={() => void refreshCancellation()}>{locale === 'ko' ? '서버 상태 다시 확인' : 'Check server status'}</button></div>}<section className="reservation-policy"><h2>{text.policy}</h2><p>{reservation.cancellationPolicyDetails ? (locale === 'ko' ? `체크인 ${reservation.cancellationPolicyDetails.refundCutoffDaysBefore}일 전 ${reservation.cancellationPolicyDetails.refundCutoffLocalTime}까지 전액 환불 (${reservation.cancellationPolicyDetails.timezone})` : `Full refund until ${reservation.cancellationPolicyDetails.refundCutoffLocalTime}, ${reservation.cancellationPolicyDetails.refundCutoffDaysBefore} day(s) before check-in (${reservation.cancellationPolicyDetails.timezone})`) : text.policyUnknown}</p>{preview && <p>{preview.cancellable ? `${text.cutoff}: ${formatReservationDateTime(preview.cutoffAt, locale, preview.timezone)} · ${text.refund} ${money(preview.refundAmount, preview.currency, locale)}` : (locale === 'en' ? 'Cancellation is unavailable. Please contact the hotel.' : preview.unavailableReason ?? text.policyUnknown)}</p>}</section>{change && <section className="reservation-change-card" aria-labelledby="reservation-change-title"><p className="section-kicker">RESERVATION CHANGE</p><h2 id="reservation-change-title">{changeStatusLabel(change.status, locale)}</h2><p>{date(change.checkIn, locale)} – {date(change.checkOut, locale)} · {change.roomTypeName}</p><p>{text.difference} {money(change.differenceKrw, change.currency, locale)} · {formatReservationDateTime(change.expiresAt, locale, 'Asia/Seoul')} {text.until}</p>{change.refundStatus && <p>{locale === 'ko' ? '환불 상태' : 'Refund status'}: {paymentStatusLabel(change.refundStatus, locale)}</p>}{canContinue && <a className="primary" href={route(locale, '/reservation-change-payment')}><CreditCard size={18} aria-hidden="true" />{text.continue}</a>}{change.requestOrigin === 'CUSTOMER' && ['APPROVED','AWAITING_PAYMENT'].includes(change.status) && <button className="outline" type="button" onClick={() => void cancelCustomerChange()} disabled={cancelling}>{locale === 'ko' ? '변경 요청 취소' : 'Cancel change'}</button>}</section>}{reservation.status === 'CONFIRMED' && changeEligibility?.allowed && <a className="primary reservation-change-start" href={route(locale, `/reservations/${reservation.id}/change`)}>{text.change}</a>}{reservation.status === 'CONFIRMED' && changeEligibility && !changeEligibility.allowed && <p className="reservation-change-unavailable">{locale === 'ko' ? eligibilityReason(changeEligibility.reasonCode) : eligibilityReasonEn(changeEligibility.reasonCode)}</p>}{preview?.cancellable && <button className="outline reservation-cancel" type="button" ref={cancelButton} onClick={() => setDialogOpen(true)}>{text.cancel}</button>}</section>{dialogOpen && preview && <div className="reservation-dialog-backdrop" role="presentation"><div className="reservation-dialog" role="dialog" aria-modal="true" aria-labelledby="cancel-dialog-title" tabIndex={-1} ref={dialog}><button className="reservation-dialog-close" type="button" aria-label={text.close} onClick={closeDialog} ref={closeButton}><X size={20} /></button><h2 id="cancel-dialog-title">{text.cancelTitle}</h2><p>{text.cancelHelp.replace('{amount}', money(preview.refundAmount, preview.currency, locale))}</p>{error && <p className="reservation-management-error" role="alert" tabIndex={-1} ref={errorRef}>{error}</p>}<div className="reservation-dialog-actions"><button className="outline" type="button" onClick={closeDialog} disabled={cancelling}>{text.backAction}</button><button className="primary" type="button" onClick={() => void cancelReservation()} disabled={cancelling}>{cancelling ? text.cancelling : text.confirm}</button></div></div></div>}</main>
}

function eligibilityReason(code: string | null) {
 const labels: Record<string, string> = { RESERVATION_NOT_CONFIRMED: '확정된 예약만 변경할 수 있습니다.', ROOM_ALREADY_ASSIGNED: '객실 배정 후에는 호텔에 변경을 문의해 주세요.', RESERVATION_CHANGE_ACTIVE: '진행 중인 예약 변경을 먼저 완료해 주세요.', RESERVATION_STAY_CHANGE_TOO_LATE: '체크인 당일에는 예약을 변경할 수 없습니다.' }
 return labels[code ?? ''] ?? '현재 예약은 변경할 수 없습니다.'
}
function eligibilityReasonEn(code: string | null) {
 const labels: Record<string, string> = { RESERVATION_NOT_CONFIRMED: 'Only confirmed reservations can be changed.', ROOM_ALREADY_ASSIGNED: 'Contact the hotel after a room is assigned.', RESERVATION_CHANGE_ACTIVE: 'Complete the active reservation change first.', RESERVATION_STAY_CHANGE_TOO_LATE: 'Reservations cannot be changed on check-in day.' }
 return labels[code ?? ''] ?? 'This reservation cannot be changed.'
}
function paymentStatusLabel(status: string | null | undefined, locale: 'ko' | 'en') {
 const labels: Record<string, [string,string]> = { SUCCEEDED: ['완료','Completed'], NOT_STARTED: ['시작 전','Not started'], FAILED: ['실패','Failed'], UNKNOWN: ['결과 확인 중','Checking result'], APPROVING: ['승인 확인 중','Confirming payment'], NEW: ['처리 대기','Pending'], PROCESSING: ['처리 중','Processing'] };
 return (labels[status ?? ''] ?? ['서버 확인 필요','Check with the server'])[locale === 'ko' ? 0 : 1]
}
