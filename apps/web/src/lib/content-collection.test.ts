import { parseContentCollection } from './content-collection.ts'

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

const card = {
  id: '723e4567-e89b-12d3-a456-426614174000',
  contentKind: 'ROOM',
  path: '/stays/sokcho/rooms/forest-suite',
  title: '포레스트 스위트',
  summary: '숲과 바다를 함께 바라보는 객실입니다.',
  image: '/images/forest-suite.jpg',
  hotelSlug: 'sokcho',
}

expectEqual(parseContentCollection([card]), [card], '공개 카드 read model만 파싱한다')
expectEqual(
  parseContentCollection([{ ...card, image: '/api/website/media/b23e4567-e89b-12d3-a456-426614174000/content' }])?.[0]?.image,
  '/api/website/media/b23e4567-e89b-12d3-a456-426614174000/content',
  '공개 카드의 업로드 미디어 전달 경로를 파싱한다',
)
expectEqual(parseContentCollection([{ ...card, price: 1000 }]), null, 'CMS 가격 필드를 거절한다')
expectEqual(parseContentCollection([{ ...card, image: '/images/private/../room.jpg' }]), null, '상위 경로 이미지를 거절한다')
expectEqual(parseContentCollection([{ ...card, draftVersion: 4 }]), null, '초안 전용 필드를 거절한다')
expectEqual(parseContentCollection([{ ...card, path: '/stays/Sokcho' }]), null, '정규 로컬 경로가 아닌 카드를 거절한다')
