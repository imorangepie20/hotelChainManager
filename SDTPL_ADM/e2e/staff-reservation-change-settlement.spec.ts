import { expect, test, type Page } from '@playwright/test'

const HOTEL = '11000000-0000-0000-0000-000000000001'
const RESERVATION = '42000000-0000-0000-0000-000000000001'
const REQUEST = '43000000-0000-0000-0000-000000000001'

const reservation = {
  reservationId: RESERVATION, guestName: '김하늘', guestEmail: 'guest@example.com',
  roomTypeName: '스탠다드 시티', ratePlanName: '룸 온리', checkIn: '2026-10-10',
  checkOut: '2026-10-12', adults: 2, children: 0, rooms: 1, status: 'CONFIRMED',
  totalKrw: 340000, currency: 'KRW', assignedRoomNumbers: [],
}

function request(status = 'APPROVED', actions = ['REPRICE', 'CANCEL', 'PAYMENT_LINK']) {
  return {
    id: REQUEST, reservationId: RESERVATION, hotelId: HOTEL, hotelName: '속초 지점', guestName: '김하늘',
    status, settlementDirection: 'CHARGE', version: status === 'APPROVED' ? 3 : 5,
    previousCheckIn: '2026-10-10', previousCheckOut: '2026-10-12', previousRoomTypeId: 'room-standard',
    previousRoomTypeName: '스탠다드 시티', previousRatePlanId: 'rate-standard', previousRatePlanName: '룸 온리',
    targetCheckIn: '2026-10-20', targetCheckOut: '2026-10-23', targetRoomTypeId: 'room-deluxe',
    targetRoomTypeName: '디럭스 오션', targetRatePlanId: 'rate-breakfast', targetRatePlanName: '조식 포함',
    rooms: 1, adults: 2, children: 0, approvalExpiresAt: '2026-09-15T00:00:00Z',
    quote: { id: 'quote', revision: 1, previousTotalKrw: 340000, totalKrw: 440000, differenceKrw: 100000,
      currency: 'KRW', nightlyPrices: [{ date: '2026-10-20', amount: 140000 }], createdAt: '2026-09-14T00:00:00Z' },
    approval: { id: 'approval', decisionType: 'AUTO_APPROVED', decidedBy: 'staff', decidedRole: 'BRANCH_STAFF',
      limitKrw: 100000, reason: null, createdAt: '2026-09-14T00:00:00Z' },
    actions,
    events: [{ id: 'event', eventType: 'APPROVED', fromStatus: null, toStatus: status,
      actorStaffId: 'staff', reason: null, createdAt: '2026-09-14T00:00:00Z' }],
  }
}

async function mockDashboard(page: Page) {
  await page.addInitScript(({ hotelId }) => {
    localStorage.setItem('hotel-chain-staff-session', 'test-session-token')
    localStorage.setItem('hotel-chain-staff', JSON.stringify({
      id: 'staff', email: 'sokcho@example.com', displayName: '속초 직원', role: 'BRANCH_STAFF', hotelId,
    }))
  }, { hotelId: HOTEL })
  await page.route('**/api/staff/me', route => route.fulfill({ json: {} }))
  await page.route('**/api/staff/hotels/*/reservations?*', route => route.fulfill({
    json: { hotelId: HOTEL, date: '2026-09-14', truncated: false, reservations: [reservation] },
  }))
  await page.route('**/api/staff/reservation-change-policy', route => route.fulfill({
    json: { settlementEnabled: true, directLimitKrw: 100000, approvalTtlSeconds: 86400, holdTtlSeconds: 900 },
  }))
  await page.route('**/api/staff/reservation-change-requests?*', route => route.fulfill({ json: [request()] }))
}

test('응답 유실 재시도에도 같은 token과 key로 고객 링크를 만든다', async ({ page }) => {
  await mockDashboard(page)
  const keys: string[] = []
  const bodies: Array<{ version: number; publicToken: string }> = []
  let attempts = 0
  await page.route(`**/api/staff/reservation-change-requests/${REQUEST}/payment-link`, route => {
    attempts++
    keys.push(route.request().headers()['idempotency-key'] ?? '')
    bodies.push(route.request().postDataJSON())
    if (attempts === 1) return route.fulfill({ status: 502, json: { message: '응답을 확인하지 못했습니다.' } })
    return route.fulfill({ json: {
      requestId: REQUEST, status: 'AWAITING_PAYMENT', version: 5,
      customerUrl: `http://127.0.0.1:4000/reservation-change-payment#${bodies[0]!.publicToken}`,
      expiresAt: '2026-09-14T00:15:00Z',
    } })
  })
  await page.route(`**/api/staff/reservation-change-requests/${REQUEST}`, route => route.fulfill({
    json: request('AWAITING_PAYMENT', []),
  }))

  await page.goto('/dashboard/reservations')
  await page.getByRole('button', { name: '김하늘 예약 상세' }).click()
  const detail = page.getByRole('dialog')
  const action = detail.getByRole('button', { name: '고객 결제 링크 만들기' })
  await action.click()
  await expect(detail.getByRole('alert')).toContainText('응답을 확인하지 못했습니다')
  await action.click()

  expect(keys).toHaveLength(2)
  expect(keys[0]).not.toBe('')
  expect(keys[1]).toBe(keys[0])
  expect(bodies[0]!.publicToken).toMatch(/^[A-Za-z0-9_-]{43}$/)
  expect(bodies[1]).toEqual(bodies[0])
  await expect(detail.getByRole('textbox', { name: '고객 결제 링크' })).toHaveValue(/reservation-change-payment#/)
  expect(await page.evaluate(() => localStorage.getItem('reservation-change-payment-token'))).toBeNull()
})
