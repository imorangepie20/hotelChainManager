import { recoverTossPayment, paymentActions } from './payment-recovery.ts'

function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`Expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`)
}
const returned = { kind: 'success' as const, input: { paymentKey: 'pk', orderId: 'order', amountKrw: 120000 } }
const state = { reservationId: 'id', orderId: 'order', status: 'PENDING_PAYMENT', paymentStatus: 'UNKNOWN' }
let confirmations = 0
const confirm = async () => { confirmations++; return { ...state, status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' } }
equal(await recoverTossPayment(async () => state, confirm, returned), state)
equal(confirmations, 0)
equal((await recoverTossPayment(async () => ({ ...state, paymentStatus: 'NEW' }), confirm, returned)).paymentStatus, 'SUCCEEDED')
equal(confirmations, 1)
await recoverTossPayment(async () => ({ ...state, paymentStatus: 'NEW', orderId: 'other' }), confirm, returned)
await recoverTossPayment(async () => ({ ...state, paymentStatus: 'NEW' }), confirm, { kind: 'none' })
await recoverTossPayment(async () => ({ ...state, paymentStatus: 'APPROVING' }), confirm, returned)
equal(confirmations, 1)
equal(paymentActions('toss-test'), { toss: true, fake: false })
equal(paymentActions('fake'), { toss: false, fake: true })
equal(paymentActions('disabled'), { toss: false, fake: false })
equal(paymentActions(null), { toss: false, fake: false })
for (const status of ['EXPIRED', 'CANCELLED', 'CONFIRMED']) {
  const terminal = { ...state, status, paymentStatus: 'NEW' }
  equal(await recoverTossPayment(async () => terminal, confirm, returned), terminal)
}
equal(confirmations, 1)
console.log('Payment recovery and provider actions passed')
