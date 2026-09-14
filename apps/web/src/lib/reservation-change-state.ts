export type ChangeAction = 'PAY' | 'POLL' | 'RETURN'

export function changeAction(status: string): ChangeAction {
  if (status === 'AWAITING_PAYMENT') return 'PAY'
  if (['COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED'].includes(status)) return 'RETURN'
  return 'POLL'
}
