import { expect, test, type Page } from '@playwright/test'

const hotel = {
  id: 'sokcho',
  name: '스테이 하늘 속초',
  region: '속초',
  timezone: 'Asia/Seoul',
}

const offer = {
  roomTypeId: 'standard-city',
  roomTypeName: '스탠다드 시티',
  ratePlanId: 'standard-city-flex',
  ratePlanName: '유연 취소',
  breakfastIncluded: false,
  remaining: 3,
  nightlyPrices: [{ date: '2026-09-22', amount: 180000 }, { date: '2026-09-23', amount: 180000 }],
  total: 360000,
  currency: 'KRW',
  policyVersion: 'v1',
}

const pendingReservation = {
  id: 'reservation-123', status: 'PENDING_PAYMENT', checkIn: '2026-09-22', checkOut: '2026-09-24', rooms: 1,
  expiresAt: new Date(Date.now() + 600000).toISOString(), total: 360000, currency: 'KRW', nightlyPrices: offer.nightlyPrices,
  roomTypeName: offer.roomTypeName, ratePlanName: offer.ratePlanName, adults: 2, children: 0, paymentStatus: 'NOT_STARTED', cancellationPolicyDetails: { refundCutoffDaysBefore: 1, refundCutoffLocalTime: '18:00', timezone: 'Asia/Seoul' }, cancellationPolicy: '{"version":"v1"}', guest: { name: '변경 테스트', email: 'change-test@example.com' },
}

async function mockCustomerApi(page: Page) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/hotels') return route.fulfill({ json: [hotel] })
    if (url.pathname === '/api/payments/mode') return route.fulfill({ json: { provider: 'toss-test', changeProvider: 'toss-test' } })
    if (url.pathname === '/api/hotels/sokcho/content') return route.fulfill({ json: {} })
    if (url.pathname === '/api/website/navigation') return route.fulfill({ json: [] })
    if (url.pathname === '/api/website/pages/resolve') return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
    if (url.pathname === '/api/availability') return route.fulfill({ json: { offers: [offer] } })
    if (url.pathname === '/api/reservations' && route.request().method() === 'POST') return route.fulfill({ status: 201, json: pendingReservation })
    if (url.pathname === '/api/reservations/reservation-123') return route.fulfill({ json: pendingReservation })
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
  })
}

test('홈 검색과 객실 선택', async ({ page }) => {
  await mockCustomerApi(page)
  await page.goto('/#booking')
  await page.getByRole('button', { name: '객실 검색' }).click()
  await expect(page).toHaveURL(/\/booking\/results\?/)
  await expect(page.getByRole('heading', { name: '예약 가능한 객실' })).toBeVisible()
  await expect(page.locator('#stays .offer-card')).toHaveCount(0)
  await page.getByRole('button', { name: /스탠다드 시티 선택/ }).click()
  await expect(page).toHaveURL('/booking/checkout')
})

test('390px에서 결과 화면은 가로로 넘치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockCustomerApi(page)
  await page.goto('/booking/results?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1')
  await expect(page.getByRole('heading', { name: '예약 가능한 객실' })).toBeVisible()
  expect(await page.locator('body').evaluate(body => body.scrollWidth <= window.innerWidth)).toBe(true)
})

test('키보드로 조건과 객실 선택에 도달한다', async ({ page }) => {
  await mockCustomerApi(page)
  await page.goto('/booking/results?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1')
  await expect(page.getByRole('heading', { name: '예약 가능한 객실' })).toBeVisible()
  await page.getByRole('button', { name: /검색 조건 수정/ }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: /검색 조건 수정/ })).toHaveAttribute('aria-expanded', 'true')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: /스테이 하늘 속초/ })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: /체크인/ })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: /성인 2/ })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: '조건 적용' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: /스탠다드 시티 선택/ })).toBeFocused()
})

test('영문 예약 경로를 결과와 선택 뒤에도 유지한다', async ({ page }) => {
  await mockCustomerApi(page)
  await page.goto('/en/booking/results?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1')
  await expect(page.getByRole('link', { name: /STAY HANEUL/ })).toHaveAttribute('href', '/en')
  await page.getByRole('button', { name: /Edit search/ }).click()
  await page.getByRole('button', { name: 'Apply search' }).click()
  await expect(page).toHaveURL(/\/en\/booking\/results\?/)
  await page.getByRole('button', { name: /스탠다드 시티 Select/ }).click()
  await expect(page).toHaveURL('/en/booking/checkout')
})

test('예약자 입력 뒤 서버 확보와 결제 재시도를 준비한다', async ({ page }) => {
  await mockCustomerApi(page)
  await page.goto('/booking/results?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1')
  await page.getByRole('button', { name: /스탠다드 시티 선택/ }).click()
  await page.getByLabel('예약자 이름').fill('변경 테스트')
  await page.getByLabel('이메일').fill('change-test@example.com')
  await page.getByRole('checkbox', { name: /예약 및 결제 서비스/ }).check()
  await page.getByRole('button', { name: '예약 및 결제 진행' }).click()
  await expect(page.getByText(/남은 확보 시간/)).toBeVisible()
  await expect(page.getByRole('button', { name: '토스 테스트 결제' })).toBeEnabled()
  await expect(page).not.toHaveURL(/change-test%40example\.com|managementToken|paymentKey/)
})

test('영문 fake 결제는 완료와 새로고침에서 서버 예약 상태를 확인한다', async ({ page }) => {
  await mockCustomerApi(page)
  let paid = false
  await page.route('**/api/payments/mode', route => route.fulfill({ json: { provider: 'fake' } }))
  await page.route('**/api/reservations/reservation-123', route => route.fulfill({ json: { ...pendingReservation, status: paid ? 'CONFIRMED' : 'PENDING_PAYMENT', paymentStatus: paid ? 'SUCCEEDED' : 'NOT_STARTED' } }))
  await page.route('**/api/reservations/reservation-123/test-payment', route => { paid = true; return route.fulfill({ json: { status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' } }) })
  await page.goto('/en/booking/results?hotelId=sokcho&checkIn=2026-09-22&checkOut=2026-09-24&adults=2&children=0&rooms=1')
  await page.getByRole('button', { name: /스탠다드 시티 Select/ }).click()
  await page.getByLabel('Guest name').fill('Test Guest')
  await page.getByLabel('Email').fill('test@example.com')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Continue to payment' }).click()
  await page.getByRole('button', { name: 'Complete test payment' }).click()
  await expect(page.getByRole('heading', { name: 'Your reservation is confirmed' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'View reservation' })).toHaveAttribute('href', '/en/reservations/reservation-123')
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Your reservation is confirmed' })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('hotel-chain.booking.checkout-progress.v1'))).toBeNull()
})
