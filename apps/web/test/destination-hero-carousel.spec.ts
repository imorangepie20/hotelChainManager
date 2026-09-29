import { expect, test, type Page } from '@playwright/test'

const hotel = {
  id: 'sokcho',
  name: '스테이 하늘 속초',
  region: '속초',
  timezone: 'Asia/Seoul',
}

const assetIds = Array.from({ length: 5 }, (_, index) => `723e4567-e89b-12d3-a456-42661417400${index}`)
const heroSlides = assetIds.map((assetId, index) => ({
  assetId,
  image: `/api/website/media/${assetId}/content`,
  alt: `속초 메인 이미지 ${index + 1}`,
}))

const landing = {
  title: '파도와 설악의 사이에서\n가장 느린 하루를',
  description: '동해의 수평선과 설악의 능선을 한눈에 담는 휴식.',
  eyebrow: 'SOKCHO · EAST SEA',
  heroAssetId: heroSlides[0].assetId,
  heroImage: heroSlides[0].image,
  heroAlt: heroSlides[0].alt,
  heroSlides,
  mediaVariants: Object.fromEntries(assetIds.map((assetId) => [assetId, [{
    targetWidth: 1280,
    deliveryUrl: `/api/website/media/${assetId}/variants/1280.webp`,
    mimeType: 'image/webp',
  }]])),
}

const transparentPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
)

async function mockLanding(page: Page) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/hotels') return route.fulfill({ json: [hotel] })
    if (url.pathname === '/api/hotels/sokcho/content') return route.fulfill({ json: landing })
    if (url.pathname === '/api/website/navigation') return route.fulfill({ json: [] })
    if (url.pathname === '/api/website/pages/resolve') return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
    if (/^\/api\/website\/media\/[0-9a-f-]+\/(content|variants\/1280\.webp)$/.test(url.pathname)) {
      return route.fulfill({ body: transparentPng, contentType: 'image/png' })
    }
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '없음' } })
  })
}

async function currentSlide(page: Page, position: number) {
  await expect(page.getByRole('button', { name: `메인 이미지 ${position} 보기` })).toHaveAttribute('aria-current', 'true')
  await expect(page.getByText(`메인 이미지 ${position}/5: 속초 메인 이미지 ${position}`, { exact: true })).toBeAttached()
}

test('moves through five hero images without moving the hero copy', async ({ page }) => {
  await mockLanding(page)
  await page.goto('/')

  const carousel = page.getByRole('region', { name: '리조트 메인 이미지' })
  const heading = page.getByRole('heading', { name: /파도와 설악의 사이에서/ })
  await expect(carousel).toBeVisible()
  await expect(heading).toBeVisible()
  await currentSlide(page, 1)
  await expect(carousel.locator('[aria-live]').last()).toHaveAttribute('aria-live', 'off')
  await expect(page.locator('link[rel="preload"][as="image"]')).toHaveAttribute('href', heroSlides[1].image)

  await carousel.getByRole('button', { name: '다음 이미지' }).click()
  await expect(carousel.locator('[aria-live]').last()).toHaveAttribute('aria-live', 'polite')
  await expect(carousel.locator('.hero-carousel-tile .is-incoming img').first()).toHaveAttribute(
    'srcset',
    `/api/website/media/${assetIds[1]}/variants/1280.webp 1280w`,
  )
  await expect(carousel.locator('.hero-carousel-tile .is-incoming img').first()).toHaveAttribute('sizes', '100vw')
  await currentSlide(page, 2)
  await expect(heading).toBeVisible()

  await carousel.getByRole('button', { name: '이전 이미지' }).focus()
  await page.keyboard.press('Enter')
  await currentSlide(page, 1)

  await carousel.getByRole('button', { name: '메인 이미지 5 보기' }).focus()
  await page.keyboard.press('Enter')
  await currentSlide(page, 5)
})

test('pauses automatic rotation while hovered or focused', async ({ page }) => {
  await mockLanding(page)
  await page.clock.install()
  await page.goto('/')

  const carousel = page.getByRole('region', { name: '리조트 메인 이미지' })
  await carousel.getByRole('button', { name: '다음 이미지' }).focus()
  await page.clock.runFor(6_000)
  await currentSlide(page, 1)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(0)

  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur())
  await carousel.hover()
  await page.clock.runFor(6_000)
  await currentSlide(page, 1)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(0)

  await page.locator('#booking').hover()
  await page.clock.runFor(5_100)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(24)
})

test('lets the user keep automatic rotation paused', async ({ page }) => {
  await mockLanding(page)
  await page.clock.install()
  await page.goto('/')

  const carousel = page.getByRole('region', { name: '리조트 메인 이미지' })
  const pause = carousel.getByRole('button', { name: '자동 전환 일시정지' })
  await pause.click()
  await expect(carousel.getByRole('button', { name: '자동 전환 재생' })).toHaveAttribute('aria-pressed', 'true')
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur())
  await page.locator('#booking').hover()
  await page.clock.runFor(6_000)
  await currentSlide(page, 1)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(0)

  await carousel.getByRole('button', { name: '자동 전환 재생' }).click()
  await page.locator('#booking').hover()
  await page.clock.runFor(5_100)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(24)
})

test('pauses automatic rotation while the fixed booking link is hovered or focused', async ({ page }) => {
  await mockLanding(page)
  await page.clock.install()
  await page.goto('/')

  const carousel = page.getByRole('region', { name: '리조트 메인 이미지' })
  const bookingLink = page.getByRole('link', { name: /객실 예약하기/ }).first()
  await bookingLink.hover()
  await page.clock.runFor(6_000)
  await currentSlide(page, 1)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(0)

  await page.locator('#booking').hover()
  await bookingLink.focus()
  await page.clock.runFor(6_000)
  await currentSlide(page, 1)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(0)
})

test('uses a short fade instead of tiles for reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await mockLanding(page)
  await page.clock.install()
  await page.goto('/')

  const carousel = page.getByRole('region', { name: '리조트 메인 이미지' })
  await page.clock.runFor(6_000)
  await currentSlide(page, 1)
  await carousel.getByRole('button', { name: '다음 이미지' }).click()
  await currentSlide(page, 2)
  await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(0)
  await expect(carousel.locator('.hero-carousel-fade')).toHaveCount(1)
})

for (const width of [390, 360, 320]) {
  test(`uses twelve visible transition tiles and controls at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await mockLanding(page)
    await page.goto('/')

    const carousel = page.getByRole('region', { name: '리조트 메인 이미지' })
    const buttons = carousel.getByRole('button')
    for (let index = 0; index < await buttons.count(); index++) {
      const box = await buttons.nth(index).boundingBox()
      expect(box).not.toBeNull()
      expect(box!.x).toBeGreaterThanOrEqual(0)
      expect(box!.x + box!.width).toBeLessThanOrEqual(width)
      expect(box!.width).toBeGreaterThanOrEqual(44)
      expect(box!.height).toBeGreaterThanOrEqual(44)
    }
    await carousel.getByRole('button', { name: '다음 이미지' }).click()
    await expect(carousel.locator('.hero-carousel-tile')).toHaveCount(12)
    await currentSlide(page, 2)
    expect(await page.locator('body').evaluate(body => body.scrollWidth <= window.innerWidth)).toBe(true)
  })
}
