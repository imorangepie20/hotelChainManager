import { destinationContentFromPublished } from './destination-content.ts'
import { parseContentPage } from './content-page.ts'

const actual = destinationContentFromPublished('속초', { title: 'English title' }, 'en')
if (actual !== null) throw new Error('불완전한 영어 문서를 한국어 정적 콘텐츠로 대체하면 안 됩니다.')

const page = parseContentPage({ seo: { title: 'Our story', description: 'English description' }, blocks: [{
  type: 'HERO', imageAssetId: '14000000-0000-0000-0000-000000000001', imageSrc: '/images/sokcho-coast-hero.png', imageAlt: 'Coast',
  title: 'Our story', description: 'English', cta: { label: 'Home', href: '/' },
}] }, 'en')
if (page?.blocks[0].cta?.href !== '/en') throw new Error('영어 CMS의 홈 링크는 영어 locale을 유지해야 합니다.')
