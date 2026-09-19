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

// AI 도우미 응답. policy는 AI가 가격·재고·예약 확정을 만들지 않는다는 안내문이다.
const conciergeReply = {
  reply: '실시간 조회 결과 스탠다드 시티을 제안합니다. 표시된 금액과 잔여 객실은 예약 화면에서 다시 확인할 수 있습니다.',
  nextAction: 'RECOMMEND',
  criteria: {
    region: '속초',
    checkIn: '2026-09-22',
    checkOut: '2026-09-24',
    adults: 2,
    children: 0,
    rooms: 1,
    breakfastIncluded: false,
  },
  offers: [offer],
  policy: 'AI는 Spring Boot 예약 API가 반환한 객실·가격·잔여 수만 안내한다.',
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
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
  })
}

async function openConcierge(page: Page) {
  await page.getByRole('button', { name: 'AI 예약 도우미' }).click()
  await expect(page.getByRole('dialog', { name: 'AI 예약 도우미' })).toBeVisible()
}

test('AI 대화에서 추천을 적용하면 최신 객실만 예약 카드에 표시한다', async ({ page }) => {
  await mockCustomerApi(page)
  await page.route('**/chat', route => route.fulfill({ json: conciergeReply }))
  await page.goto('/#booking')

  await openConcierge(page)
  await page.getByLabel('예약 조건 입력').fill('2026-09-22부터 2박, 속초 성인 두 명')
  await page.getByRole('button', { name: '메시지 보내기' }).click()

  // AI 응답 정책 안내문과 후보가 노출된다.
  await expect(page.getByText('실시간 조회 결과 스탠다드 시티')).toBeVisible()
  await expect(page.getByText('₩360,000')).toBeVisible()
  await expect(page.getByText('잔여 3실')).toBeVisible()

  // 적용하면 AI 후보를 복사하지 않고 Spring availability를 다시 조회한다.
  const availability = page.waitForResponse(response => response.url().includes('/api/availability'))
  await page.getByRole('button', { name: '이 조건으로 객실 검색' }).click()
  expect((await availability).ok()).toBe(true)
  await expect(page.getByRole('heading', { name: '예약 가능한 객실' })).toBeVisible()
  await expect(page.getByRole('button', { name: /스탠다드 시티 선택/ })).toBeVisible()
})

test('AI가 누락된 조건을 묻고 390px에서 가로로 넘치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockCustomerApi(page)
  await page.route('**/chat', route => route.fulfill({
    json: {
      reply: '정확한 추천을 위해 체크인 날짜, 체크아웃 날짜를 알려주세요.',
      nextAction: 'ASK',
      criteria: { region: '속초', adults: 2 },
      offers: [],
      policy: '',
    },
  }))
  await page.goto('/#booking')

  await openConcierge(page)
  await page.getByLabel('예약 조건 입력').fill('속초 성인 두 명')
  await page.getByRole('button', { name: '메시지 보내기' }).click()
  await expect(page.getByText('체크인 날짜, 체크아웃 날짜를 알려주세요.')).toBeVisible()

  expect(await page.locator('body').evaluate(body => body.scrollWidth <= window.innerWidth)).toBe(true)
})

test('AI 도우미 오류는 직접 검색 안내로 끝난다', async ({ page }) => {
  await mockCustomerApi(page)
  await page.route('**/chat', route => route.fulfill({ status: 502, json: { detail: '예약 정보를 조회하지 못했습니다.' } }))
  await page.goto('/#booking')

  await openConcierge(page)
  await page.getByLabel('예약 조건 입력').fill('2026-09-22부터 2박, 속초 성인 두 명')
  await page.getByRole('button', { name: '메시지 보내기' }).click()
  await expect(page.getByRole('alert')).toHaveText('도우미에 연결하지 못했습니다. 직접 객실 검색을 이용해 주세요.')
})

test('정책 위반 400은 연결 장애와 다른 안내를 보여준다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockCustomerApi(page)
  await page.route('**/chat', route => route.fulfill({ status: 400, json: { detail: 'AI는 payment 조건을 처리할 수 없습니다.' } }))
  await page.goto('/#booking')

  await openConcierge(page)
  await page.getByLabel('예약 조건 입력').fill('결제해 주고 예약 확정해 줘')
  await page.getByRole('button', { name: '메시지 보내기' }).click()

  // 서버의 정책 메시지 원문이 아니라 고객 행동 안내가 노출된다.
  await expect(page.getByRole('alert')).toHaveText('도우미가 처리할 수 없는 조건입니다. 객실 검색에서 직접 선택해 주세요.')
  expect(await page.locator('body').evaluate(body => body.scrollWidth <= window.innerWidth)).toBe(true)
})
