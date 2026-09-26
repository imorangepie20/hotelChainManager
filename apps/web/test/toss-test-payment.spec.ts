import { expect, test } from '@playwright/test'

const id = '12345678-1234-1234-1234-123456789abc'
const token = 'A'.repeat(43)
const origin = process.env.CUSTOMER_WEB_BASE_URL ?? 'http://127.0.0.1:4000'
const resultPath = `/reservations/${id}/payment-result`
const checkout = { orderId: 'test-order', amountKrw: 120000, currency: 'KRW', clientKey: 'test_ck_fixture', successUrl: `${origin}${resultPath}?result=success`, failUrl: `${origin}${resultPath}?result=fail`, environmentLabel: '토스 테스트 결제' }
const reservation = { id, status: 'PENDING_PAYMENT', checkIn: '2026-10-10', checkOut: '2026-10-12', rooms: 1, expiresAt: '2026-10-10T00:00:00Z', total: 120000, currency: 'KRW', nightlyPrices: [], cancellationPolicy: '무료 취소', guest: { name: '테스트', email: 'test@example.com' } }
const change = { reservationId: id, previousCheckIn: '2026-10-10', previousCheckOut: '2026-10-12', previousRoomTypeName: '스탠다드', previousRatePlanName: '룸 온리', previousTotalKrw: 120000, totalKrw: 220000, differenceKrw: 100000, reservationNumberSuffix: '12ab34cd', checkIn: '2026-10-10', checkOut: '2026-10-12', roomTypeName: '디럭스 오션', ratePlanName: '조식 포함', additionalAmountKrw: 100000, currency: 'KRW', expiresAt: '2026-10-01T03:15:00Z', environmentLabel: '테스트 결제', status: 'AWAITING_PAYMENT' }

test.beforeEach(async ({ page }) => {
  await page.route('**/api/payments/mode', route => route.fulfill({ json: { provider: 'toss-test', changeProvider: 'toss-test' } }))
  await page.addInitScript(({ id, token }) => {
    sessionStorage.setItem('latestReservation', id)
    sessionStorage.setItem(`reservation:${id}`, token)
    const selection = { criteria: { hotelId: 'sokcho', checkIn: '2026-10-10', checkOut: '2026-10-12', adults: 2, children: 0, rooms: 1 }, roomTypeId: 'standard', ratePlanId: 'flex' }
    sessionStorage.setItem('hotel-chain.booking.selection.v1', JSON.stringify({ version: 1, selection }))
    sessionStorage.setItem('hotel-chain.booking.checkout-progress.v1', JSON.stringify({ version: 1, selection, access: { reservationId: id, managementToken: token }, summary: { roomTypeName: '스탠다드', ratePlanName: '유연 취소' } }))
  }, { id, token })
  await page.route('**/api/hotels', route => route.fulfill({ json: [] }))
  await page.route('**/api/website/**', route => route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } }))
  await page.route(`**/api/reservations/${id}`, route => route.fulfill({ json: reservation }))
  await page.route('**/api/reservations/*/change-summary', route => route.fulfill({ status: 204 }))
  await page.route('**/api/reservation-change-payments/current', route => route.fulfill({ json: change }))
})

test('신규 SDK는 서버 주문만 사용하며 이중 클릭과 모바일 키보드에 안전하다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  let requests = 0
  await page.route(`**/api/reservations/${id}/payment-checkout`, async route => { requests++; expect(route.request().headers()['x-reservation-token']).toBe(token); await route.fulfill({ json: checkout }) })
  await page.route('https://js.tosspayments.com/v2/standard', route => route.fulfill({ contentType: 'text/javascript', body: `window.TossPayments = Object.assign(key => ({payment: options => ({requestPayment: input => {window.sdkInput = {key, options, input}; return new Promise(() => {})}})}), {ANONYMOUS: 'ANONYMOUS'});` }))
  await page.goto('/booking/checkout')
  await expect(page.getByRole('button', { name: '테스트 결제', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '결제 실패 시험', exact: true })).toHaveCount(0)
  const button = page.getByRole('button', { name: '토스 테스트 결제', exact: true })
  expect(await button.evaluate(el => el.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44)
  await button.focus(); await page.keyboard.press('Enter'); await page.keyboard.press('Enter')
  await expect.poll(() => page.evaluate(() => (window as unknown as { sdkInput: unknown }).sdkInput)).toEqual({ key: 'test_ck_fixture', options: { customerKey: 'ANONYMOUS' }, input: { method: 'CARD', amount: { currency: 'KRW', value: 120000 }, orderId: 'test-order', orderName: '호텔 예약 테스트 결제', successUrl: checkout.successUrl, failUrl: checkout.failUrl } })
  expect(requests).toBe(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
})

test('복귀 query 즉시 제거 후 권한으로 승인하고 새로고침은 상태만 조회한다', async ({ page }) => {
  let confirms = 0
  await page.route(`**/api/reservations/${id}/payment-confirm`, async route => {
    confirms++; expect(page.url()).toBe(`${origin}${resultPath}`)
    expect(route.request().postDataJSON()).toEqual({ paymentKey: 'pk', orderId: 'test-order', amountKrw: 120000 })
    expect(route.request().headers()['x-reservation-token']).toBe(token)
    await route.fulfill({ json: { reservationId: id, orderId: 'test-order', status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' } })
  })
  await page.route(`**/api/reservations/${id}/payment-status`, route => route.fulfill({ json: { reservationId: id, orderId: 'test-order', status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' } }))
  await page.goto(`${resultPath}?result=success&paymentKey=pk&orderId=test-order&amount=120000`)
  await expect(page.getByText('결제가 확인되어 예약이 확정되었습니다.', { exact: true })).toBeVisible()
  expect(confirms).toBe(1)
  await page.reload()
  await expect(page.getByText('결제가 확인되어 예약이 확정되었습니다.', { exact: true })).toBeVisible()
  expect(confirms).toBe(1)
})

test('UNKNOWN 복귀는 성공으로 표시하지 않고 서버 상태 재확인이 가능하다', async ({ page }) => {
  const status = { reservationId: id, orderId: 'test-order', status: 'PENDING_PAYMENT', paymentStatus: 'UNKNOWN' }
  await page.route(`**/api/reservations/${id}/payment-confirm`, route => route.fulfill({ json: status }))
  await page.route(`**/api/reservations/${id}/payment-reconcile`, route => route.fulfill({ json: { ...status, status: 'CONFIRMED', paymentStatus: 'SUCCEEDED' } }))
  await page.goto(`${resultPath}?result=success&paymentKey=pk&orderId=test-order&amount=120000`)
  await expect(page.getByText(/결제 결과를 확인 중입니다|결제 결과를 확인 중/)).toBeVisible()
  await page.getByRole('button', { name: '서버 상태 다시 확인' }).click()
  await expect(page.getByText('결제가 확인되어 예약이 확정되었습니다.', { exact: true })).toBeVisible()
})

test('권한 유실은 주문만으로 승인하지 않으며 오류에 초점을 둔다', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.clear())
  let confirms = 0
  await page.route('**/payment-confirm', async route => { confirms++; await route.abort() })
  await page.goto(`${resultPath}?result=success&paymentKey=pk&orderId=test-order&amount=120000`)
  await expect(page.getByRole('alert')).toContainText('예약을 진행한 같은 브라우저')
  await expect(page.getByRole('alert')).toBeFocused()
  expect(confirms).toBe(0)
  await expect(page).toHaveURL(`${origin}${resultPath}`)
})

test('실패 복귀는 승인하지 않고 실패 원문을 화면에 반사하지 않는다', async ({ page }) => {
  let confirms = 0
  await page.route('**/payment-confirm', async route => { confirms++; await route.abort() })
  await page.route(`**/api/reservations/${id}/payment-status`, route => route.fulfill({ json: { status: 'PENDING_PAYMENT', paymentStatus: 'NEW' } }))
  await page.goto(`${resultPath}?result=fail&message=sensitive&code=USER_CANCEL`)
  await expect(page.getByText(/결제창에서 결제를 완료하지 않았습니다/)).toBeVisible()
  await expect(page.getByText('sensitive')).toHaveCount(0)
  expect(confirms).toBe(0)
})

test('변경 결제 Strict 세션 복귀는 clean URL의 자사 API로 승인한다', async ({ page }) => {
  await page.route('**/api/reservation-change-payments/current/toss/status', route => route.fulfill({ json: { status: 'AWAITING_PAYMENT', paymentStatus: 'UNKNOWN' } }))
  await page.context().addCookies([{ name: 'reservation_change_session', value: 'test-session', domain: new URL(origin).hostname, path: '/api/reservation-change-payments', httpOnly: true, sameSite: 'Strict' }])
  await page.route('**/api/reservation-change-payments/current/toss/confirm', async route => {
    expect(page.url()).toBe(`${origin}/reservation-change-payment`)
    expect(route.request().headers().cookie).toContain('reservation_change_session=test-session')
    await route.fulfill({ json: { status: 'AWAITING_PAYMENT', paymentStatus: 'UNKNOWN' } })
  })
  await page.goto('/reservation-change-payment?result=success&paymentKey=pk&orderId=change-order&amount=100000')
  await expect(page.getByText(/결제 결과를 확인 중입니다|결제 결과를 확인 중/)).toBeVisible()
  await expect(page.getByRole('button', { name: '토스 테스트 결제', exact: true })).toHaveCount(0)
  await page.reload()
  await expect(page.getByText(/결제 결과를 확인 중입니다|결제 결과를 확인 중/)).toBeVisible()
  await expect(page.getByRole('button', { name: '토스 테스트 결제', exact: true })).toHaveCount(0)
})

test('live 키는 SDK를 로드하지 않고 오류에 초점을 둔다', async ({ page }) => {
  let sdkRequests = 0
  await page.route('https://js.tosspayments.com/**', async route => { sdkRequests++; await route.abort() })
  await page.route(`**/api/reservations/${id}/payment-checkout`, route => route.fulfill({ json: { ...checkout, clientKey: 'live_ck_invalid' } }))
  await page.goto('/booking/checkout')
  await page.getByRole('button', { name: '토스 테스트 결제', exact: true }).click()
  await expect(page.getByRole('alert')).toBeFocused()
  expect(sdkRequests).toBe(0)
})

test('변경 결제 세션 만료는 재인증을 요청하며 주문만으로 재시도하지 않는다', async ({ page }) => {
  await page.route('**/api/reservation-change-payments/current/toss/confirm', route => route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED', message: '만료' } }))
  await page.goto('/reservation-change-payment?result=success&paymentKey=pk&orderId=change-order&amount=100000')
  await expect(page.getByRole('alert')).toContainText('만료')
  await expect(page.getByRole('alert')).toBeFocused()
  await expect(page.getByRole('button', { name: '토스 테스트 결제', exact: true })).toHaveCount(0)
})

test('변경 fragment 교환과 fake 결제는 기존 흐름을 유지한다', async ({ page }) => {
  await page.route('**/api/payments/mode', route => route.fulfill({ json: { provider: 'fake', changeProvider: 'fake' } }))
  let exchanges = 0
  await page.route('**/api/reservation-change-payments/session', async route => { exchanges++; expect(page.url()).toBe(`${origin}/reservation-change-payment`); await route.fulfill({ status: 204 }) })
  await page.route('**/api/reservation-change-payments/current/checkout', route => route.fulfill({ json: { checkoutUrl: `${origin}/fake-checkout` } }))
  await page.route('**/fake-checkout', route => route.fulfill({ contentType: 'text/html', body: '<h1>Fake checkout</h1>' }))
  await page.goto(`/reservation-change-payment#${token}`)
  await expect(page.getByText(/디럭스 오션/)).toBeVisible()
  expect(exchanges).toBe(1)
  await expect(page.getByRole('button', { name: '토스 테스트 결제', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '100,000원 결제하기' }).click()
  await expect(page.getByRole('heading', { name: 'Fake checkout' })).toBeVisible()
})

test('변경 토스 모드는 SDK만 표시하고 legacy 자사 URL로 이동하지 않는다', async ({ page }) => {
  await page.goto('/reservation-change-payment')
  await expect(page.getByRole('button', { name: '토스 테스트 결제', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '100,000원 결제하기', exact: true })).toHaveCount(0)
})

test('승인 요청이 서버 도달 전에 유실되면 NEW 확인 후 메모리 callback으로만 재시도한다', async ({ page }) => {
  let confirms = 0
  await page.route(`**/api/reservations/${id}/payment-confirm`, async route => {
    confirms++
    if (confirms === 1) await route.abort()
    else await route.fulfill({ json: { status: 'CONFIRMED', paymentStatus: 'SUCCEEDED', orderId: 'test-order' } })
  })
  await page.route(`**/api/reservations/${id}/payment-reconcile`, route => route.fulfill({ json: { status: 'PENDING_PAYMENT', paymentStatus: 'NEW', orderId: 'test-order' } }))
  await page.goto(`${resultPath}?result=success&paymentKey=pk&orderId=test-order&amount=120000`)
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByRole('button', { name: '서버 상태 다시 확인' }).click()
  await expect(page.getByText('결제가 확인되어 예약이 확정되었습니다.', { exact: true })).toBeVisible()
  expect(confirms).toBe(2)
})

test('고객 전체 취소 pending을 완료로 표시하지 않는다', async ({ page }) => {
  await page.route(`**/api/reservations/${id}`, route => route.fulfill({ json: { ...reservation, status: 'CONFIRMED' } }))
  await page.route(`**/api/reservations/${id}/cancel`, route => route.fulfill({ json: { status: 'CANCELLATION_PENDING', refundAmount: 120000 } }))
  await page.route(`**/api/reservations/${id}/cancellation-preview`, route => route.fulfill({ json: { reservationId: id, status: 'CONFIRMED', cancellable: true, refundAmount: 120000, currency: 'KRW', cutoffAt: '2026-10-09T09:00:00Z', timezone: 'Asia/Seoul', unavailableReason: null } }))
  await page.goto(`/reservations/${id}`)
  await page.getByRole('button', { name: '예약 취소', exact: true }).click()
  await page.getByRole('button', { name: '예약 취소 확정', exact: true }).click()
  await expect(page.getByText(/환불 결과를 확인 중/)).toBeVisible()
  await expect(page.getByText(/예약이 취소되었습니다/)).toHaveCount(0)
})

test('저장 초안 미리보기는 기존 예약의 결제 진입을 차단한다', async ({ page }) => {
  let checkoutRequests = 0
  await page.route('**/payment-checkout', async route => { checkoutRequests++; await route.abort() })
  await page.goto(`/#preview=${token}`)
  await expect(page.getByRole('button', { name: '토스 테스트 결제', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '테스트 결제', exact: true })).toHaveCount(0)
  expect(checkoutRequests).toBe(0)
})

for (const failure of ['timeout', 'server-error'] as const) {
  test(`변경 최초 승인 ${failure} 후 상세 없이 상태 복구하고 완료를 우선 표시한다`, async ({ page }) => {
    let confirms = 0
    let statusReads = 0
    await page.route('**/api/reservation-change-payments/current/toss/confirm', route => {
      confirms++
      return failure === 'timeout' ? route.abort('timedout') : route.fulfill({ status: 503, json: { code: 'UNAVAILABLE', message: '일시 장애' } })
    })
    await page.route('**/api/reservation-change-payments/current/toss/status', route => {
      statusReads++
      return route.fulfill({ json: { status: 'AWAITING_PAYMENT', paymentStatus: 'UNKNOWN' } })
    })
    await page.route('**/api/reservation-change-payments/current', route => route.fulfill({ json: { ...change, status: 'COMPLETED' } }))
    await page.goto('/reservation-change-payment?result=success&paymentKey=pk&orderId=change-order&amount=100000')
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('button', { name: '토스 테스트 결제', exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: '서버 상태 다시 확인' }).click()
    await expect(page.getByText('예약 변경이 완료되었습니다', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '예약 상세 보기' })).toBeVisible()
    await expect(page.getByText('결제 결과를 확인 중입니다', { exact: true })).toHaveCount(0)
    expect(confirms).toBe(1)
    expect(statusReads).toBe(1)
  })
}
