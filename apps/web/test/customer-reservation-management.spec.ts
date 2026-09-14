import { expect, test, type Page } from '@playwright/test'

const id = '123e4567-e89b-12d3-a456-426614174000'
const access = { reservationId: id, managementToken: 'A'.repeat(43) }
const confirmed = { id, status: 'CONFIRMED', checkIn: '2026-09-22', checkOut: '2026-09-24', rooms: 1, expiresAt: '2026-09-22T10:10:00.000Z', total: 360000, currency: 'KRW', nightlyPrices: [], cancellationPolicy: '체크인 1일 전 18:00까지 전액 환불', guest: { name: '테스트 고객', email: 'guest@example.com' }, roomTypeName: '스탠다드 시티', paymentStatus: 'SUCCEEDED' }

async function storeAccess(page: Page, value: unknown = [access]) {
  await page.addInitScript(entries => sessionStorage.setItem('hotel-chain.booking.reservation-access.v1', JSON.stringify({ version: 1, accesses: entries })), value)
}

async function mockApi(page: Page, reservation = confirmed, options: { preview?: object; change?: object } = {}) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/hotels' || url.pathname === '/api/website/navigation') return route.fulfill({ json: [] })
    if (url.pathname === `/api/reservations/${id}`) return route.request().headers()['x-reservation-token'] === access.managementToken
      ? route.fulfill({ json: reservation }) : route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
    if (url.pathname === `/api/reservations/${id}/cancellation-preview`) return route.fulfill({ json: options.preview ?? { reservationId: id, status: 'CONFIRMED', cancellable: true, refundAmount: 360000, currency: 'KRW', cutoffAt: '2026-09-21T09:00:00.000Z', unavailableReason: null } })
    if (url.pathname === `/api/reservations/${id}/cancel`) return route.fulfill({ json: { status: 'CANCELLED', refundAmount: 360000 } })
    if (url.pathname === '/api/reservation-change-payments/current') return options.change ? route.fulfill({ json: options.change }) : route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
  })
}

test('같은 브라우저의 예약 목록과 상세를 표시한다', async ({ page }) => {
  await storeAccess(page); await mockApi(page)
  await page.goto('/reservations')
  await expect(page.getByRole('heading', { name: '내 예약' })).toBeVisible()
  await page.getByRole('link', { name: /스탠다드 시티 예약 상세/ }).click()
  await expect(page.getByText('예약 확정')).toBeVisible()
  await expect(page.getByRole('button', { name: '예약 취소' })).toBeVisible()
})

test('빈 기록과 접근 불가 토큰은 예약 존재를 노출하지 않는다', async ({ page }) => {
  await mockApi(page); await page.goto('/reservations')
  await expect(page.getByText('이 브라우저에서 확인할 예약이 없습니다')).toBeVisible()
  await storeAccess(page, [{ reservationId: id, managementToken: 'B'.repeat(43) }]); await page.reload()
  await expect(page.getByText('이 브라우저에서 확인할 예약이 없습니다')).toBeVisible()
})

test('확정 예약은 환불 예상액을 확인한 뒤 취소를 확인한다', async ({ page }) => {
  await storeAccess(page); await mockApi(page)
  await page.goto(`/reservations/${id}`)
  await expect(page.getByText('예상 환불액 360,000원')).toBeVisible()
  await page.getByRole('button', { name: '예약 취소' }).click()
  await expect(page.getByRole('dialog')).toContainText('예약을 취소할까요?')
  await page.getByRole('button', { name: '돌아가기' }).click()
  await expect(page.getByRole('button', { name: '예약 취소' })).toBeFocused()
})

test('취소 처리 중, 취소 완료, 만료 상태에는 서버가 허용한 행동만 표시한다', async ({ page }) => {
  for (const [status, label] of [['CANCELLING', '취소 처리 중'], ['CANCELLED', '취소 완료'], ['EXPIRED', '만료']] as const) {
    await storeAccess(page); await mockApi(page, { ...confirmed, status })
    await page.goto(`/reservations/${id}`)
    await expect(page.getByText(label)).toBeVisible()
    await expect(page.getByRole('button', { name: '예약 취소' })).toHaveCount(0)
  }
})

test('현재 고객 변경 세션의 추가 결제만 상세에서 계속한다', async ({ page }) => {
  await storeAccess(page); await mockApi(page, confirmed, { change: { reservationNumberSuffix: id.slice(-8), checkIn: '2026-09-24', checkOut: '2026-09-26', roomTypeName: '디럭스 오션', ratePlanName: '유연 취소', additionalAmountKrw: 100000, currency: 'KRW', expiresAt: '2026-09-20T10:00:00.000Z', environmentLabel: '테스트 결제', status: 'AWAITING_PAYMENT' } })
  await page.goto(`/reservations/${id}`)
  await expect(page.getByText('추가 결제 대기')).toBeVisible()
  await expect(page.getByRole('link', { name: '추가 결제 계속하기' })).toHaveAttribute('href', '/reservation-change-payment')
})
