import { bookingText } from '../lib/booking-copy'
import type { ReactNode } from 'react'

type BookingStep = 'search' | 'checkout' | 'complete'

type CustomerBookingShellProps = {
  step: BookingStep
  locale?: 'ko' | 'en'
  summary?: ReactNode
  children: ReactNode
}


export function CustomerBookingShell({ step, locale = 'ko', summary, children }: CustomerBookingShellProps) {
  const steps: Array<{ key: BookingStep; label: string }> = [
  { key: 'search', label: bookingText(locale, "객실 선택") },
  { key: 'checkout', label: bookingText(locale, "예약 정보") },
  { key: 'complete', label: bookingText(locale, "결제") },
]

  const activeIndex = steps.findIndex(item => item.key === step)

  return <div className="customer-booking-shell">
    <header className="customer-booking-header">
      <a className="brand" href={locale === 'en' ? '/en' : '/'}><span>STAY</span> HANEUL</a>
      <a className="manage-link" href={locale === 'en' ? '/en/reservations' : '/reservations'}>{bookingText(locale, "예약 조회")}</a>
    </header>
    <main className="customer-booking-main">
      <ol className="booking-steps" aria-label={bookingText(locale, "예약 진행 단계")}>
        {steps.map((item, index) => <li key={item.key} aria-current={index === activeIndex ? 'step' : undefined} className={index <= activeIndex ? 'active' : ''}>
          <span>{index + 1}</span>{item.label}
        </li>)}
      </ol>
      {summary && <div className="booking-summary" aria-label={bookingText(locale, "선택한 숙박 조건")}>{summary}</div>}
      {children}
    </main>
  </div>
}
