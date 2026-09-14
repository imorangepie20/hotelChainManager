import type { PaymentModes } from './payment-recovery'
import type { ConfirmationInput, TossCheckout, TossStatus } from './toss-payments.ts'

export type Hotel = { id: string; name: string; region: string; timezone: string }
export type NightlyPrice = { date: string; amount: number }
export type Offer = {
  roomTypeId: string; roomTypeName: string; ratePlanId: string; ratePlanName: string
  breakfastIncluded: boolean; remaining: number; nightlyPrices: NightlyPrice[]
  total: number; currency: string; policyVersion: string
}
export type Reservation = {
  id: string; status: string; checkIn: string; checkOut: string; rooms: number
  expiresAt: string; total: number; currency: string; nightlyPrices: NightlyPrice[]
  cancellationPolicy: string; guest: { name: string; email: string; phone?: string | null }
  roomTypeName?: string; ratePlanName?: string; paymentStatus?: string; adults?: number; children?: number
  cancellationPolicyDetails?: { refundCutoffDaysBefore: number; refundCutoffLocalTime: string; timezone: string }
}

export type ReservationChangePayment = {
  reservationId: string
  reservationNumberSuffix: string
  previousCheckIn: string
  previousCheckOut: string
  previousRoomTypeName: string
  previousRatePlanName: string
  previousTotalKrw: number
  checkIn: string
  checkOut: string
  roomTypeName: string
  ratePlanName: string
  totalKrw: number
  differenceKrw: number
  additionalAmountKrw: number
  currency: string
  expiresAt: string
  environmentLabel: string
  status: string
}

export type ReservationChangeSummary = Pick<ReservationChangePayment, 'reservationId' | 'status' | 'checkIn' | 'checkOut' | 'roomTypeName' | 'ratePlanName' | 'differenceKrw' | 'currency' | 'expiresAt'> & { requestId: string; requestOrigin: 'STAFF' | 'CUSTOMER'; refundStatus: string | null }

export type CustomerChangeEligibility = { allowed: boolean; reasonCode: string | null }
export type CustomerChangeQuoteInput = { checkIn: string; checkOut: string; adults: number; children: number }
export type CustomerChangeQuote = {
  quoteId: string; baseOperationRevision: number; expiresAt: string
  checkIn: string; checkOut: string; adults: number; children: number; rooms: number
  previousTotal: number; currency: string
  offers: Array<Offer & { difference: number }>
}
export type CustomerChangeStart = { requestId: string; status: string; direction: 'CHARGE' | 'REFUND' | 'NONE'; version: number; expiresAt: string }

export type CancellationPreview = {
  reservationId: string; status: string; cancellable: boolean; refundAmount: number
  currency: string; cutoffAt: string; timezone: string; unavailableReason: string | null
}
export type PaymentMode = PaymentModes

export type WebsiteNavigationItem = {
  id: string
  hotelId: string | null
  label: string
  path: string
  children: WebsiteNavigationItem[]
}

export type PublishedWebsitePage = {
  id: string
  type: 'HOTEL_LANDING' | 'CONTENT_PAGE' | 'HOME_PAGE'
  contentKind: string | null
  path: string
  hotelId: string | null
  content: Record<string, unknown>
  connections: { roomTypeIds: string[]; targetHotelIds: string[]; relatedPages: { targetPageId: string; relationType: 'RELATED' | 'MANUAL_CARD'; displayOrder: number }[] }
  mediaVariants?: Record<string, { targetWidth: 640 | 1280; deliveryUrl: string; mimeType: 'image/webp' }[]>
}

export class ApiFailure extends Error {
  readonly code: string
  readonly status: number
  constructor(code: string, message: string, status: number) { super(message); this.code = code; this.status = status }
}

export type WebsitePreviewPageResponse = { page: PublishedWebsitePage; expiresAt: string }

async function apiFailure(response: Response) {
  const error = await response.json().catch(() => ({ code: 'NETWORK_ERROR', message: '요청을 처리하지 못했습니다.' }))
  return new ApiFailure(error.code, error.message, response.status)
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    throw await apiFailure(response)
  }
  if (response.status === 204) return null as T
  return response.json() as Promise<T>
}

const headers = (token: string, key?: string) => ({
  'Content-Type': 'application/json',
  'X-Reservation-Token': token,
  ...(key ? { 'Idempotency-Key': key } : {}),
})

export function reservationPaymentPath(id: string, action: 'checkout' | 'confirm' | 'status' | 'reconcile') {
  return `/api/reservations/${encodeURIComponent(id)}/payment-${action}`
}

export const api = {
  hotels: () => request<Hotel[]>('/api/hotels'),
  paymentModes: () => request<PaymentMode>('/api/payments/mode', { cache: 'no-store' }),
  tossChangeCheckout: () => request<TossCheckout>('/api/reservation-change-payments/current/toss/checkout', { method: 'POST', credentials: 'include' }),
  tossChangeConfirm: (input: ConfirmationInput) => request<TossStatus>('/api/reservation-change-payments/current/toss/confirm', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }),
  tossChangeStatus: () => request<TossStatus>('/api/reservation-change-payments/current/toss/status', { credentials: 'include', cache: 'no-store' }),
  hotelContent: (hotelId: string) => request<Record<string, unknown>>(`/api/hotels/${hotelId}/content`),
  websiteNavigation: (locale: 'ko' | 'en' = 'ko') => request<WebsiteNavigationItem[]>(`/api/website/navigation?${new URLSearchParams({ locale })}`),
  websitePage: (path: string, locale: 'ko' | 'en' = 'ko') => request<PublishedWebsitePage>(`/api/website/pages/resolve?${new URLSearchParams({ path, locale })}`),
  websitePreviewPage: async (path: string, locale: 'ko' | 'en', token: string): Promise<WebsitePreviewPageResponse> => {
    if (typeof window !== 'undefined' && window.location.protocol !== 'https:' && !['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname)) {
      throw new ApiFailure('WEBSITE_PREVIEW_HTTPS_REQUIRED', '초안 미리보기는 HTTPS에서만 사용할 수 있습니다.', 403)
    }
    const response = await fetch(`/api/website/pages/preview?${new URLSearchParams({ path, locale })}`, {
      headers: { 'X-Website-Preview': token }, cache: 'no-store', credentials: 'omit',
    })
    if (!response.ok) throw await apiFailure(response)
    const expiresAt = response.headers.get('X-Website-Preview-Expires-At')
    if (!expiresAt || !Number.isFinite(Date.parse(expiresAt))) throw new ApiFailure('WEBSITE_PREVIEW_UNAVAILABLE', '미리보기 만료 정보를 확인할 수 없습니다.', 410)
    return { page: await response.json() as PublishedWebsitePage, expiresAt }
  },
  websiteCollection: (contentKind: string, hotelSlug?: string, locale: 'ko' | 'en' = 'ko') => request<unknown[]>(`/api/website/collections?${new URLSearchParams({ kind: contentKind, locale, ...(hotelSlug ? { hotelSlug } : {}) })}`),
  availability: (query: URLSearchParams) => request<{ offers: Offer[] }>(`/api/availability?${query}`),
  reserve: (body: object, token: string, key: string) => request<Reservation>('/api/reservations', {
    method: 'POST', headers: headers(token, key), body: JSON.stringify(body),
  }),
  getReservation: (id: string, token: string) => request<Reservation>(`/api/reservations/${id}`, { headers: headers(token) }),
  changeEligibility: (id: string, token: string) => request<CustomerChangeEligibility>(`/api/reservations/${encodeURIComponent(id)}/change-eligibility`, { headers: headers(token), cache: 'no-store' }),
  changeQuote: (id: string, token: string, input: CustomerChangeQuoteInput) => request<CustomerChangeQuote>(`/api/reservations/${encodeURIComponent(id)}/change-quotes`, { method: 'POST', headers: headers(token), body: JSON.stringify(input) }),
  startChange: (id: string, token: string, key: string, input: { quoteId: string; roomTypeId: string; ratePlanId: string; expectedTotal: number }) => request<CustomerChangeStart>(`/api/reservations/${encodeURIComponent(id)}/change-requests`, { method: 'POST', headers: headers(token, key), body: JSON.stringify(input), credentials: 'include' }),
  cancelChange: (id: string, requestId: string, token: string, key: string) => request<CustomerChangeStart>(`/api/reservations/${encodeURIComponent(id)}/change-requests/${encodeURIComponent(requestId)}/cancel`, { method: 'POST', headers: headers(token, key) }),
  reservationChangeSummary: (id: string, token: string) => request<ReservationChangeSummary | null>(`/api/reservations/${encodeURIComponent(id)}/change-summary`, { headers: headers(token), cache: 'no-store' }),
  cancellationPreview: (id: string, token: string) => request<CancellationPreview>(`/api/reservations/${encodeURIComponent(id)}/cancellation-preview`, {
    headers: headers(token), cache: 'no-store',
  }),
  tossCheckout: (id: string, token: string, key: string) => request<TossCheckout>(reservationPaymentPath(id, 'checkout'), {
    method: 'POST', headers: headers(token, key),
  }),
  tossConfirm: (id: string, token: string, input: ConfirmationInput) => request<TossStatus>(reservationPaymentPath(id, 'confirm'), {
    method: 'POST', headers: headers(token), body: JSON.stringify(input),
  }),
  tossStatus: (id: string, token: string) => request<TossStatus>(reservationPaymentPath(id, 'status'), {
    headers: headers(token), cache: 'no-store',
  }),
  tossReconcile: (id: string, token: string) => request<TossStatus>(reservationPaymentPath(id, 'reconcile'), {
    method: 'POST', headers: headers(token),
  }),
  pay: (id: string, token: string, key: string, outcome: 'SUCCESS' | 'FAILURE') => request<{ status: string; paymentStatus: string }>(`/api/reservations/${id}/test-payment`, {
    method: 'POST', headers: headers(token, key), body: JSON.stringify({ outcome }),
  }),
  cancel: (id: string, token: string, key: string) => request<{ status: string; refundAmount: number }>(`/api/reservations/${id}/cancel`, {
    method: 'POST', headers: headers(token, key),
  }),
  exchangeReservationChangePaymentToken: async (token: string) => {
    const response = await fetch('/api/reservation-change-payments/session', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-Reservation-Change-Token': token },
    })
    if (!response.ok) throw await apiFailure(response)
  },
  reservationChangePayment: () => request<ReservationChangePayment>(
    '/api/reservation-change-payments/current',
    { credentials: 'include', cache: 'no-store' },
  ),
  reservationChangeCheckout: () => request<{ checkoutUrl: string }>(
    '/api/reservation-change-payments/current/checkout',
    { method: 'POST', credentials: 'include' },
  ),
}

export function createManagementToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}
