import { useEffect, useRef, useState } from 'react'

type Props = { expiresAt: string; locale: 'ko' | 'en'; onExpire: () => void; onExit: () => void }
export function WebsitePreviewBanner({ expiresAt, locale, onExpire, onExit }: Props) {
  const [remaining, setRemaining] = useState(() => Math.max(0, Math.ceil((Date.parse(expiresAt) - Date.now()) / 1000)))
  const expire = useRef(onExpire)
  useEffect(() => { expire.current = onExpire }, [onExpire])
  useEffect(() => {
    let expired = false
    const tick = () => {
      const seconds = Math.max(0, Math.ceil((Date.parse(expiresAt) - Date.now()) / 1000))
      setRemaining(seconds)
      if (!seconds && !expired) { expired = true; expire.current() }
    }
    tick()
    const timer = window.setInterval(tick, 1000)
    document.addEventListener('visibilitychange', tick)
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', tick) }
  }, [expiresAt])
  return <section className="website-preview-banner" aria-label={locale === 'en' ? 'Saved draft preview' : '저장 초안 미리보기'}>
    <div><strong>{locale === 'en' ? 'Saved draft preview' : '저장 초안 미리보기'}</strong><p>{locale === 'en' ? 'Booking actions are disabled.' : '예약·결제·AI 예약 기능은 사용할 수 없습니다.'}</p>
      <span>{locale === 'en' ? 'Expires in' : '남은 시간'} {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')} · {new Date(expiresAt).toLocaleTimeString(locale === 'en' ? 'en-US' : 'ko-KR')}</span></div>
    <button type="button" onClick={onExit}>{locale === 'en' ? 'Exit preview' : '미리보기 종료'}</button>
  </section>
}
