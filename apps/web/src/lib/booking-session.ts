import { isBookingCriteria, type BookingCriteria } from './booking-query.ts'

export type BookingSelection = {
  criteria: BookingCriteria
  roomTypeId: string
  ratePlanId: string
}

export type ReservationAccess = {
  reservationId: string
  managementToken: string
}

type SessionStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

const selectionStorageKey = 'hotel-chain.booking.selection.v1'
const reservationAccessStorageKey = 'hotel-chain.booking.reservation-access.v1'
const storageVersion = 1

export class BookingSessionStore {
  private readonly storage: SessionStorage | null

  constructor(storage: SessionStorage | null = getSessionStorage()) {
    this.storage = storage
  }

  loadSelection(): BookingSelection | null {
    const stored = parseStoredValue(this.get(selectionStorageKey))
    if (!isRecord(stored) || stored.version !== storageVersion || !isBookingSelection(stored.selection)) return null
    return stored.selection
  }

  saveSelection(selection: BookingSelection): void {
    if (!isBookingSelection(selection)) {
      this.remove(selectionStorageKey)
      return
    }
    this.set(selectionStorageKey, { version: storageVersion, selection })
  }

  clearSelection(): void {
    this.remove(selectionStorageKey)
  }

  saveReservationAccess(access: ReservationAccess): void {
    if (!isReservationAccess(access)) return
    const accesses = this.listReservationAccess().filter(item => item.reservationId !== access.reservationId)
    this.set(reservationAccessStorageKey, { version: storageVersion, accesses: [access, ...accesses] })
  }

  listReservationAccess(): ReservationAccess[] {
    const stored = parseStoredValue(this.get(reservationAccessStorageKey))
    if (!isRecord(stored) || stored.version !== storageVersion || !Array.isArray(stored.accesses)) return []
    return stored.accesses.filter(isReservationAccess)
  }

  private get(key: string): string | null {
    try { return this.storage?.getItem(key) ?? null } catch { return null }
  }

  private set(key: string, value: unknown): void {
    try { this.storage?.setItem(key, JSON.stringify(value)) } catch { /* session storage can be unavailable */ }
  }

  private remove(key: string): void {
    try { this.storage?.removeItem(key) } catch { /* session storage can be unavailable */ }
  }
}

function getSessionStorage(): SessionStorage | null {
  try { return typeof sessionStorage === 'undefined' ? null : sessionStorage } catch { return null }
}

function parseStoredValue(value: string | null): unknown {
  if (!value) return null
  try { return JSON.parse(value) } catch { return null }
}

function isBookingSelection(value: unknown): value is BookingSelection {
  if (!isRecord(value)) return false
  return isBookingCriteria(value.criteria)
    && typeof value.roomTypeId === 'string' && value.roomTypeId.trim().length > 0
    && typeof value.ratePlanId === 'string' && value.ratePlanId.trim().length > 0
}

function isReservationAccess(value: unknown): value is ReservationAccess {
  if (!isRecord(value)) return false
  return typeof value.reservationId === 'string' && value.reservationId.trim().length > 0
    && typeof value.managementToken === 'string' && value.managementToken.trim().length > 0
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
