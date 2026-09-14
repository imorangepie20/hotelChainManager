import type { ConfirmationInput, TossReturn, TossStatus } from './toss-payments'

export type PaymentProvider = 'toss-test' | 'fake' | 'disabled'
export type PaymentModes = { provider: PaymentProvider; changeProvider: PaymentProvider }

export function paymentActions(provider: PaymentProvider | null) {
  return { toss: provider === 'toss-test', fake: provider === 'fake' }
}

export async function recoverTossPayment(
  reconcile: () => Promise<TossStatus>,
  confirm: (input: ConfirmationInput) => Promise<TossStatus>,
  returned: TossReturn,
): Promise<TossStatus> {
  const state = await reconcile()
  // Only a request that never reached the server may use the in-memory callback again.
  if (state.paymentStatus === 'NEW' && returned.kind === 'success' && state.orderId === returned.input.orderId) {
    return confirm(returned.input)
  }
  return state
}
