export type CustomerRoute = (
  | { kind: 'home'; pathname: '/' | '/en' }
  | { kind: 'reservation-change-payment'; pathname: '/reservation-change-payment' }
  | { kind: 'booking-results'; pathname: string }
  | { kind: 'booking-checkout'; pathname: string }
  | { kind: 'booking-complete'; pathname: string }
  | { kind: 'reservation-payment-result'; pathname: string; reservationId: string }
  | { kind: 'reservations'; pathname: string }
  | { kind: 'reservation-detail'; pathname: string; reservationId: string }
  | { kind: 'reservation-change'; pathname: string; reservationId: string }
  | { kind: 'collection'; pathname: string; hotelSlug?: string; contentKind: 'ROOM' | 'DINING' | 'FACILITY' | 'EXPERIENCE' | 'PROMOTION' | 'GUIDE' | 'BRAND' }
  | { kind: 'page'; pathname: string; segments: string[] }) & { locale?: 'ko' | 'en' }

const segmentPattern = /^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$/
const encodedSeparator = /%(?:2f|5c)/i

export function normalizeCustomerPathname(pathname: string): string | null {
  const path = pathname.split(/[?#]/, 1)[0] ?? ''
  if (path === '/') return '/'
  if (encodedSeparator.test(path)) return null

  const hasTrailingSlash = path.endsWith('/')
  const rawSegments = path.split('/')
  if (rawSegments.shift() !== '' || rawSegments.length === 0) return null
  if (hasTrailingSlash) rawSegments.pop()
  const maximumSegments = rawSegments[0]?.toLowerCase() === 'en' ? 5 : 4
  if (rawSegments.length < 1 || rawSegments.length > maximumSegments || rawSegments.some(segment => !segmentPattern.test(segment))) return null

  return `/${rawSegments.map(segment => segment.toLowerCase()).join('/')}`
}

export function resolveCustomerRoute(pathname: string): CustomerRoute | null {
  const normalized = normalizeCustomerPathname(pathname)
  if (!normalized) return null
  if (normalized === '/en' || normalized.startsWith('/en/')) {
    const base = normalized === '/en' ? '/' : normalized.slice(3)
    if (base === '/en' || base.startsWith('/en/')) return null
    const route = resolveBaseRoute(base)
    return route ? { ...route, pathname: normalized, locale: 'en' } as CustomerRoute : null
  }
  return resolveBaseRoute(normalized)
}

function resolveBaseRoute(pathname: string): CustomerRoute | null {
  const normalizedPathname = normalizeCustomerPathname(pathname)
  if (!normalizedPathname) return null
  if (normalizedPathname === '/') return { kind: 'home', pathname: '/' }
  if (normalizedPathname === '/reservation-change-payment') {
    return { kind: 'reservation-change-payment', pathname: '/reservation-change-payment' }
  }
  const bookingRoutes = {
    '/booking/results': 'booking-results',
    '/booking/checkout': 'booking-checkout',
    '/booking/complete': 'booking-complete',
    '/reservations': 'reservations',
  } as const
  const bookingRoute = bookingRoutes[normalizedPathname as keyof typeof bookingRoutes]
  if (bookingRoute) return { kind: bookingRoute, pathname: normalizedPathname }

  const paymentResult = normalizedPathname.match(/^\/reservations\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/payment-result$/)
  if (paymentResult) return { kind: 'reservation-payment-result', pathname: normalizedPathname, reservationId: paymentResult[1]! }

  const reservationChange = normalizedPathname.match(/^\/reservations\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/change$/)
  if (reservationChange) return { kind: 'reservation-change', pathname: normalizedPathname, reservationId: reservationChange[1]! }

  const reservationDetail = normalizedPathname.match(/^\/reservations\/([a-z0-9]+(?:-[a-z0-9]+)*)$/)
  if (reservationDetail) return { kind: 'reservation-detail', pathname: normalizedPathname, reservationId: reservationDetail[1]! }

  const hotelCollection = normalizedPathname.match(/^\/stays\/([a-z0-9]+(?:-[a-z0-9]+)*)\/(rooms|dining|facilities|experiences)$/)
  if (hotelCollection) {
    const kindBySegment = { rooms: 'ROOM', dining: 'DINING', facilities: 'FACILITY', experiences: 'EXPERIENCE' } as const
    return { kind: 'collection', pathname: normalizedPathname, hotelSlug: hotelCollection[1]!, contentKind: kindBySegment[hotelCollection[2] as keyof typeof kindBySegment] }
  }
  const globalCollections = { '/offers': 'PROMOTION', '/guides': 'GUIDE', '/brand': 'BRAND' } as const
  const globalKind = globalCollections[normalizedPathname as keyof typeof globalCollections]
  if (globalKind) return { kind: 'collection', pathname: normalizedPathname, contentKind: globalKind }
  const roomDetail = normalizedPathname.match(/^\/stays\/[a-z0-9]+(?:-[a-z0-9]+)*\/(rooms|dining|facilities|experiences)\/[a-z0-9]+(?:-[a-z0-9]+)*$/)
  if (roomDetail) return { kind: 'page', pathname: normalizedPathname, segments: normalizedPathname.slice(1).split('/') }
  if (normalizedPathname.slice(1).split('/').length > 2) return null

  return {
    kind: 'page',
    pathname: normalizedPathname,
    segments: normalizedPathname.slice(1).split('/'),
  }
}
