export type CustomerRoute =
  | { kind: 'home'; pathname: '/' }
  | { kind: 'collection'; pathname: string; hotelSlug?: string; contentKind: 'ROOM' | 'DINING' | 'FACILITY' | 'EXPERIENCE' | 'PROMOTION' | 'GUIDE' | 'BRAND' }
  | { kind: 'page'; pathname: string; segments: string[] }

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
  if (rawSegments.length < 1 || rawSegments.length > 4 || rawSegments.some(segment => !segmentPattern.test(segment))) return null

  return `/${rawSegments.map(segment => segment.toLowerCase()).join('/')}`
}

export function resolveCustomerRoute(pathname: string): CustomerRoute | null {
  const normalizedPathname = normalizeCustomerPathname(pathname)
  if (!normalizedPathname) return null
  if (normalizedPathname === '/') return { kind: 'home', pathname: '/' }
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
