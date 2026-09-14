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

export type CheckoutSummary = { roomTypeName: string; ratePlanName: string }

type SessionStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

const selectionStorageKey = 'hotel-chain.booking.selection.v1'
const reservationAccessStorageKey = 'hotel-chain.booking.reservation-access.v1'
const checkoutProgressStorageKey = 'hotel-chain.booking.checkout-progress.v1'
const checkoutAttemptKey = 'hotel-chain.booking.checkout-attempt.v1'
const storageVersion = 1

export class BookingSessionStore {
  private readonly storage: SessionStorage | null

  constructor(storage: SessionStorage | null = getSessionStorage()) {
    this.storage = storage
  }

  savePaymentResultAccess(access: ReservationAccess): void { if (isReservationAccess(access)) this.set('hotel-chain.booking.payment-result.v1', access) }
  loadPaymentResultAccess(): ReservationAccess | null {
    const access = parseStoredValue(this.get('hotel-chain.booking.payment-result.v1'))
    return isReservationAccess(access) ? access : null
  }

  clearCheckoutProgress(): void { this.remove(checkoutProgressStorageKey) }

  clearCheckoutAttempt(): void { this.remove(checkoutAttemptKey) }

  checkoutAttempt(fingerprint: string, credentials: { managementToken: string; idempotencyKey: string }) {
    const stored = parseStoredValue(this.get(checkoutAttemptKey))
    if (isRecord(stored) && typeof stored.fingerprint === 'string' && typeof stored.managementToken === 'string' && typeof stored.idempotencyKey === 'string') {
      if (stored.fingerprint !== fingerprint) throw new Error('CHECKOUT_RETRY_INPUT_CHANGED')
      return { managementToken: stored.managementToken, idempotencyKey: stored.idempotencyKey }
    }
    this.set(checkoutAttemptKey, { fingerprint, ...credentials })
    if (!this.get(checkoutAttemptKey)) throw new Error('CHECKOUT_STORAGE_REQUIRED')
    return credentials
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

  saveCheckoutProgress(selection: BookingSelection, access: ReservationAccess, summary?: CheckoutSummary): void {
    if (!isBookingSelection(selection) || !isReservationAccess(access) || (summary !== undefined && !isCheckoutSummary(summary))) return
    this.set(checkoutProgressStorageKey, { version: storageVersion, selection, access, summary })
  }

  loadCheckoutProgress(selection: BookingSelection): ReservationAccess | null {
    const stored = parseStoredValue(this.get(checkoutProgressStorageKey))
    if (!isRecord(stored) || stored.version !== storageVersion || !isBookingSelection(stored.selection) || !isReservationAccess(stored.access)) return null
    return sameSelection(stored.selection, selection) ? stored.access : null
  }

  loadCheckoutSummary(selection: BookingSelection): CheckoutSummary | null {
    const stored = parseStoredValue(this.get(checkoutProgressStorageKey))
    if (!isRecord(stored) || stored.version !== storageVersion || !isBookingSelection(stored.selection) || !isCheckoutSummary(stored.summary)) return null
    return sameSelection(stored.selection, selection) ? stored.summary : null
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

function isCheckoutSummary(value: unknown): value is CheckoutSummary {
  return isRecord(value) && typeof value.roomTypeName === 'string' && value.roomTypeName.trim().length > 0
    && typeof value.ratePlanName === 'string' && value.ratePlanName.trim().length > 0
}

function sameSelection(left: BookingSelection, right: BookingSelection): boolean {
  return left.roomTypeId === right.roomTypeId && left.ratePlanId === right.ratePlanId
    && left.criteria.hotelId === right.criteria.hotelId && left.criteria.checkIn === right.criteria.checkIn
    && left.criteria.checkOut === right.criteria.checkOut && left.criteria.adults === right.criteria.adults
    && left.criteria.children === right.criteria.children && left.criteria.rooms === right.criteria.rooms
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
