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
  cancellationPolicy: string; guest: { name: string; email: string }
}

export class ApiFailure extends Error {
  constructor(public code: string, message: string, public status: number) { super(message) }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    const error = await response.json().catch(() => ({ code: 'NETWORK_ERROR', message: '요청을 처리하지 못했습니다.' }))
    throw new ApiFailure(error.code, error.message, response.status)
  }
  return response.json() as Promise<T>
}

const headers = (token: string, key?: string) => ({
  'Content-Type': 'application/json',
  'X-Reservation-Token': token,
  ...(key ? { 'Idempotency-Key': key } : {}),
})

export const api = {
  hotels: () => request<Hotel[]>('/api/hotels'),
  availability: (query: URLSearchParams) => request<{ offers: Offer[] }>(`/api/availability?${query}`),
  reserve: (body: object, token: string, key: string) => request<Reservation>('/api/reservations', {
    method: 'POST', headers: headers(token, key), body: JSON.stringify(body),
  }),
  getReservation: (id: string, token: string) => request<Reservation>(`/api/reservations/${id}`, { headers: headers(token) }),
  pay: (id: string, token: string, key: string, outcome: 'SUCCESS' | 'FAILURE') => request<{ status: string; paymentStatus: string }>(`/api/reservations/${id}/test-payment`, {
    method: 'POST', headers: headers(token, key), body: JSON.stringify({ outcome }),
  }),
  cancel: (id: string, token: string, key: string) => request<{ status: string; refundAmount: number }>(`/api/reservations/${id}/cancel`, {
    method: 'POST', headers: headers(token, key),
  }),
}

export function createManagementToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}
