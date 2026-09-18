export type ReservationChangePaymentStatus =
  | 'AWAITING_PAYMENT'
  | 'READY_TO_APPLY'
  | 'APPLYING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'RECONCILIATION_REQUIRED'
  | 'REFUND_PENDING'

export type ReservationChangePaymentState = {
  status: string
  differenceKrw: number
}

export type ReservationChangePaymentDisplay = {
  kind: ReservationChangePaymentStatus | 'UNKNOWN'
  canCheckout: boolean
  direction: 'CHARGE' | 'REFUND' | 'NONE'
}

export function describeReservationChangePayment(payment: ReservationChangePaymentState): ReservationChangePaymentDisplay {
  const kind = isCustomerPaymentStatus(payment.status) ? payment.status : 'UNKNOWN'
  return {
    kind,
    canCheckout: kind === 'AWAITING_PAYMENT',
    direction: payment.differenceKrw > 0 ? 'CHARGE' : payment.differenceKrw < 0 ? 'REFUND' : 'NONE',
  }
}

export function shouldPollReservationChangePayment(status: string) {
  return status === 'AWAITING_PAYMENT' || status === 'READY_TO_APPLY' || status === 'APPLYING' || status === 'REFUND_PENDING'
}

export function customerPaymentFailureStatus(status: number) {
  return status === 401 || status === 404 ? 'EXPIRED' : null
}

export function customerPaymentFailureMessage(locale: 'ko' | 'en') {
  return locale === 'ko'
    ? '결제 링크가 만료되었습니다. 예약 상세에서 변경을 다시 시작해 주세요.'
    : 'This payment link has expired. Start the change again from your reservation details.'
}

function isCustomerPaymentStatus(status: string): status is ReservationChangePaymentStatus {
  return ['AWAITING_PAYMENT', 'READY_TO_APPLY', 'APPLYING', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'RECONCILIATION_REQUIRED', 'REFUND_PENDING'].includes(status)
}

export function changePaymentStatusForDisplay(changeStatus: string, paymentStatus?: string): string {
  // The change summary is authoritative once settlement or a terminal transition is recorded.
  if (['READY_TO_APPLY', 'APPLYING', 'COMPLETED', 'CANCELLED', 'EXPIRED'].includes(changeStatus)) return changeStatus
  return paymentStatus && ['UNKNOWN', 'APPROVING', 'PROCESSING'].includes(paymentStatus)
    ? 'RECONCILIATION_REQUIRED' : changeStatus
}

export function canRecoverChangePayment(returnedFromToss: boolean, provider: string | null, hasTossState: boolean, changeStatus?: string): boolean {
  // 결제 창을 떠난 뒤거나 Toss provider가 설정된 경우에만 서버 상태 재조회를 허용한다.
  // 0원·환불 변경은 결제 창이 없으므로 이 화면의 복구 동선에서 제외한다.
  if (changeStatus === 'REFUND_PENDING' || changeStatus === 'COMPLETED') return false
  return returnedFromToss || ((provider === 'toss-test' || provider === 'toss-live') && (hasTossState || changeStatus === undefined))
}
