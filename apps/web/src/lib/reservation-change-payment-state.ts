export type ReservationChangePaymentStatus =
  | 'AWAITING_PAYMENT'
  | 'READY_TO_APPLY'
  | 'APPLYING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'RECONCILIATION_REQUIRED'

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
  return status === 'AWAITING_PAYMENT' || status === 'READY_TO_APPLY' || status === 'APPLYING'
}

export function customerPaymentFailureStatus(status: number) {
  return status === 401 || status === 404 ? 'EXPIRED' : null
}

function isCustomerPaymentStatus(status: string): status is ReservationChangePaymentStatus {
  return ['AWAITING_PAYMENT', 'READY_TO_APPLY', 'APPLYING', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'RECONCILIATION_REQUIRED'].includes(status)
}

export function changePaymentStatusForDisplay(changeStatus: string, paymentStatus?: string): string {
  // The change summary is authoritative once settlement or a terminal transition is recorded.
  if (['READY_TO_APPLY', 'APPLYING', 'COMPLETED', 'CANCELLED', 'EXPIRED'].includes(changeStatus)) return changeStatus
  return paymentStatus && ['UNKNOWN', 'APPROVING', 'PROCESSING'].includes(paymentStatus)
    ? 'RECONCILIATION_REQUIRED' : changeStatus
}

export function canRecoverChangePayment(returnedFromToss: boolean, provider: string | null, hasTossState: boolean, changeStatus?: string): boolean {
  return changeStatus !== 'COMPLETED' && (returnedFromToss || (provider === 'toss-test' && (hasTossState || changeStatus === undefined)))
}
