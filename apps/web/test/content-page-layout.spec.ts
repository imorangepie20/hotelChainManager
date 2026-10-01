import { expect, test, type Page } from '@playwright/test'

const hotel = { id: '11000000-0000-0000-0000-000000000001', name: '스테이 하늘 속초', region: '속초', timezone: 'Asia/Seoul' }
const assetId = '723e4567-e89b-12d3-a456-426614174000'
const image = `/api/website/media/${assetId}/content`
const connections = { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }
const id = (index: number) => `823e4567-e89b-12d3-a456-42661417400${index}`
const detail = {
  type: 'CONTENT_PAGE', contentKind: 'DINING', hotelId: hotel.id, connections,
  mediaVariants: { [assetId]: [{ targetWidth: 640, deliveryUrl: `/api/website/media/${assetId}/variants/640.webp`, mimeType: 'image/webp' }] },
  content: {
    seo: { title: '파도 테이블', description: '가상 호텔 데모 다이닝' },
    blocks: [
      { type: 'HERO', blockId: id(0), title: '파도 테이블', eyebrow: 'SOKCHO · DINING', description: '가상 호텔 데모 다이닝을 소개합니다.', imageAssetId: assetId, imageSrc: image, imageAlt: '가상 호텔의 바다' },
      { type: 'RICH_TEXT', blockId: id(1), eyebrow: 'STORY', title: '이곳의 이야기', paragraphs: ['바다를 바라보며 여유로운 한 끼를 제안합니다. 실제 영업 시설이 아닌 가상 호텔 데모입니다.'] },
      { type: 'RICH_TEXT', blockId: id(2), eyebrow: 'YOUR STAY', title: '제안하는 머무름', paragraphs: ['자신의 속도로 쉬고 이야기하는 시간입니다.'] },
      { type: 'IMAGE_GALLERY', blockId: id(3), title: '분위기 이미지', items: [1, 2].map(index => ({ imageAssetId: assetId, imageSrc: image, imageAlt: `가상 호텔 이미지 ${index}`, caption: `가상 데모 이미지 ${index}` })) },
      { type: 'OPERATING_HOURS', blockId: id(4), title: '운영 안내', entries: [{ dayLabel: '가상 데모', closed: true }], exceptions: '실제 영업시간을 안내하는 페이지가 아닙니다.' },
      { type: 'NOTICE_LIST', blockId: id(5), title: '방문 전 확인해 주세요', items: [{ text: '가상 호텔 데모이며 실제 시설 예약은 제공하지 않습니다.', severity: 'IMPORTANT' }] },
    ],
  },
}

async function mockDetail(page: Page, contentKind = 'DINING') {
  const document = {
    ...detail,
    contentKind,
    content: { ...detail.content, blocks: detail.content.blocks.map(block => contentKind === 'EXPERIENCE' && block.type === 'OPERATING_HOURS'
      ? { type: 'SPEC_TABLE', blockId: id(4), title: '이용 안내', rows: [{ label: '운영 상태', value: '가상 호텔 데모' }] }
      : block) },
  }
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/hotels') return route.fulfill({ json: [hotel] })
    if (url.pathname === '/api/website/navigation') return route.fulfill({ json: [] })
    if (url.pathname === '/api/website/pages/resolve') return route.fulfill({ json: document })
    if (url.pathname === image || url.pathname === `/api/website/media/${assetId}/variants/640.webp`) return route.fulfill({ body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64'), contentType: 'image/png' })
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } })
  })
}

test('공통 상세는 본문 시작선과 폭을 통일하고 hero와 여백을 줄인다', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockDetail(page)
  await page.goto('/stays/sokcho/discover/pado-table')
  await expect(page.getByRole('heading', { name: '파도 테이블', exact: true })).toBeVisible()
  await expect.poll(() => page.locator('.content-page-hero img').evaluate(image => image instanceof HTMLImageElement && image.complete && image.naturalWidth > 0)).toBe(true)
  const sections = page.locator('main section.content-section')
  await expect(sections).toHaveCount(5)
  const boxes = await sections.evaluateAll(elements => elements.map(element => {
    const box = element.getBoundingClientRect()
    return { left: box.left, width: box.width, top: box.top, bottom: box.bottom }
  }))
  for (const box of boxes) {
    expect(Math.abs(box.left - boxes[0].left)).toBeLessThanOrEqual(1)
    expect(Math.abs(box.width - boxes[0].width)).toBeLessThanOrEqual(1)
  }
  const hero = await page.locator('.content-page-hero').boundingBox()
  expect(hero!.height).toBeLessThanOrEqual(440)
  for (let index = 1; index < boxes.length; index++) {
    expect(boxes[index].top - boxes[index - 1].bottom).toBeLessThanOrEqual(80)
  }
})

test('추천 link는 밝은 설명 면·충분한 대비·일정한 간격과 키보드 이동을 제공한다', async ({ page }) => {
  await mockDetail(page)
  await page.route('**/api/website/pages/resolve*', route => {
    const path = new URL(route.request().url()).searchParams.get('path')
    if (path === '/stays/sokcho/discover/pado-table') return route.fulfill({ json: detail })
    return route.fulfill({ json: {
      type: 'HOTEL_LANDING', hotelId: hotel.id,
      content: {
        title: '속초에서 보내는 하루', description: '가상의 지점 소개입니다.', eyebrow: 'SOKCHO',
        heroAssetId: assetId, heroImage: image, heroAlt: '속초 데모 이미지',
        recommendedExperiences: [{ pageId: id(6), contentKind: 'DINING', path: '/stays/sokcho/discover/pado-table', title: '파도 테이블', summary: '동해의 빛을 담은 가상 다이닝입니다.', image }],
      },
    } })
  })
  await page.goto('/stays/sokcho')
  const card = page.getByRole('link', { name: /파도 테이블.*자세히 보기/ })
  await expect(card).toBeVisible()
  await expect(card).toHaveCSS('background-color', 'rgb(255, 255, 255)')
  const category = await card.locator('p').boundingBox()
  const heading = await card.getByRole('heading').boundingBox()
  expect(heading!.y - category!.y - category!.height).toBeLessThanOrEqual(20)
  const contrast = await card.evaluate(element => {
    const rgb = (value: string) => value.match(/\d+/g)!.slice(0, 3).map(Number)
    const luminance = (channels: number[]) => channels.map(c => c / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4).reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i], 0)
    const styles = getComputedStyle(element)
    const background = luminance(rgb(styles.backgroundColor))
    return Math.min(...[element, ...element.querySelectorAll('.experience-card-body > p, .experience-card-body > h3, .experience-card-body > span, .experience-card-body > strong')].map(text => {
      const foreground = luminance(rgb(getComputedStyle(text).color))
      return (Math.max(foreground, background) + .05) / (Math.min(foreground, background) + .05)
    }))
  })
  expect(contrast).toBeGreaterThanOrEqual(4.5)
  await card.focus()
  await expect(card).toBeFocused()
  expect(await card.evaluate(element => getComputedStyle(element).outlineStyle)).not.toBe('none')
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL('/stays/sokcho/discover/pado-table')
  await expect(page.getByRole('heading', { name: '파도 테이블', exact: true })).toBeVisible()
})

for (const kind of ['DINING', 'FACILITY', 'EXPERIENCE']) {
  for (const locale of ['ko', 'en']) {
    test(`${kind} ${locale} 모바일 정렬·가로 넘침·갤러리 키보드 제어`, async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await mockDetail(page, kind)
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 844 })
        await page.goto(`${locale === 'en' ? '/en' : ''}/stays/sokcho/discover/pado-table`)
        await expect(page.getByRole('heading', { name: '파도 테이블', exact: true })).toBeVisible()
        await expect(page.locator('.content-kind-label')).toHaveText(kind)
        expect(await page.locator('body').evaluate(element => element.scrollWidth <= window.innerWidth)).toBe(true)
        const boxes = await page.locator('main section.content-section').evaluateAll(elements => elements.map(element => {
          const box = element.getBoundingClientRect()
          return { left: box.left, right: box.right, width: box.width }
        }))
        expect(boxes).toHaveLength(5)
        for (const box of boxes) {
          expect(Math.abs(box.left - boxes[0].left)).toBeLessThanOrEqual(1)
          expect(Math.abs(box.width - boxes[0].width)).toBeLessThanOrEqual(1)
          expect(box.left).toBeGreaterThanOrEqual(20)
          expect(box.right).toBeLessThanOrEqual(width - 20)
        }
        const hero = await page.locator('.content-page-hero').boundingBox()
        expect(hero!.height).toBeLessThanOrEqual(340)
        const copy = await page.locator('.content-page-hero .hero-copy').boundingBox()
        expect(copy!.x).toBeGreaterThanOrEqual(20)
        expect(copy!.x + copy!.width).toBeLessThanOrEqual(width - 20)
        const next = page.getByRole('button', { name: locale === 'en' ? 'Next image' : '다음 이미지' })
        await next.scrollIntoViewIfNeeded()
        const hitArea = await next.boundingBox()
        expect(hitArea!.width).toBeGreaterThanOrEqual(44)
        expect(hitArea!.height).toBeGreaterThanOrEqual(44)
        await next.focus()
        await page.keyboard.press('Enter')
        await expect(page.locator('.content-gallery-position')).toHaveText('2 / 2')
        await expect(page.locator('.content-gallery-caption')).toHaveText('가상 데모 이미지 2')
        await page.getByRole('button', { name: locale === 'en' ? 'Previous image' : '이전 이미지' }).click()
        await expect(page.locator('.content-gallery-position')).toHaveText('1 / 2')
        await page.getByRole('button', { name: locale === 'en' ? 'View image 2' : '2번 이미지 보기' }).click()
        await expect(page.locator('.content-gallery-position')).toHaveText('2 / 2')
        await expect(page.getByText('가상 호텔 데모이며 실제 시설 예약은 제공하지 않습니다.', { exact: true })).toBeVisible()
      }
    })
  }
}

test('이미지 없는 legacy 추천·예약 CTA와 지점 hero는 기존 구성을 유지한다', async ({ page }) => {
  await mockDetail(page)
  await page.route('**/api/website/pages/resolve*', route => route.fulfill({ json: {
    type: 'HOTEL_LANDING', hotelId: hotel.id,
    content: { title: '속초 지점', description: '가상 호텔 데모', heroAssetId: assetId, heroImage: image, heroAlt: '속초 데모 이미지' },
  } }))
  await page.goto('/stays/sokcho')
  const cards = page.locator('#experiences article.experience-card')
  await expect(cards).toHaveCount(3)
  await expect(cards.first()).toHaveCSS('color', 'rgb(255, 255, 255)')
  await expect(cards.first().getByRole('link')).toHaveAttribute('href', '#booking')
  await expect(page.locator('.cms-content-page')).toHaveCount(0)
  expect((await page.locator('.hero').boundingBox())!.height).toBeGreaterThanOrEqual(560)
})

test('영문 CMS 홈은 상세 전용 hero 높이와 본문 wrapper를 적용하지 않는다', async ({ page }) => {
  await mockDetail(page)
  await page.route('**/api/website/pages/resolve*', route => route.fulfill({ json: {
    type: 'HOME_PAGE', contentKind: 'HOME', hotelId: null,
    content: { ...detail.content, blocks: detail.content.blocks.slice(0, 3) },
  } }))
  await page.goto('/en')
  await expect(page.getByRole('heading', { name: '파도 테이블', exact: true })).toBeVisible()
  expect((await page.locator('.hero').boundingBox())!.height).toBeGreaterThanOrEqual(560)
  await expect(page.locator('.cms-content-page')).toHaveCount(0)
})

test('320px 장문 hero도 제목·설명·CTA를 자르지 않고 자연스럽게 확장된다', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 844 })
  await mockDetail(page)
  const longHero = {
    ...detail.content.blocks[0],
    title: '동해의 풍경과 함께 천천히 머무는 가상 다이닝 공간 '.repeat(10).slice(0, 160),
    eyebrow: 'SOKCHO · 가상 호텔 데모 · 긴 안내 문구 '.repeat(5).slice(0, 100),
    description: '가상 호텔 데모입니다. 실제 운영 시설이나 예약 가능한 프로그램을 안내하지 않습니다. '.repeat(25).slice(0, 1000),
    cta: { label: '지점 소개로 돌아가기', href: '/stays/sokcho' },
  }
  await page.route('**/api/website/pages/resolve*', route => route.fulfill({ json: {
    ...detail, content: { ...detail.content, blocks: [longHero, ...detail.content.blocks.slice(1)] },
  } }))
  await page.goto('/stays/sokcho/discover/pado-table')
  await expect(page.locator('h1')).toHaveText(longHero.title.trim())
  await page.evaluate(() => document.fonts.ready)
  const hero = await page.locator('.content-page-hero').boundingBox()
  const copy = await page.locator('.content-page-hero .hero-copy').boundingBox()
  expect(copy!.y).toBeGreaterThanOrEqual(hero!.y)
  expect(copy!.y + copy!.height).toBeLessThanOrEqual(hero!.y + hero!.height)
  expect(copy!.x).toBeGreaterThanOrEqual(20)
  expect(copy!.x + copy!.width).toBeLessThanOrEqual(300)
  const cta = page.getByRole('link', { name: '지점 소개로 돌아가기' })
  await cta.scrollIntoViewIfNeeded()
  await expect(cta).toBeVisible()
  const button = await cta.boundingBox()
  expect(button!.y + button!.height).toBeLessThanOrEqual((await page.locator('.content-page-hero').boundingBox())!.y + hero!.height)
  expect(await page.locator('body').evaluate(element => element.scrollWidth <= window.innerWidth)).toBe(true)
})
