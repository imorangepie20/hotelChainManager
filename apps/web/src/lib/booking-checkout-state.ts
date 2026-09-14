export type CheckoutState = 'DETAILS' | 'HOLDING' | 'PAYMENT_READY' | 'PAYMENT_OPEN' | 'EXPIRED' | 'ERROR' | 'CONFIRMED'

export function checkoutStateFromReservation(status: string, expiresAt: string, now = new Date()): CheckoutState {
  if (status === 'CONFIRMED') return 'CONFIRMED'
  if (status === 'EXPIRED' || !Number.isFinite(Date.parse(expiresAt)) || Date.parse(expiresAt) <= now.getTime()) return 'EXPIRED'
  if (status === 'PENDING_PAYMENT') return 'PAYMENT_READY'
  return 'ERROR'
}

export function formatHoldRemaining(expiresAt: string, now = new Date()): string {
  const remainingSeconds = Math.max(0, Math.ceil((Date.parse(expiresAt) - now.getTime()) / 1000))
  const minutes = Math.floor(remainingSeconds / 60)
  const seconds = remainingSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function isRetryablePaymentState(state: CheckoutState): boolean {
  return state === 'PAYMENT_READY'
}
