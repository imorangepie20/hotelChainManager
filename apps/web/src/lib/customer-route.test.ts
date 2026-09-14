import { normalizeCustomerPathname, resolveCustomerRoute } from './customer-route.ts'

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

export function runCustomerRouteTests() {
  expectEqual(resolveCustomerRoute('/booking/results'), { kind: 'booking-results', pathname: '/booking/results' }, '객실 검색 결과를 CMS 경로보다 먼저 구분한다')
  expectEqual(resolveCustomerRoute('/booking/checkout'), { kind: 'booking-checkout', pathname: '/booking/checkout' }, '예약 결제를 전용 경로로 구분한다')
  expectEqual(resolveCustomerRoute('/booking/complete'), { kind: 'booking-complete', pathname: '/booking/complete' }, '예약 완료를 전용 경로로 구분한다')
  expectEqual(resolveCustomerRoute('/reservations/123e4567-e89b-12d3-a456-426614174000/payment-result'), { kind: 'reservation-payment-result', pathname: '/reservations/123e4567-e89b-12d3-a456-426614174000/payment-result', reservationId: '123e4567-e89b-12d3-a456-426614174000' }, '기존 예약별 결제 결과 경로를 구분한다')
  expectEqual(resolveCustomerRoute('/reservations/123e4567-e89b-12d3-a456-426614174000/change'), { kind: 'reservation-change', pathname: '/reservations/123e4567-e89b-12d3-a456-426614174000/change', reservationId: '123e4567-e89b-12d3-a456-426614174000' }, '고객 예약 변경 경로를 상세보다 먼저 구분한다')
  expectEqual(resolveCustomerRoute('/reservations'), { kind: 'reservations', pathname: '/reservations' }, '예약 목록을 전용 경로로 구분한다')
  expectEqual(resolveCustomerRoute('/reservations/R1'), { kind: 'reservation-detail', pathname: '/reservations/r1', reservationId: 'r1' }, '예약 상세 ID를 정규화한다')
  expectEqual(resolveCustomerRoute('/en/booking/results'), { kind: 'booking-results', pathname: '/en/booking/results', locale: 'en' }, '영문 예약 경로도 CMS 경로보다 먼저 구분한다')
  expectEqual(resolveCustomerRoute('/reservation-change-payment#token'), { kind: 'reservation-change-payment', pathname: '/reservation-change-payment' }, '예약 변경 결제를 일반 CMS 경로와 구분한다')
  expectEqual(resolveCustomerRoute('/en/brand/story'), { kind: 'page', pathname: '/en/brand/story', segments: ['brand', 'story'], locale: 'en' }, '영어 상세를 한국어와 구분한다')
  expectEqual(resolveCustomerRoute('/en'), { kind: 'home', pathname: '/en', locale: 'en' }, '영어 홈 경로를 구분한다')
  expectEqual(resolveCustomerRoute('/en/stays/sokcho/rooms/suite'), { kind: 'page', pathname: '/en/stays/sokcho/rooms/suite', segments: ['stays', 'sokcho', 'rooms', 'suite'], locale: 'en' }, 'locale 접두사는 콘텐츠 깊이에 포함하지 않는다')
  expectEqual(resolveCustomerRoute('/en/stays/sokcho/rooms'), { kind: 'collection', pathname: '/en/stays/sokcho/rooms', hotelSlug: 'sokcho', contentKind: 'ROOM', locale: 'en' }, '영어 목록을 구분한다')
  expectEqual(normalizeCustomerPathname('/'), '/', '루트 경로를 유지한다')
  expectEqual(normalizeCustomerPathname('/stays/SOKCHO/'), '/stays/sokcho', '지점 슬러그를 정규화한다')
  expectEqual(
    resolveCustomerRoute('/stays/jeju?checkIn=2026-09-20#booking'),
    { kind: 'page', pathname: '/stays/jeju', segments: ['stays', 'jeju'] },
    '숙소 상세 경로를 해석한다',
  )
  expectEqual(
    resolveCustomerRoute('/brand/story/'),
    { kind: 'page', pathname: '/brand/story', segments: ['brand', 'story'] },
    '일반 CMS 경로를 정규화한다',
  )
  expectEqual(resolveCustomerRoute('/brand'), { kind: 'collection', pathname: '/brand', contentKind: 'BRAND' }, '브랜드 루트는 공개 목록 경로로 구분한다')
  expectEqual(resolveCustomerRoute('/stays/seoraksan/rooms'), { kind: 'collection', pathname: '/stays/seoraksan/rooms', hotelSlug: 'seoraksan', contentKind: 'ROOM' }, '지점 객실 목록 경로를 상세 resolve보다 먼저 구분한다')
  expectEqual(resolveCustomerRoute('/brand/story/extra'), null, '세 단계 CMS 경로를 거절한다')
  expectEqual(resolveCustomerRoute('/stays/%2F'), null, '인코딩된 구분자를 슬러그로 허용하지 않는다')
  expectEqual(resolveCustomerRoute('/brand/%5c'), null, '인코딩된 역슬래시를 거절한다')
  expectEqual(resolveCustomerRoute('/brand//story'), null, '빈 경로 세그먼트를 거절한다')
}

runCustomerRouteTests()
