import type { ReactNode } from 'react'

type BookingStep = 'search' | 'checkout' | 'complete'

type CustomerBookingShellProps = {
  step: BookingStep
  summary?: ReactNode
  children: ReactNode
}

const steps: Array<{ key: BookingStep; label: string }> = [
  { key: 'search', label: '객실 선택' },
  { key: 'checkout', label: '예약 정보' },
  { key: 'complete', label: '결제' },
]

export function CustomerBookingShell({ step, summary, children }: CustomerBookingShellProps) {
  const activeIndex = steps.findIndex(item => item.key === step)

  return <div className="customer-booking-shell">
    <header className="customer-booking-header">
      <a className="brand" href="/"><span>STAY</span> HANEUL</a>
      <a className="manage-link" href="/reservations">예약 조회</a>
    </header>
    <main className="customer-booking-main">
      <ol className="booking-steps" aria-label="예약 진행 단계">
        {steps.map((item, index) => <li key={item.key} aria-current={index === activeIndex ? 'step' : undefined} className={index <= activeIndex ? 'active' : ''}>
          <span>{index + 1}</span>{item.label}
        </li>)}
      </ol>
      {summary && <div className="booking-summary" aria-label="선택한 숙박 조건">{summary}</div>}
      {children}
    </main>
  </div>
}
