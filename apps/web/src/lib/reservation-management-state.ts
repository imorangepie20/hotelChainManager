export function reservationStatusLabel(status: string, locale: 'ko' | 'en' = 'ko'): string {
  const korean = {
    PENDING_PAYMENT: '결제 대기',
    CONFIRMED: '예약 확정',
    CHANGING: '변경 처리 중',
    CANCELLING: '취소 처리 중',
    CANCELLED: '취소 완료',
    EXPIRED: '만료',
  } as Record<string, string>
  const english = {
    PENDING_PAYMENT: 'Payment pending', CONFIRMED: 'Reservation confirmed', CHANGING: 'Change in progress',
    CANCELLING: 'Cancellation in progress', CANCELLED: 'Cancelled', EXPIRED: 'Expired',
  } as Record<string, string>
  return (locale === 'en' ? english : korean)[status] ?? (locale === 'en' ? 'Status pending' : '상태 확인 중')
}

export function changeStatusLabel(status: string, locale: 'ko' | 'en' = 'ko'): string {
  const korean = {
    PENDING_APPROVAL: '변경 승인 대기', APPROVED: '변경 승인 완료', REFUNDING: '환불 처리 중', REJECTED: '변경 반려', CANCELLED: '변경 취소',
    AWAITING_PAYMENT: '추가 결제 대기',
    READY_TO_APPLY: '변경 처리 중',
    APPLYING: '변경 처리 중',
    REFUND_PENDING: '환불 처리 중',
    COMPLETED: '변경 완료',
    EXPIRED: '변경 요청 만료',
    RECONCILIATION_REQUIRED: '변경 결과 확인 중',
  } as Record<string, string>
  const english = {
    PENDING_APPROVAL: 'Awaiting change approval', APPROVED: 'Change approved', REFUNDING: 'Refund in progress', REJECTED: 'Change rejected', CANCELLED: 'Change cancelled',
    AWAITING_PAYMENT: 'Additional payment pending', READY_TO_APPLY: 'Change in progress', APPLYING: 'Change in progress',
    REFUND_PENDING: 'Refund in progress', COMPLETED: 'Change completed', EXPIRED: 'Change request expired',
    RECONCILIATION_REQUIRED: 'Checking change result',
  } as Record<string, string>
  return (locale === 'en' ? english : korean)[status] ?? (locale === 'en' ? 'Change in progress' : '변경 처리 중')
}

export function canContinueChangePayment(status: string, reservationId: string, changeReservationId: string): boolean {
  return status === 'AWAITING_PAYMENT' && reservationId === changeReservationId
}

export function formatReservationDateTime(value: string, locale: 'ko' | 'en', timezone: string): string {
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: timezone,
  }).format(new Date(value))
}
