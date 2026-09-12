import { useEffect, useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { api } from '../lib/api'
import { parseContentCollection, type ContentCollectionItem } from '../lib/content-collection'
import type { ContentKind } from '../lib/content-page'

type ContentCollectionPageProps = { contentKind: ContentKind; hotelSlug?: string; hotelName?: string }

const labelByKind: Record<ContentKind, string> = {
  ROOM: '객실', DINING: '다이닝', FACILITY: '부대시설', EXPERIENCE: '경험', PROMOTION: '프로모션', GUIDE: '이용 안내', BRAND: '브랜드',
}

export function ContentCollectionPage({ contentKind, hotelSlug, hotelName }: ContentCollectionPageProps) {
  const [items, setItems] = useState<ContentCollectionItem[] | null>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    let active = true
    setItems(null); setFailed(false)
    api.websiteCollection(contentKind, hotelSlug)
      .then(value => {
        const parsed = parseContentCollection(value)
        if (!parsed) throw new Error('공개 목록 형식이 올바르지 않습니다.')
        if (active) setItems(parsed)
      })
      .catch(() => { if (active) setFailed(true) })
    return () => { active = false }
  }, [contentKind, hotelSlug])
  const label = labelByKind[contentKind]
  if (failed) return <section className="content-section content-collection-state"><h1>{label} 정보를 불러오지 못했습니다.</h1><p>잠시 후 다시 시도해 주세요.</p></section>
  if (items === null) return <section className="content-section content-collection-state" aria-live="polite"><p>{label} 정보를 불러오는 중입니다.</p></section>
  if (!items.length) return <section className="content-section content-collection-state"><h1>{hotelName ?? '선택한 지점'}의 {label} 준비 중입니다.</h1><p>다른 지점의 머무름과 현재 공개된 콘텐츠를 확인해 보세요.</p>{hotelSlug && <a className="text-link dark" href={`/stays/${hotelSlug}`}>지점으로 돌아가기 <ArrowRight size={18} /></a>}</section>
  return <main className="content-collection-page"><section className="content-section content-collection-heading"><p className="content-kind-label">{label}</p><h1>{hotelName ? `${hotelName} ${label}` : label}</h1></section><section className="content-collection-grid" aria-label={`${label} 목록`}>{items.map(item => <article key={item.id} className="content-collection-card"><img src={item.image} alt="" /><div><p>{labelByKind[item.contentKind]}</p><h2>{item.title}</h2><p>{item.summary}</p><a href={item.path}>자세히 보기 <ArrowRight size={17} /></a></div></article>)}</section></main>
}
