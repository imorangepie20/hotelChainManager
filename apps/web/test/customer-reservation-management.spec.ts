import { expect, test, type Page } from '@playwright/test'

const id = '123e4567-e89b-12d3-a456-426614174000'
const access = { reservationId: id, managementToken: 'A'.repeat(43) }
const confirmed = { id, status: 'CONFIRMED', checkIn: '2026-09-22', checkOut: '2026-09-24', rooms: 1, expiresAt: '2026-09-22T10:10:00.000Z', total: 360000, currency: 'KRW', nightlyPrices: [], adults: 2, children: 0, ratePlanName: '유연 취소', cancellationPolicyDetails: { refundCutoffDaysBefore: 1, refundCutoffLocalTime: '18:00', timezone: 'Asia/Seoul' }, cancellationPolicy: '{"refundCutoffDaysBefore":1,"refundCutoffLocalTime":"18:00","timezone":"Asia/Seoul"}', guest: { name: '테스트 고객', email: 'guest@example.com' }, roomTypeName: '스탠다드 시티', paymentStatus: 'SUCCEEDED' }

async function storeAccess(page: Page, value: unknown = [access]) {
  await page.addInitScript(entries => sessionStorage.setItem('hotel-chain.booking.reservation-access.v1', JSON.stringify({ version: 1, accesses: entries })), value)
}

async function mockApi(page: Page, reservation = confirmed, options: { preview?: object; change?: object } = {}) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/hotels' || url.pathname === '/api/website/navigation') return route.fulfill({ json: [] })
    if (url.pathname === `/api/reservations/${id}`) return route.request().headers()['x-reservation-token'] === access.managementToken
      ? route.fulfill({ json: reservation }) : route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
    if (url.pathname === `/api/reservations/${id}/cancellation-preview`) return route.fulfill({ json: options.preview ?? { reservationId: id, status: 'CONFIRMED', cancellable: true, refundAmount: 360000, currency: 'KRW', cutoffAt: '2026-09-21T09:00:00.000Z', timezone: 'Asia/Seoul', unavailableReason: null } })
    if (url.pathname === `/api/reservations/${id}/cancel`) return route.fulfill({ json: { status: 'CANCELLED', refundAmount: 360000 } })
    if (url.pathname === `/api/reservations/${id}/change-summary`) return route.fulfill({ json: options.change ? { differenceKrw: 100000, refundStatus: null, ...options.change } : null })
    if (url.pathname === '/api/reservation-change-payments/current') return options.change ? route.fulfill({ json: options.change }) : route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
  })
}

test('같은 브라우저의 예약 목록과 상세를 표시한다', async ({ page }) => {
  await storeAccess(page); await mockApi(page)
  await page.goto('/reservations')
  await expect(page.getByRole('heading', { name: '내 예약' })).toBeVisible()
  await page.getByRole('link', { name: /스탠다드 시티 내 예약/ }).click()
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
  await expect(page.getByText('예상 환불액 ₩360,000')).toBeVisible()
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
  await storeAccess(page); await mockApi(page, confirmed, { change: { reservationId: id, reservationNumberSuffix: id.slice(-8), checkIn: '2026-09-24', checkOut: '2026-09-26', roomTypeName: '디럭스 오션', ratePlanName: '유연 취소', additionalAmountKrw: 100000, currency: 'KRW', expiresAt: '2026-09-20T10:00:00.000Z', environmentLabel: '테스트 결제', status: 'AWAITING_PAYMENT' } })
  await page.goto(`/reservations/${id}`)
  await expect(page.getByText('추가 결제 대기')).toBeVisible()
  await expect(page.getByRole('link', { name: '추가 결제 계속하기' })).toHaveAttribute('href', '/reservation-change-payment')
})

test('같은 suffix의 다른 예약 변경 세션은 연결하지 않는다', async ({ page }) => {
  await storeAccess(page); await mockApi(page, confirmed, { change: { reservationId: `aaaaaaaa-aaaa-aaaa-aaaa-${id.slice(-12)}`, reservationNumberSuffix: id.slice(-8), checkIn: '2026-09-24', checkOut: '2026-09-26', roomTypeName: '디럭스 오션', ratePlanName: '유연 취소', additionalAmountKrw: 100000, currency: 'KRW', expiresAt: '2026-09-20T10:00:00.000Z', environmentLabel: '테스트 결제', status: 'AWAITING_PAYMENT' } })
  await page.goto(`/reservations/${id}`)
  await expect(page.getByRole('link', { name: '추가 결제 계속하기' })).toHaveCount(0)
})

test('예약 변경 결제는 기존·변경 예약과 서버 상태를 함께 표시한다', async ({ page }) => {
  const token = 'A'.repeat(43)
  const change = {
    reservationId: id, reservationNumberSuffix: id.slice(-8),
    previousCheckIn: '2026-09-22', previousCheckOut: '2026-09-24', previousRoomTypeName: '스탠다드 시티', previousRatePlanName: '룸 온리', previousTotalKrw: 360000,
    checkIn: '2026-09-24', checkOut: '2026-09-26', roomTypeName: '디럭스 오션', ratePlanName: '유연 취소', totalKrw: 460000, differenceKrw: 100000,
    additionalAmountKrw: 100000, currency: 'KRW', expiresAt: '2026-09-20T10:00:00.000Z', environmentLabel: '테스트 결제', status: 'AWAITING_PAYMENT',
  }
  await page.route('**/api/**', route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/reservation-change-payments/session') return route.fulfill({ status: 204 })
    if (url.pathname === '/api/reservation-change-payments/current') return route.fulfill({ json: change })
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
  })
  await page.goto(`/reservation-change-payment#${token}`)
  await expect(page.getByRole('term', { name: '기존 예약' })).toBeVisible()
  await expect(page.getByRole('term', { name: '변경 예약' })).toBeVisible()
  await expect(page.getByText('추가 결제 100,000원')).toBeVisible()
  await expect(page).toHaveURL(/\/reservation-change-payment$/)
})

test('예약 변경 결제는 만료와 조정 필요 상태에서 결제를 다시 시작하지 않는다', async ({ page }) => {
  for (const status of ['EXPIRED', 'RECONCILIATION_REQUIRED', 'COMPLETED']) {
    await page.route('**/api/**', route => {
      const url = new URL(route.request().url())
      if (url.pathname === '/api/reservation-change-payments/current') return route.fulfill({ json: {
        reservationId: id, reservationNumberSuffix: id.slice(-8), previousCheckIn: '2026-09-22', previousCheckOut: '2026-09-24', previousRoomTypeName: '스탠다드 시티', previousRatePlanName: '룸 온리', previousTotalKrw: 360000,
        checkIn: '2026-09-24', checkOut: '2026-09-26', roomTypeName: '디럭스 오션', ratePlanName: '유연 취소', totalKrw: 460000, differenceKrw: 100000,
        additionalAmountKrw: 100000, currency: 'KRW', expiresAt: '2026-09-20T10:00:00.000Z', environmentLabel: '테스트 결제', status,
      } })
      return route.fulfill({ status: 204 })
    })
    await page.goto('/reservation-change-payment')
    await expect(page.getByRole('button', { name: /결제하기|Pay now/ })).toHaveCount(0)
    await page.unroute('**/api/**')
  }
})

test('같은 날짜와 금액의 객실·요금제 변경도 지연된 상세와 취소 미리보기를 갱신한다', async ({ page }) => {
  await storeAccess(page)
  await mockApi(page)
  let summaryReads = 0
  let reservationReads = 0
  let previewReads = 0
  await page.route(`**/api/reservations/${id}/change-summary`, route => route.fulfill({ json: {
    reservationId: id, status: ++summaryReads === 1 ? 'APPLYING' : 'COMPLETED', checkIn: confirmed.checkIn, checkOut: confirmed.checkOut,
    roomTypeName: '디럭스 오션', ratePlanName: '룸 온리', differenceKrw: 0, currency: 'KRW', expiresAt: '2026-09-20T10:00:00Z', refundStatus: null,
  } }))
  await page.route(`**/api/reservations/${id}`, async route => {
    if (++reservationReads === 1) return route.fulfill({ json: confirmed })
    await new Promise(resolve => setTimeout(resolve, 50))
    return route.fulfill({ json: { ...confirmed, roomTypeName: '디럭스 오션', ratePlanName: '룸 온리' } })
  })
  await page.route(`**/api/reservations/${id}/cancellation-preview`, route => { previewReads += 1; return route.fulfill({ json: {
    reservationId: id, status: 'CONFIRMED', cancellable: true, refundAmount: confirmed.total,
    currency: 'KRW', cutoffAt: '2026-09-23T09:00:00Z', timezone: 'Asia/Seoul', unavailableReason: null,
  } }) })
  await page.goto(`/reservations/${id}`)
  await expect(page.getByRole('heading', { name: '스탠다드 시티' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '변경 완료' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '디럭스 오션' })).toBeVisible()
  await expect(page.getByText(/예상 환불액 ₩360,000/)).toBeVisible()
  await expect(page.getByRole('button', { name: '예약 취소' })).toBeVisible()
  expect(previewReads).toBe(2)
})
