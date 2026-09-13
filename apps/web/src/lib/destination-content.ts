import { safeMediaDeliveryPath, safeMediaVariants, type ResponsiveMediaVariant } from './content-page.ts'

export type DestinationContent = {
  heroImage: string
  heroVariants?: ResponsiveMediaVariant[]
  heroAlt: string
  eyebrow: string
  title: string
  description: string
  seo: { title: string; description: string }
  arrival: { address: string; checkInOut: string; highlight: string }
  experiences: Array<{ category: string; title: string; description: string }>
  offers: Array<{ title: string; detail: string; bookingPeriod: string; stayPeriod: string }>
}

const sokchoHero = '/images/sokcho-coast-hero.png'
const seoraksanHero = '/images/seoraksan-forest-hero.png'
const jejuHero = '/images/jeju-island-hero.png'

const destinations: Record<string, DestinationContent> = {
  '속초': {
    heroImage: sokchoHero, heroAlt: '동해와 설악산을 바라보는 가상의 속초 해안 호텔', eyebrow: 'SOKCHO · EAST SEA',
    title: '파도와 설악의 사이에서\n가장 느린 하루를', description: '동해의 수평선과 설악의 능선을 한눈에 담는 휴식.',
    seo: { title: '속초 오션 호텔 | STAY HANEUL', description: '동해와 설악을 바라보는 속초 오션 호텔의 객실, 오퍼와 예약 정보를 확인하세요.' },
    arrival: { address: '강원특별자치도 속초시, 가상 해안로 186', checkInOut: '15:00 / 11:00', highlight: '새벽의 바다와 저녁의 항구를 가까이에서 만나보세요.' },
    experiences: [{ category: 'ROOM', title: '수평선을 향한 객실', description: '창 너머로 바다가 이어지는 편안한 하루.' }, { category: 'DINING', title: '동해의 제철 식탁', description: '지역의 계절을 담은 느긋한 아침과 저녁.' }, { category: 'EXPERIENCE', title: '바다 곁의 산책', description: '해안 산책로와 항구의 시간을 따라 걷는 여행.' }],
    offers: [{ title: '푸른 아침', detail: '조용한 바다를 바라보며 시작하는 휴식의 제안', bookingPeriod: '2026.09.01 ~ 2026.12.31', stayPeriod: '2026.09.15 ~ 2027.02.28' }, { title: '느린 주말', detail: '하루를 더 머물며 속초의 계절을 깊게 만나는 여정', bookingPeriod: '2026.09.01 ~ 2026.12.31', stayPeriod: '2026.09.01 ~ 2027.03.31' }],
  },
  '설악산': {
    heroImage: seoraksanHero, heroAlt: '새벽 안개와 설악 능선, 소나무 숲 사이의 포레스트 호텔', eyebrow: 'SEORAK · FOREST',
    title: '숲의 결을 따라\n깊어지는 쉼', description: '계절마다 다른 빛을 품은 설악의 능선에서 만나는 고요.',
    seo: { title: '설악산 포레스트 호텔 | STAY HANEUL', description: '설악의 숲과 능선을 품은 포레스트 호텔의 객실, 오퍼와 예약 정보를 확인하세요.' },
    arrival: { address: '강원특별자치도 설악산 일대, 가상 설악로 72', checkInOut: '15:00 / 11:00', highlight: '아침 안개와 숲의 향을 따라 천천히 하루를 시작하세요.' },
    experiences: [{ category: 'ROOM', title: '능선을 담은 객실', description: '나무와 돌의 온기를 닮은 차분한 공간.' }, { category: 'DINING', title: '산의 계절 식탁', description: '강원도의 재료로 완성한 따뜻한 식사.' }, { category: 'EXPERIENCE', title: '숲속의 한 걸음', description: '계곡과 산책길을 따라 자연의 호흡을 만나는 시간.' }],
    offers: [{ title: '숲의 아침', detail: '산의 공기와 함께 시작하는 한적한 휴식', bookingPeriod: '2026.09.01 ~ 2026.12.31', stayPeriod: '2026.09.15 ~ 2027.02.28' }, { title: '계절의 산책', detail: '머무는 날만큼 깊어지는 설악의 풍경', bookingPeriod: '2026.09.01 ~ 2026.12.31', stayPeriod: '2026.09.01 ~ 2027.03.31' }],
  },
  '제주도': {
    heroImage: jejuHero, heroAlt: '현무암 해안과 푸른 바다를 바라보는 제주 아일랜드 호텔', eyebrow: 'JEJU · ISLAND',
    title: '바람이 머무는 섬에서\n나만의 리듬으로', description: '현무암의 질감과 푸른 바다가 만나는 제주에서의 여정.',
    seo: { title: '제주 아일랜드 호텔 | STAY HANEUL', description: '제주의 바람과 바다를 담은 아일랜드 호텔의 객실, 오퍼와 예약 정보를 확인하세요.' },
    arrival: { address: '제주특별자치도 제주시, 가상 해안길 31', checkInOut: '15:00 / 11:00', highlight: '바람, 돌, 바다의 질감을 따라 섬의 시간을 만나보세요.' },
    experiences: [{ category: 'ROOM', title: '섬의 빛을 담은 객실', description: '낮은 빛과 바다의 색을 편안하게 들이는 공간.' }, { category: 'DINING', title: '제주의 식탁', description: '섬의 식재료를 담백하게 풀어낸 한 끼.' }, { category: 'EXPERIENCE', title: '바람의 길', description: '해안과 오름을 잇는 제주의 느린 산책.' }],
    offers: [{ title: '섬의 오후', detail: '제주의 빛과 바람을 천천히 즐기는 휴식', bookingPeriod: '2026.09.01 ~ 2026.12.31', stayPeriod: '2026.09.15 ~ 2027.02.28' }, { title: '제주의 리듬', detail: '머무는 동안 발견하는 섬의 작은 장면들', bookingPeriod: '2026.09.01 ~ 2026.12.31', stayPeriod: '2026.09.01 ~ 2027.03.31' }],
  },
}

export function destinationContentByRegion(region: string): DestinationContent {
  return destinations[region] ?? destinations['속초']
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const isText = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0

export function destinationContentFromPublished(region: string, published: unknown): DestinationContent
export function destinationContentFromPublished(region: string, published: unknown, locale: 'en'): DestinationContent | null
export function destinationContentFromPublished(region: string, published: unknown, locale?: 'en'): DestinationContent | null {
  if (locale === 'en') {
    if (!isRecord(published) || !['title', 'description', 'eyebrow', 'heroAlt'].every(key => isText(published[key]))
      || !safeMediaDeliveryPath(published.heroAssetId, published.heroImage) || !isRecord(published.arrival)
      || !['address', 'checkInOut', 'highlight'].every(key => isText((published.arrival as Record<string, unknown>)[key]))
      || !Array.isArray(published.experiences) || !published.experiences.every(item => isRecord(item) && ['category', 'title', 'description'].every(key => isText(item[key])))
      || !Array.isArray(published.offers) || !published.offers.every(item => isRecord(item) && ['title', 'detail', 'bookingPeriod', 'stayPeriod'].every(key => isText(item[key])))) return null
    const englishSeo = isRecord(published.seo) ? published.seo : {}
    return destinationContentFromPublished(region, { ...published, seo: {
      title: isText(englishSeo.title) ? englishSeo.title : published.title,
      description: isText(englishSeo.description) ? englishSeo.description : published.description,
    } })
  }
  const fallback = destinationContentByRegion(region)
  if (!isRecord(published) || !isText(published.title)) return fallback

  const arrival = isRecord(published.arrival) ? published.arrival : {}
  const seo = isRecord(published.seo) ? published.seo : {}
  const experiences = Array.isArray(published.experiences) && published.experiences.every(item =>
    isRecord(item) && isText(item.category) && isText(item.title) && isText(item.description),
  ) ? published.experiences as DestinationContent['experiences'] : fallback.experiences
  const offers = Array.isArray(published.offers) && published.offers.every(item =>
    isRecord(item) && isText(item.title) && isText(item.detail) && isText(item.bookingPeriod) && isText(item.stayPeriod),
  ) ? published.offers as DestinationContent['offers'] : fallback.offers
  const heroImage = safeMediaDeliveryPath(published.heroAssetId, published.heroImage)
  const heroVariants = heroImage ? safeMediaVariants(published.mediaVariants, published.heroAssetId) : []

  return {
    heroImage: heroImage ?? fallback.heroImage,
    ...(heroVariants.length > 0 ? { heroVariants } : {}),
    heroAlt: heroImage && isText(published.heroAlt) ? published.heroAlt : fallback.heroAlt,
    eyebrow: isText(published.eyebrow) ? published.eyebrow : fallback.eyebrow,
    title: published.title,
    description: isText(published.description) ? published.description : fallback.description,
    seo: {
      title: isText(seo.title) ? seo.title : fallback.seo.title,
      description: isText(seo.description) ? seo.description : fallback.seo.description,
    },
    arrival: {
      address: isText(arrival.address) ? arrival.address : fallback.arrival.address,
      checkInOut: isText(arrival.checkInOut) ? arrival.checkInOut : fallback.arrival.checkInOut,
      highlight: isText(arrival.highlight) ? arrival.highlight : fallback.arrival.highlight,
    },
    experiences,
    offers,
  }
}
