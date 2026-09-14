export type ReservationChangeLinkState = 'NOT_CREATED' | 'CREATING' | 'READY' | 'COPIED' | 'EXPIRED'

export function shouldPollReservationChangeRequest(status: string) {
  return ['AWAITING_PAYMENT', 'REFUND_PENDING', 'READY_TO_APPLY', 'APPLYING'].includes(status)
}

export function describeReservationChangeLink(
  requestStatus: string | null,
  hasCustomerUrl: boolean,
  state: ReservationChangeLinkState,
) {
  const expired = requestStatus === 'EXPIRED' || state === 'EXPIRED'
  return {
    keepVisible: hasCustomerUrl && (expired || requestStatus !== null),
    state: expired ? 'EXPIRED' as const : state,
    canStartNewRequest: expired,
  }
}
