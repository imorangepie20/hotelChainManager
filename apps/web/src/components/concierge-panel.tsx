import { FormEvent, useEffect, useRef, useState } from 'react'
import { Bot, LoaderCircle, Send, X } from 'lucide-react'

export type ConciergeCriteria = { region?: string; checkIn?: string; checkOut?: string; adults?: number; children?: number; rooms?: number; breakfastIncluded?: boolean }
type Offer = { roomTypeName: string; ratePlanName: string; total: number; remaining: number; currency: string }
type Message = { role: 'assistant' | 'user'; text: string }
type Props = { previewMode?: boolean; criteria: ConciergeCriteria; onApply: (criteria: ConciergeCriteria) => Promise<boolean> }
const money = new Intl.NumberFormat('ko-KR')

export function ConciergePanel({ criteria, onApply, previewMode = false }: Props) {
  const [open, setOpen] = useState(false); const [input, setInput] = useState(''); const [messages, setMessages] = useState<Message[]>([{ role: 'assistant', text: '어디로, 언제, 몇 분이 머무를지 알려주세요. 실제 판매 가능 객실만 찾아드릴게요.' }]); const [result, setResult] = useState<{ criteria: ConciergeCriteria; offers: Offer[] } | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const criteriaVersion = useRef(0)
  useEffect(() => { criteriaVersion.current += 1; setResult(null) }, [criteria.region, criteria.checkIn, criteria.checkOut, criteria.adults, criteria.children, criteria.rooms, criteria.breakfastIncluded])
  async function submit(event: FormEvent) {
    event.preventDefault()
    const message = input.trim()
    if (previewMode || !message || busy) return
    const requestVersion = criteriaVersion.current
    const requestCriteria = result?.criteria ?? criteria
    setBusy(true); setError(''); setInput('')
    setMessages(current => [...current, { role: 'user', text: message }])
    try {
      const response = await fetch(`${import.meta.env.VITE_CONCIERGE_URL ?? 'http://127.0.0.1:9000'}/chat`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message, criteria: requestCriteria }) })
      // 400은 도우미가 정책상 다루지 않는 조건을 거부한 것이다. 연결 장애(502)와 안내를 나눈다.
      if (response.status === 400) { if (requestVersion === criteriaVersion.current) setError('도우미가 처리할 수 없는 조건입니다. 객실 검색에서 직접 선택해 주세요.'); return }
      if (!response.ok) throw new Error()
      const next = await response.json() as { reply: string; criteria: ConciergeCriteria; offers: Offer[] }
      if (requestVersion !== criteriaVersion.current) return
      setResult({ criteria: next.criteria, offers: next.offers })
      setMessages(current => [...current, { role: 'assistant', text: next.reply }])
    } catch {
      if (requestVersion === criteriaVersion.current) setError('도우미에 연결하지 못했습니다. 직접 객실 검색을 이용해 주세요.')
    } finally {
      setBusy(false)
    }
  }
  async function apply() { if (previewMode || !result || busy) return; setBusy(true); setError(''); try { if (await onApply(result.criteria)) setOpen(false); else setError('최신 객실 조회에 실패했습니다. 예약 바에서 다시 검색해 주세요.') } catch { setError('최신 객실 조회에 실패했습니다. 예약 바에서 다시 검색해 주세요.') } finally { setBusy(false) } }
  if (previewMode) return <div className="concierge"><button className="ai-button" type="button" disabled><Bot size={17} /> AI 예약 도우미 <span>미리보기에서 사용 불가</span></button></div>
  return <div className="concierge"><button className="ai-button" type="button" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(true)}><Bot size={17} /> AI 예약 도우미 <span>실시간 조회</span></button>{open && <div className="concierge-backdrop" role="presentation" onMouseDown={() => setOpen(false)}><section className="concierge-panel" role="dialog" aria-modal="true" aria-label="AI 예약 도우미" onMouseDown={event => event.stopPropagation()}><header><div><p>STAY HANEUL CONCIERGE</p><h3>말로 찾는 나만의 스테이</h3></div><button type="button" aria-label="AI 예약 도우미 닫기" onClick={() => setOpen(false)}><X size={19} /></button></header><div className="concierge-note"><Bot size={15} /> 가격과 객실은 실시간 예약 API 조회 결과만 안내합니다.</div><div className="chat-history" aria-live="polite">{messages.map((message, index) => <p key={`${message.role}-${index}`} className={message.role}>{message.text}</p>)}</div>{result?.offers.length ? <div className="concierge-offers">{result.offers.map(offer => <article key={`${offer.roomTypeName}-${offer.ratePlanName}`}><div><strong>{offer.roomTypeName}</strong><span>{offer.ratePlanName} · 잔여 {offer.remaining}실</span></div><b>₩{money.format(offer.total)}</b></article>)}<button type="button" className="concierge-apply" onClick={apply} disabled={busy}>{busy ? '최신 객실 확인 중…' : '이 조건으로 객실 검색'}</button></div> : null}{error && <p className="concierge-error" role="alert">{error}</p>}<form onSubmit={submit}><input value={input} onChange={event => setInput(event.target.value)} maxLength={500} placeholder="예: 2026-09-17부터 2박, 속초 성인 두 명 조식 포함" aria-label="예약 조건 입력" /><button type="submit" disabled={busy || !input.trim()} aria-label="메시지 보내기">{busy ? <LoaderCircle className="spin" size={18} /> : <Send size={18} />}</button></form></section></div>}</div>
}
