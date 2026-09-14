import { parseBookingCriteria, serializeBookingCriteria, type BookingCriteria } from './booking-query.ts'

const validCriteria: BookingCriteria = {
  hotelId: 'sokcho', checkIn: '2026-09-22', checkOut: '2026-09-24', adults: 2, children: 0, rooms: 1,
}

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

function runBookingQueryTests() {
  expectEqual(
    parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1'),
    validCriteria,
    '유효한 검색 조건을 URL에서 복원한다',
  )
  expectEqual(
    serializeBookingCriteria(validCriteria),
    'hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1',
    '검색 조건만 URL로 직렬화한다',
  )
  expectEqual(serializeBookingCriteria(validCriteria).includes('secret'), false, '관리 토큰을 검색 URL에 넣지 않는다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1&managementToken=secret'), null, '관리 토큰이 있는 URL을 거절한다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1&guestEmail=guest%40example.com'), null, '예약자 정보가 있는 URL을 거절한다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1&paymentKey=payment-secret'), null, '결제 정보가 있는 URL을 거절한다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-02-29&checkOut=2026-03-02&adults=1&children=0&rooms=1'), null, '존재하지 않는 날짜를 거절한다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-24&checkOut=2026-09-22&adults=1&children=0&rooms=1'), null, '체크아웃이 체크인 이후가 아닌 조건을 거절한다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=0&children=0&rooms=1'), null, '성인 없는 조건을 거절한다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=1&children=-1&rooms=1'), null, '음수 아동 수를 거절한다')
  expectEqual(parseBookingCriteria('?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=1&children=0&rooms=0'), null, '객실 없는 조건을 거절한다')
}

runBookingQueryTests()
