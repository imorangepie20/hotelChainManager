import { expect, test } from '@playwright/test'

const token = 'A'.repeat(43)
const payment = {
  reservationNumberSuffix: '12ab34cd',
  checkIn: '2026-10-10',
  checkOut: '2026-10-12',
  roomTypeName: '디럭스 오션',
  ratePlanName: '조식 포함',
  additionalAmountKrw: 100000,
  currency: 'KRW',
  expiresAt: '2026-10-01T03:15:00Z',
  environmentLabel: '테스트 결제',
  status: 'AWAITING_PAYMENT',
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/payments/mode', route => route.fulfill({ json: { provider: 'fake', changeProvider: 'fake' } }))
  await page.route('**/api/reservation-change-payments/current', route => route.fulfill({ json: payment }))
  await page.route('**/api/reservation-change-payments/current/checkout', route => route.fulfill({
    json: { checkoutUrl: 'http://localhost:4000/fake-checkout' },
  }))
  await page.route('**/fake-checkout', route => route.fulfill({
    contentType: 'text/html; charset=utf-8',
    body: '<main><h1>테스트 결제 화면</h1></main>',
  }))
})

test('fragment를 한 번만 교환하고 최소 결제 정보만 표시한다', async ({ page }) => {
  let exchanges = 0
  let unrelatedRequests = 0
  await page.route('**/api/reservation-change-payments/session', async route => {
    exchanges++
    expect(route.request().headers()['x-reservation-change-token']).toBe(token)
    await route.fulfill({ status: 204, headers: { 'Set-Cookie': 'reservation_change_session=session; Path=/api/reservation-change-payments; HttpOnly; SameSite=Strict' } })
  })
  page.on('request', request => {
    if (/\/api\/(hotels|website|availability)/.test(request.url())) unrelatedRequests++
  })

  await page.goto(`/reservation-change-payment#${token}`)

  await expect(page).toHaveURL(/\/reservation-change-payment$/)
  await expect(page.getByText('•••• 12ab34cd')).toBeVisible()
  await expect(page.getByText('디럭스 오션')).toBeVisible()
  await expect(page.getByText('100,000원', { exact: true })).toBeVisible()
  expect(exchanges).toBe(1)
  expect(unrelatedRequests).toBe(0)

  await page.reload()
  await expect(page.getByText('•••• 12ab34cd')).toBeVisible()
  expect(exchanges).toBe(1)
})

test('390px에서 가로 넘침 없이 키보드로 결제를 연다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/reservation-change-payments/session', route => route.fulfill({ status: 204 }))
  await page.goto(`/reservation-change-payment#${token}`)

  const action = page.getByRole('button', { name: '100,000원 결제하기' })
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
  expect(overflow).toBe(false)
  await action.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '테스트 결제 화면' })).toBeVisible()
})
