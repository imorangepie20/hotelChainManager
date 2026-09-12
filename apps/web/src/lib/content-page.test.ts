import { parseContentPage } from './content-page.ts'
import { destinationContentByRegion, destinationContentFromPublished } from './destination-content.ts'

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

export function runContentPageTests() {
  expectEqual(
    parseContentPage({
      seo: { title: '브랜드 이야기 | STAY HANEUL', description: '호텔 브랜드의 이야기입니다.' },
      blocks: [
        { type: 'HERO', eyebrow: 'OUR BRAND', title: '머무름의 이야기', description: '각 장소의 시간을 소개합니다.', imageSrc: '/images/brand-story.jpg', imageAlt: '호텔 로비', cta: { label: '속초 호텔 보기', href: '/stays/sokcho' } },
        { type: 'TEXT', title: '우리가 만드는 시간', paragraphs: ['첫 번째 문단', '두 번째 문단'] },
        { type: 'CTA', eyebrow: 'BOOK', title: '여정을 시작하세요', description: '가까운 지점을 찾아보세요.', cta: { label: '객실 예약', href: '/stays/sokcho#booking' } },
        { type: 'SCRIPT', value: 'alert(1)' },
        { type: 'HERO', title: '외부 이미지', imageSrc: 'https://example.com/image.jpg', imageAlt: '외부 이미지' },
        { type: 'CTA', title: '외부 이동', cta: { label: '외부 이동', href: 'https://example.com' } },
      ],
    }),
    null,
    '허용되지 않은 블록이나 링크가 있으면 문서 전체를 거절한다',
  )

  const seo = { title: '브랜드 이야기 | STAY HANEUL', description: '호텔 브랜드의 이야기입니다.' }
  const bundledAssetId = '123e4567-e89b-12d3-a456-426614174000'
  const uploadedAssetId = '223e4567-e89b-12d3-a456-426614174000'
  const hero = {
    type: 'HERO',
    title: '머무름의 이야기',
    imageAssetId: bundledAssetId,
    imageSrc: '/images/brand-story.jpg',
    imageAlt: '호텔 로비',
  }
  const text = { type: 'TEXT', title: '우리가 만드는 시간', paragraphs: ['첫 번째 문단'] }
  const richBlocks = [
    { type: 'IMAGE_GALLERY', title: '객실 갤러리', items: [{ imageAssetId: bundledAssetId, imageSrc: '/images/brand-story.jpg', imageAlt: '객실 창가' }, { imageAssetId: uploadedAssetId, imageSrc: `/api/website/media/${uploadedAssetId}/content`, imageAlt: '객실 침실', caption: '편안한 침실' }] },
    { type: 'FEATURE_GRID', title: '객실 특징', items: [{ title: '오션뷰', description: '동해를 바라봅니다.' }, { title: '킹 베드', description: '여유로운 휴식을 제공합니다.' }] },
    { type: 'SPEC_TABLE', title: '객실 사양', rows: [{ label: '면적', value: '45㎡' }] },
    { type: 'ACCORDION', title: '자주 묻는 질문', items: [{ title: '체크인 시간', content: '오후 3시부터입니다.' }] },
    { type: 'NOTICE_LIST', title: '이용 안내', items: [{ text: '성수기에는 조기 마감될 수 있습니다.', severity: 'IMPORTANT' }] },
  ]
  const homepageHero = { ...hero, cta: { label: '메인으로', href: '/' } }

  expectEqual(parseContentPage({ seo, blocks: [homepageHero] }), { seo, blocks: [homepageHero] }, '자산 UUID가 있는 내장 이미지를 허용한다')
  expectEqual(
    parseContentPage({ seo, blocks: [{ ...hero, imageAssetId: uploadedAssetId, imageSrc: `/api/website/media/${uploadedAssetId}/content` }] }),
    { seo, blocks: [{ ...hero, imageAssetId: uploadedAssetId, imageSrc: `/api/website/media/${uploadedAssetId}/content` }] },
    '자산 UUID와 일치하는 업로드 전달 경로를 허용한다',
  )
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, imageAssetId: 'not-a-uuid' }] }), null, '형식이 잘못된 HERO 자산 UUID를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, imageAssetId: undefined }] }), null, 'HERO 자산 UUID가 없으면 문서를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, imageSrc: '/images/foo/../private.jpg' }] }), null, '중첩된 상위 경로 이미지도 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, imageSrc: 'data:image/png;base64,AAAA' }] }), null, 'data URL 이미지를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, imageSrc: 'https://example.com/image.jpg' }] }), null, '외부 이미지를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, imageSrc: `/api/website/media/${uploadedAssetId}/content` }] }), null, '다른 자산의 업로드 전달 경로를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, imageSrc: '/api/website/media/not-a-uuid/content' }] }), null, '형식이 잘못된 업로드 전달 경로를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [{ ...hero, cta: { label: '외부 이동', href: 'https://example.com' } }] }), null, 'HERO의 외부 CTA도 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [] }), null, '블록이 없는 문서를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [text, hero] }), null, '첫 블록이 HERO가 아니면 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [hero, hero] }), null, 'HERO가 둘 이상이면 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [hero, { ...text, paragraphs: [] }] }), null, '문단이 없는 TEXT를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [hero, { ...text, paragraphs: ['하나', '둘', '셋', '넷', '다섯', '여섯', '일곱'] }] }), null, '문단이 일곱 개인 TEXT를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [hero, { ...text, paragraphs: ['유효한 문단', ''] }] }), null, '빈 문단이 있는 TEXT를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [hero, ...richBlocks] }), { seo, blocks: [hero, ...richBlocks] }, '상세 페이지의 다섯 구조화 block을 허용한다')
  const gallery = richBlocks[0] as { type: 'IMAGE_GALLERY'; title: string; items: unknown[] }
  expectEqual(parseContentPage({ seo, blocks: [hero, { ...gallery, items: [gallery.items[0]] }] }), null, '이미지가 한 장인 gallery를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [hero, { ...richBlocks[4], items: [{ text: '안내', severity: 'UNKNOWN' }] }] }), null, '알 수 없는 안내 심각도를 거절한다')
  expectEqual(parseContentPage({ seo, blocks: [hero, ...Array.from({ length: 20 }, () => text)] }), null, '블록이 스물한 개면 거절한다')
  expectEqual(parseContentPage({ seo: { title: '', description: '설명' }, blocks: [hero] }), null, '유효하지 않은 SEO 문서를 거절한다')

  const staticLanding = destinationContentFromPublished('속초', {
    title: '속초의 이야기',
    heroAssetId: bundledAssetId,
    heroImage: '/images/sokcho-coast-hero.png',
    heroAlt: '속초 해안',
  })
  expectEqual(staticLanding.heroImage, '/images/sokcho-coast-hero.png', '자산 UUID가 있는 내장 랜딩 이미지를 사용한다')
  expectEqual(destinationContentByRegion('설악산').heroImage, '/images/seoraksan-forest-hero.png', '설악산은 지점별 기본 히어로 이미지를 사용한다')
  expectEqual(destinationContentByRegion('제주도').heroImage, '/images/jeju-island-hero.png', '제주도는 지점별 기본 히어로 이미지를 사용한다')

  const uploadedLanding = destinationContentFromPublished('속초', {
    title: '속초의 이야기',
    heroAssetId: uploadedAssetId,
    heroImage: `/api/website/media/${uploadedAssetId}/content`,
    heroAlt: '업로드한 속초 해안',
  })
  expectEqual(uploadedLanding.heroImage, `/api/website/media/${uploadedAssetId}/content`, '자산 UUID와 일치하는 업로드 랜딩 이미지를 사용한다')

  const fallbackHero = destinationContentByRegion('속초').heroImage
  expectEqual(
    destinationContentFromPublished('속초', { title: '속초의 이야기', heroAssetId: 'not-a-uuid', heroImage: '/images/sokcho-coast-hero.png' }).heroImage,
    fallbackHero,
    '형식이 잘못된 랜딩 자산 UUID는 정적 히어로로 되돌린다',
  )
  expectEqual(
    destinationContentFromPublished('속초', { title: '속초의 이야기', heroAssetId: bundledAssetId, heroImage: 'https://example.com/hero.jpg' }).heroImage,
    fallbackHero,
    '외부 랜딩 이미지는 정적 히어로로 되돌린다',
  )
  expectEqual(
    destinationContentFromPublished('속초', { title: '속초의 이야기', heroAssetId: bundledAssetId, heroImage: '/images/foo/../private.jpg' }).heroImage,
    fallbackHero,
    '상위 경로 랜딩 이미지는 정적 히어로로 되돌린다',
  )
  expectEqual(
    destinationContentFromPublished('속초', { title: '속초의 이야기', heroAssetId: bundledAssetId, heroImage: `/api/website/media/${uploadedAssetId}/content` }).heroImage,
    fallbackHero,
    '다른 자산의 업로드 랜딩 이미지는 정적 히어로로 되돌린다',
  )

  const hotelId = '323e4567-e89b-12d3-a456-426614174000'
  const roomTypeId = '423e4567-e89b-12d3-a456-426614174000'
  const typedBlockId = (suffix: string) => `523e4567-e89b-12d3-a456-4266141740${suffix}`
  const typedHero = { ...hero, blockId: typedBlockId('01') }
  const typedGallery = {
    type: 'IMAGE_GALLERY', blockId: typedBlockId('02'), title: '객실 갤러리',
    items: [
      { imageAssetId: bundledAssetId, imageSrc: '/images/brand-story.jpg', imageAlt: '창가' },
      { imageAssetId: bundledAssetId, imageSrc: '/images/brand-story.jpg', imageAlt: '침실' },
    ],
  }
  const roomPayload = {
    contentKind: 'ROOM', hotelId,
    connections: { roomTypeIds: [roomTypeId], targetHotelIds: [], relatedPages: [] },
    seo,
    blocks: [
      typedHero,
      typedGallery,
      { type: 'SPEC_TABLE', blockId: typedBlockId('03'), title: '객실 사양', rows: [{ label: '면적', value: '45㎡' }] },
      { type: 'BOOKING_CTA', blockId: typedBlockId('04'), title: '예약', description: '일정을 선택하세요.', label: '객실 예약', hotelId, roomTypeId },
    ],
  }
  expectEqual(parseContentPage(roomPayload)?.blocks.at(-1)?.type, 'BOOKING_CTA', 'ROOM의 연결된 예약 CTA를 파싱한다')
  expectEqual(
    parseContentPage({ ...roomPayload, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] } }),
    null,
    'ROOM 연결에 없는 객실 유형 CTA를 거절한다',
  )
  expectEqual(
    parseContentPage({ ...roomPayload, blocks: [...roomPayload.blocks.slice(0, 3), { ...roomPayload.blocks[3], hotelId: '623e4567-e89b-12d3-a456-426614174000' }] }),
    null,
    'ROOM 페이지와 다른 호텔 예약 CTA를 거절한다',
  )
  expectEqual(
    parseContentPage({ ...roomPayload, blocks: roomPayload.blocks.map(block => ({ ...block, blockId: typedBlockId('01') })) }),
    null,
    '중복 blockId를 거절한다',
  )
  expectEqual(
    parseContentPage({ ...roomPayload, blocks: roomPayload.blocks.map((block, index) => index === 1 ? { ...block, blockId: undefined } : block) }),
    null,
    'typed 페이지에서 blockId 누락을 거절한다',
  )
  expectEqual(
    parseContentPage({ ...roomPayload, blocks: [...roomPayload.blocks, { type: 'OPERATING_HOURS', blockId: typedBlockId('05'), title: '운영 시간', entries: [{ dayLabel: '매일', opensAt: '09:00', closesAt: '18:00', closed: false }] }] }),
    null,
    'ROOM에서 허용되지 않은 운영 시간 블록을 거절한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'DINING', hotelId, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, seo,
      blocks: [typedHero, typedGallery, { type: 'OPERATING_HOURS', blockId: typedBlockId('05'), title: '운영 시간', entries: [{ dayLabel: '매일', opensAt: '09:00', closesAt: '18:00', closed: false }] }],
    })?.blocks.at(-1)?.type,
    'OPERATING_HOURS',
    'DINING 운영 시간 블록을 파싱한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'DINING', hotelId, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, seo,
      blocks: [typedHero, typedGallery, { type: 'OPERATING_HOURS', blockId: typedBlockId('05'), title: '운영 시간', entries: [{ dayLabel: '매일', opensAt: '9:00', closesAt: '18:00', closed: false }] }],
    }),
    null,
    '형식이 잘못된 운영 시간을 거절한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'FACILITY', hotelId, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, seo,
      blocks: [typedHero, typedGallery, { type: 'LOCATION', blockId: typedBlockId('06'), title: '위치', address: '강원도 속초시', directions: '로비에서 좌회전', mapHref: '/stays/sokcho#map' }, { type: 'SPEC_TABLE', blockId: typedBlockId('07'), title: '시설 사양', rows: [{ label: '수심', value: '1.2m' }] }],
    })?.blocks.at(2)?.type,
    'LOCATION',
    'FACILITY 위치 블록을 파싱한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'FACILITY', hotelId, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, seo,
      blocks: [typedHero, typedGallery, { type: 'LOCATION', blockId: typedBlockId('06'), title: '위치', address: '강원도 속초시', mapHref: 'https://maps.example.com' }, { type: 'SPEC_TABLE', blockId: typedBlockId('07'), title: '시설 사양', rows: [{ label: '수심', value: '1.2m' }] }],
    }),
    null,
    '외부 지도 URL을 거절한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'EXPERIENCE', hotelId, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, seo,
      blocks: [typedHero, { type: 'RICH_TEXT', blockId: typedBlockId('08'), eyebrow: 'PROGRAM', title: '숲길 산책', paragraphs: ['계절을 따라 걸어보세요.'] }],
    })?.blocks.at(-1)?.type,
    'RICH_TEXT',
    'EXPERIENCE RICH_TEXT를 파싱한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'PROMOTION', hotelId: null, connections: { roomTypeIds: [], targetHotelIds: [hotelId], relatedPages: [] }, seo,
      blocks: [typedHero, { type: 'PROMOTION_SUMMARY', blockId: typedBlockId('09'), title: '가을 제안', salesPeriod: '9월 1일~30일', stayPeriod: '10월 1일~11월 30일', benefits: ['조식 제공'], displayPrice: '표시 정보' }, { type: 'BOOKING_CTA', blockId: typedBlockId('10'), title: '예약', description: '일정을 선택하세요.', label: '예약하기', hotelId }],
    })?.blocks.at(1)?.type,
    'PROMOTION_SUMMARY',
    'PROMOTION 요약과 대상 호텔 CTA를 파싱한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'PROMOTION', hotelId: null, connections: { roomTypeIds: [], targetHotelIds: [hotelId], relatedPages: [] }, seo,
      blocks: [typedHero, { type: 'PROMOTION_SUMMARY', blockId: typedBlockId('09'), title: '가을 제안', salesPeriod: '9월', stayPeriod: '10월', benefits: ['조식'], price: 1000 }, { type: 'BOOKING_CTA', blockId: typedBlockId('10'), title: '예약', description: '일정을 선택하세요.', label: '예약하기', hotelId }],
    }),
    null,
    'CMS 가격 필드를 거절한다',
  )
  expectEqual(
    parseContentPage({
      contentKind: 'BRAND', hotelId: null, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, seo,
      blocks: [typedHero, { type: 'RELATED_COLLECTION', blockId: typedBlockId('11'), title: '추천 객실', kind: 'ROOM', targetHotelId: hotelId, maxItems: 3 }],
    })?.blocks.at(-1)?.type,
    'RELATED_COLLECTION',
    'BRAND 관련 콘텐츠 컬렉션을 파싱한다',
  )
}

runContentPageTests()
