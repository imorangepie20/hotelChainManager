import { useEffect, useRef, useState } from 'react'
import { api } from '../lib/api'
import { paymentMessage, type TossReturn, type TossStatus } from '../lib/toss-payments'
import { recoverTossPayment } from '../lib/payment-recovery'

export function ReservationPaymentResultPage({ reservationId, returned }: { reservationId: string; returned: TossReturn }) {
  const [state, setState] = useState<TossStatus | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(true)
  const alert = useRef<HTMLDivElement>(null)
  const initialRequest = useRef<Promise<TossStatus> | null>(null)
  const busyRef = useRef(false)

  function token() {
    const value = sessionStorage.getItem(`reservation:${reservationId}`)
    if (!value) throw new Error('예약 관리 정보가 없습니다. 예약한 브라우저에서 다시 열어 주세요.')
    return value
  }
  useEffect(() => {
    let active = true
    if (!initialRequest.current) initialRequest.current = Promise.resolve().then(() => returned.kind === 'success'
      ? api.tossConfirm(reservationId, token(), returned.input) : api.tossStatus(reservationId, token()))
    initialRequest.current.then(value => { if (active) setState(value) }, () => {
      if (active) setError(sessionStorage.getItem(`reservation:${reservationId}`)
        ? '결제 결과를 확인하지 못했습니다. 중복 결제하지 말고 서버 상태를 다시 확인해 주세요.'
        : '예약 관리 정보가 없습니다. 예약한 브라우저에서 다시 열어 주세요.')
    }).finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [reservationId, returned])
  useEffect(() => { if (error) alert.current?.focus() }, [error])

  async function refresh() {
    if (busyRef.current) return
    busyRef.current = true; setBusy(true); setError('')
    try { setState(await recoverTossPayment(() => api.tossReconcile(reservationId, token()), input => api.tossConfirm(reservationId, token(), input), returned)) }
    catch { setError('서버 상태를 확인하지 못했습니다. 예약한 브라우저에서 잠시 후 다시 확인해 주세요.') }
    finally { busyRef.current = false; setBusy(false) }
  }
  return <main className="change-payment-shell"><section className="change-payment-card" aria-labelledby="payment-result-title">
    <h1 id="payment-result-title">토스 테스트 결제 결과</h1>
    <p>실제 과금 없는 테스트 결제입니다.</p>
    {returned.kind === 'fail' && <p role="status">결제창에서 결제가 완료되지 않았습니다. 서버 상태를 확인한 후 예약 화면에서 다시 시도해 주세요.</p>}
    {returned.kind === 'invalid' && <p role="status">결제 복귀 정보를 확인할 수 없어 서버 상태만 조회합니다.</p>}
    {busy && <p role="status">서버에서 결제 결과를 확인하고 있습니다.</p>}
    {error && <div className="change-payment-state is-error" ref={alert} role="alert" tabIndex={-1}>{error}</div>}
    {state && <p className="change-payment-status" role="status">{paymentMessage(state).text}</p>}
    <button className="change-payment-action" onClick={refresh} disabled={busy}>서버 상태 다시 확인</button>
    <a className="change-payment-action" href="/#reservation-management">예약 화면으로 돌아가기</a>
  </section></main>
}
