import { applyBookingIntent, availabilityRequestFields, filterOffersForRoomType } from './latest-availability-request.ts'

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

const current = {
  hotelId: '823e4567-e89b-12d3-a456-426614174000',
  checkIn: '2026-10-10',
  checkOut: '2026-10-11',
  adults: 2,
  children: 0,
  rooms: 1,
  breakfastOnly: false,
}

expectEqual(
  applyBookingIntent({ hotelId: '923e4567-e89b-12d3-a456-426614174000', roomTypeId: 'a23e4567-e89b-12d3-a456-426614174000' }, current),
  { ...current, hotelId: '923e4567-e89b-12d3-a456-426614174000', roomTypeId: 'a23e4567-e89b-12d3-a456-426614174000' },
  '예약 intent는 지점과 객실 유형만 검색 상태에 반영한다',
)
expectEqual(
  applyBookingIntent({ hotelId: '923e4567-e89b-12d3-a456-426614174000' }, { ...current, roomTypeId: 'a23e4567-e89b-12d3-a456-426614174000' }),
  { ...current, hotelId: '923e4567-e89b-12d3-a456-426614174000', roomTypeId: undefined },
  '객실 유형 없는 예약 intent는 이전 객실 필터를 지운다',
)

expectEqual(
  availabilityRequestFields({ ...current, roomTypeId: 'a23e4567-e89b-12d3-a456-426614174000' }),
  { hotelId: current.hotelId, checkIn: '2026-10-10', checkOut: '2026-10-11', adults: 2, children: 0, rooms: 1 },
  'availability 요청에는 CMS roomTypeId를 포함하지 않는다',
)
expectEqual(
  filterOffersForRoomType([{ roomTypeId: 'a23e4567-e89b-12d3-a456-426614174000' }, { roomTypeId: 'b23e4567-e89b-12d3-a456-426614174000' }], 'a23e4567-e89b-12d3-a456-426614174000'),
  [{ roomTypeId: 'a23e4567-e89b-12d3-a456-426614174000' }],
  '새 availability 응답에서 선택 객실 유형만 보인다',
)
