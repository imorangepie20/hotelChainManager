import { BookingSessionStore, type BookingSelection } from './booking-session.ts'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string) { return this.values.get(key) ?? null }
  setItem(key: string, value: string) { this.values.set(key, value) }
  removeItem(key: string) { this.values.delete(key) }
}

const selection: BookingSelection = {
  criteria: { hotelId: 'sokcho', checkIn: '2026-09-22', checkOut: '2026-09-24', adults: 2, children: 0, rooms: 1 },
  roomTypeId: 'standard-city', ratePlanId: 'flexible',
}

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

function runBookingSessionTests() {
  const storage = new MemoryStorage()
  const store = new BookingSessionStore(storage)

  store.saveSelection(selection)
  expectEqual(store.loadSelection(), selection, '새로고침 뒤에도 객실과 요금제 선택을 복원한다')

  store.clearSelection()
  expectEqual(store.loadSelection(), null, '검색 조건을 바꾸면 이전 객실 선택을 제거한다')

  store.saveReservationAccess({ reservationId: 'r1', managementToken: 'secret' })
  expectEqual(store.listReservationAccess(), [{ reservationId: 'r1', managementToken: 'secret' }], '관리 토큰은 같은 브라우저 세션에서만 복원한다')

  store.saveCheckoutProgress(selection, { reservationId: 'r1', managementToken: 'secret' })
  expectEqual(store.loadCheckoutProgress(selection), { reservationId: 'r1', managementToken: 'secret' }, '같은 객실 선택의 확보만 새로고침 뒤에 복원한다')
  expectEqual(store.loadCheckoutProgress({ ...selection, ratePlanId: 'breakfast' }), null, '다른 요금제 선택에는 이전 확보를 재사용하지 않는다')

  storage.setItem('hotel-chain.booking.selection.v1', '{"version":0,"selection":{}}')
  expectEqual(store.loadSelection(), null, '구형 선택 저장값을 무시한다')

  storage.setItem('hotel-chain.booking.reservation-access.v1', '{not-json')
  expectEqual(store.listReservationAccess(), [], '손상된 예약 접근 저장값을 예외 없이 무시한다')
}

runBookingSessionTests()
