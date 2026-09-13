import { captureReservationChangePaymentToken } from './reservation-change-payment-session.ts'

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

export function runReservationChangePaymentSessionTests() {
  const calls: string[] = []
  const token = captureReservationChangePaymentToken(
    { pathname: '/reservation-change-payment', hash: `#${'A'.repeat(43)}` },
    path => calls.push(path),
  )
  expectEqual(token, 'A'.repeat(43), '43자 token을 반환한다')
  expectEqual(calls, ['/reservation-change-payment'], 'fragment를 즉시 제거한다')
  expectEqual(
    captureReservationChangePaymentToken(
      { pathname: '/reservation-change-payment', hash: '#short-token' },
      path => calls.push(path),
    ),
    null,
    '잘못된 token을 거절한다',
  )
  expectEqual(calls.at(-1), '/reservation-change-payment', '잘못된 fragment도 주소에서 제거한다')
}

runReservationChangePaymentSessionTests()
