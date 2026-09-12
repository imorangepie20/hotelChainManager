export type BookingIntent = { hotelId: string; roomTypeId?: string }
export type SearchState = {
  hotelId: string
  checkIn: string
  checkOut: string
  adults: number
  children: number
  rooms: number
  breakfastOnly: boolean
  roomTypeId?: string
}

export function applyBookingIntent(intent: BookingIntent, current: SearchState): SearchState {
  return { ...current, hotelId: intent.hotelId, roomTypeId: intent.roomTypeId }
}

export function availabilityRequestFields(current: SearchState) {
  return {
    hotelId: current.hotelId,
    checkIn: current.checkIn,
    checkOut: current.checkOut,
    adults: current.adults,
    children: current.children,
    rooms: current.rooms,
  }
}

export function filterOffersForRoomType<T extends { roomTypeId: string }>(offers: T[], roomTypeId?: string): T[] {
  return roomTypeId ? offers.filter(offer => offer.roomTypeId === roomTypeId) : offers
}

export class LatestAvailabilityRequest {
  private revision = 0
  private activeCriteria: string | null = null
  private activeRequest: number | null = null
  private currentCriteria: string

  constructor(currentCriteria: string) {
    this.currentCriteria = currentCriteria
  }

  criteriaChanged(nextCriteria: string): boolean {
    if (this.currentCriteria === nextCriteria) return false

    this.currentCriteria = nextCriteria
    if (this.activeCriteria === nextCriteria) return false

    this.revision += 1
    this.activeCriteria = null
    this.activeRequest = null
    return true
  }

  start(criteria: string): number {
    this.revision += 1
    this.activeCriteria = criteria
    this.activeRequest = this.revision
    return this.revision
  }

  isCurrent(request: number): boolean {
    return this.activeRequest === request && this.revision === request
  }

  complete(request: number): boolean {
    if (!this.isCurrent(request)) return false

    this.activeCriteria = null
    this.activeRequest = null
    return true
  }
}
