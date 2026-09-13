import { websiteMetadata } from './website-metadata.ts'

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`)
  }
}

expectEqual(
  websiteMetadata({
    origin: 'https://stay.example',
    pathname: '/en/brand/story',
    title: 'Our Story | STAY HANEUL',
    description: 'A quiet retreat shaped by coast and forest.',
    previewMode: false,
  }),
  {
    title: 'Our Story | STAY HANEUL',
    description: 'A quiet retreat shaped by coast and forest.',
    canonicalUrl: 'https://stay.example/en/brand/story',
    robots: 'index,follow',
    openGraphTitle: 'Our Story | STAY HANEUL',
    openGraphDescription: 'A quiet retreat shaped by coast and forest.',
    openGraphUrl: 'https://stay.example/en/brand/story',
  },
  '공개 영어 페이지의 canonical과 Open Graph 메타데이터를 만든다',
)

expectEqual(
  websiteMetadata({
    origin: 'http://127.0.0.1:4000',
    pathname: '/brand/story',
    title: '브랜드 이야기',
    description: '저장된 초안입니다.',
    previewMode: true,
  }).robots,
  'noindex,nofollow',
  '저장 초안 미리보기는 검색 노출을 차단한다',
)

console.log('website-metadata: published and preview contracts passed')
