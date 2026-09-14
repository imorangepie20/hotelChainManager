import { api } from './api.ts'
const calls: Array<{ url: string; init?: RequestInit }> = []
const previousFetch = globalThis.fetch
function equal(actual: unknown, expected: unknown) { if (actual !== expected) throw new Error(`Expected ${expected}, received ${actual}`) }
try {
  globalThis.fetch = async (input, init) => { calls.push({ url: String(input), init }); return String(input).endsWith('change-summary') ? new Response(null, { status: 204 }) : new Response(JSON.stringify({ status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' }), { status: 200 }) }
  const paid = await api.pay('r1', 'secret', 'stable-key', 'SUCCESS')
  equal(paid.paymentStatus, 'SUCCEEDED')
  equal(calls[0]?.url, '/api/reservations/r1/test-payment')
  equal(new Headers(calls[0]?.init?.headers).get('Idempotency-Key'), 'stable-key')
  equal(new Headers(calls[0]?.init?.headers).get('X-Reservation-Token'), 'secret')
  equal(calls[0]?.init?.body, JSON.stringify({ outcome: 'SUCCESS' }))
  equal(await api.reservationChangeSummary('r1', 'secret'), null)
  equal(calls[1]?.url, '/api/reservations/r1/change-summary')
  equal(calls[1]?.init?.cache, 'no-store')
  equal(new Headers(calls[1]?.init?.headers).get('X-Reservation-Token'), 'secret')
} finally { globalThis.fetch = previousFetch }
