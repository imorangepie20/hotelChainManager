export function reservationStatusLabel(status: string): string {
  return ({
    PENDING_PAYMENT: '결제 대기',
    CONFIRMED: '예약 확정',
    CHANGING: '변경 처리 중',
    CANCELLING: '취소 처리 중',
    CANCELLED: '취소 완료',
    EXPIRED: '만료',
  } as Record<string, string>)[status] ?? '상태 확인 중'
}

export function changeStatusLabel(status: string): string {
  return ({
    AWAITING_PAYMENT: '추가 결제 대기',
    READY_TO_APPLY: '변경 처리 중',
    APPLYING: '변경 처리 중',
    REFUND_PENDING: '환불 처리 중',
    COMPLETED: '변경 완료',
    EXPIRED: '변경 요청 만료',
    RECONCILIATION_REQUIRED: '변경 결과 확인 중',
  } as Record<string, string>)[status] ?? '변경 처리 중'
}

export function canContinueChangePayment(status: string, reservationId: string, reservationNumberSuffix: string): boolean {
  return status === 'AWAITING_PAYMENT' && reservationNumberSuffix.length > 0 && reservationId.endsWith(reservationNumberSuffix)
}
