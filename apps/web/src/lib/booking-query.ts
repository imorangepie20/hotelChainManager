export type BookingCriteria = {
  hotelId: string
  checkIn: string
  checkOut: string
  adults: number
  children: number
  rooms: number
}

const criteriaKeys = ['hotelId', 'checkIn', 'checkOut', 'adults', 'children', 'rooms'] as const
const criteriaKeySet = new Set<string>(criteriaKeys)
const wholeNumberPattern = /^(0|[1-9]\d*)$/
const datePattern = /^(\d{4})-(\d{2})-(\d{2})$/

export function parseBookingCriteria(search: string): BookingCriteria | null {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search)
  if (Array.from(params.keys()).some(key => !criteriaKeySet.has(key))) return null
  if (!criteriaKeys.every(key => params.getAll(key).length === 1)) return null

  const hotelId = params.get('hotelId')!
  const checkIn = params.get('checkIn')!
  const checkOut = params.get('checkOut')!
  const adults = parseWholeNumber(params.get('adults')!)
  const children = parseWholeNumber(params.get('children')!)
  const rooms = parseWholeNumber(params.get('rooms')!)

  if (!hotelId.trim() || !isCalendarDate(checkIn) || !isCalendarDate(checkOut) || checkOut <= checkIn) return null
  if (adults === null || children === null || rooms === null || adults < 1 || rooms < 1) return null

  return { hotelId, checkIn, checkOut, adults, children, rooms }
}

export function serializeBookingCriteria(criteria: BookingCriteria): string {
  if (!isBookingCriteria(criteria)) throw new Error('Invalid booking criteria')

  return new URLSearchParams([
    ['hotelId', criteria.hotelId],
    ['checkIn', criteria.checkIn],
    ['checkOut', criteria.checkOut],
    ['adults', String(criteria.adults)],
    ['children', String(criteria.children)],
    ['rooms', String(criteria.rooms)],
  ]).toString()
}

export function isBookingCriteria(value: unknown): value is BookingCriteria {
  if (!isRecord(value)) return false
  const { hotelId, checkIn, checkOut, adults, children, rooms } = value
  return typeof hotelId === 'string' && hotelId.trim().length > 0
    && typeof checkIn === 'string' && isCalendarDate(checkIn)
    && typeof checkOut === 'string' && isCalendarDate(checkOut) && checkOut > checkIn
    && isNonNegativeInteger(adults) && adults >= 1
    && isNonNegativeInteger(children)
    && isNonNegativeInteger(rooms) && rooms >= 1
}

function parseWholeNumber(value: string): number | null {
  if (!wholeNumberPattern.test(value)) return null
  const number = Number(value)
  return Number.isSafeInteger(number) ? number : null
}

function isCalendarDate(value: string): boolean {
  const match = value.match(datePattern)
  if (!match) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
