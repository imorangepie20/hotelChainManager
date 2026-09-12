import { type PointerEvent as ReactPointerEvent, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, ChevronDown, ChevronUp } from 'lucide-react'
import type { ContentPageBlock, ContentPageDocument, ContentPageGalleryItem } from '../lib/content-page'
import type { BookingIntent } from '../lib/latest-availability-request'
import { gallerySwipeOffset, galleryTransition, type GalleryTransitionDirection } from '../lib/gallery-swipe'

type ContentPageHeroProps = {
  page: ContentPageDocument
  headingId?: string
  previewMode?: boolean
}

export function ContentPageHero({ page, headingId = 'content-page-title', previewMode = false }: ContentPageHeroProps) {
  const hero = page.blocks[0]
  if (!hero || hero.type !== 'HERO') return null

  return <section className="hero content-page-hero" aria-labelledby={headingId}>
    <img src={hero.imageSrc} alt={hero.imageAlt} />
    <div className="hero-shade" />
    <div className="hero-copy">
      {page.contentKind && <p className="content-kind-label">{page.contentKind}</p>}
      {hero.eyebrow && <p className="eyebrow">{hero.eyebrow}</p>}
      <h1 id={headingId}>{hero.title}</h1>
      {hero.description && <p>{hero.description}</p>}
      {hero.cta && (previewMode ? <button type="button" className="text-link" disabled>{hero.cta.label} <ArrowRight size={18} /></button> : <a href={hero.cta.href} className="text-link">{hero.cta.label} <ArrowRight size={18} /></a>)}
    </div>
  </section>
}

function ContentPageBlockView({ block, index, onBookingIntent, locale, previewMode }: { block: Exclude<ContentPageBlock, { type: 'HERO' }>; index: number; onBookingIntent?: (intent: BookingIntent) => void; locale: 'ko' | 'en'; previewMode?: boolean }) {
  if (block.type === 'TEXT') {
    return <section className="content-section content-page-text" key={`text-${index}`}>
      {block.eyebrow && <p className="section-kicker">{block.eyebrow}</p>}
      <h2>{block.title}</h2>
      {block.paragraphs.map((paragraph, paragraphIndex) => <p key={paragraphIndex}>{paragraph}</p>)}
    </section>
  }

  if (block.type === 'IMAGE_GALLERY') return <ContentPageGallery block={block} index={index} locale={locale} />

  if (block.type === 'FEATURE_GRID') return <section className="content-section content-page-detail" aria-labelledby={`detail-${index}`}>
    <BlockHeading block={block} id={`detail-${index}`} />
    <div className="content-feature-grid">{block.items.map((item, itemIndex) => <article key={itemIndex}><h3>{item.title}</h3><p>{item.description}</p></article>)}</div>
  </section>

  if (block.type === 'SPEC_TABLE') return <section className="content-section content-page-detail" aria-labelledby={`detail-${index}`}>
    <BlockHeading block={block} id={`detail-${index}`} />
    <dl className="content-specification-table">{block.rows.map((row, rowIndex) => <div key={rowIndex}><dt>{row.label}</dt><dd>{row.value}</dd></div>)}</dl>
  </section>

  if (block.type === 'ACCORDION') return <section className="content-section content-page-detail" aria-labelledby={`detail-${index}`}>
    <BlockHeading block={block} id={`detail-${index}`} />
    <div className="content-accordion">{block.items.map((item, itemIndex) => <details key={itemIndex}><summary>{item.title}<ChevronDown size={18} aria-hidden="true" /></summary><p>{item.content}</p></details>)}</div>
  </section>

  if (block.type === 'NOTICE_LIST') return <section className="content-section content-page-detail" aria-labelledby={`detail-${index}`}>
    <BlockHeading block={block} id={`detail-${index}`} />
    <ul className="content-notice-list">{block.items.map((item, itemIndex) => <li className={item.severity === 'IMPORTANT' ? 'important' : ''} key={itemIndex}>{item.text}</li>)}</ul>
  </section>

  if (block.type === 'RICH_TEXT') return <section className="content-section content-page-text" aria-labelledby={`rich-text-${index}`}>
    <p className="section-kicker">{block.eyebrow}</p><h2 id={`rich-text-${index}`}>{block.title}</h2>{block.paragraphs.map((paragraph, paragraphIndex) => <p key={paragraphIndex}>{paragraph}</p>)}
  </section>

  if (block.type === 'OPERATING_HOURS') return <section className="content-section content-page-detail" aria-labelledby={`hours-${index}`}>
    <BlockHeading block={block} id={`hours-${index}`} />
    <table className="content-operating-hours"><tbody>{block.entries.map(entry => <tr key={entry.dayLabel}><th scope="row">{entry.dayLabel}</th><td>{entry.closed ? (locale === 'en' ? 'Closed' : '휴무') : `${entry.opensAt} – ${entry.closesAt}`}</td></tr>)}</tbody></table>
    {block.exceptions && <p className="content-block-note">{block.exceptions}</p>}
  </section>

  if (block.type === 'LOCATION') return <section className="content-section content-page-detail" aria-labelledby={`location-${index}`}>
    <h2 id={`location-${index}`}>{block.title}</h2><address>{block.address}</address>{block.directions && <p>{block.directions}</p>}{block.mapHref && (previewMode ? <button type="button" className="text-link dark" disabled>{locale === 'en' ? 'View directions' : '오시는 길 보기'} <ArrowRight size={17} /></button> : <a className="text-link dark" href={block.mapHref}>{locale === 'en' ? 'View directions' : '오시는 길 보기'} <ArrowRight size={17} /></a>)}
  </section>

  if (block.type === 'PROMOTION_SUMMARY') return <section className="content-section content-page-detail" aria-labelledby={`promotion-${index}`}>
    <h2 id={`promotion-${index}`}>{block.title}</h2><dl className="content-promotion-summary"><div><dt>{locale === 'en' ? 'Booking period' : '예약 기간'}</dt><dd>{block.salesPeriod}</dd></div><div><dt>{locale === 'en' ? 'Stay period' : '투숙 기간'}</dt><dd>{block.stayPeriod}</dd></div></dl><ul>{block.benefits.map(benefit => <li key={benefit}>{benefit}</li>)}</ul><p className="content-block-note">{locale === 'en' ? 'Display information only. Booking prices are recalculated for your selected criteria.' : '표시 정보이며 실제 예약 가격은 선택 조건에서 다시 계산됩니다.'}</p>
  </section>

  if (block.type === 'RELATED_COLLECTION') return <section className="content-section content-page-detail" aria-labelledby={`related-${index}`}>
    <h2 id={`related-${index}`}>{block.title}</h2><p>{locale === 'en' ? `Find related ${block.kind.toLowerCase()} content in the collection.` : `관련 ${block.kind.toLowerCase()} 콘텐츠를 목록에서 확인할 수 있습니다.`}</p>
  </section>

  if (block.type === 'BOOKING_CTA') return <section className="content-page-booking-cta" aria-labelledby={`booking-cta-${index}`}>
    <div>{block.eyebrow && <p className="section-kicker">{block.eyebrow}</p>}<h2 id={`booking-cta-${index}`}>{block.title}</h2><p>{block.description}</p></div><button type="button" className="primary" disabled={previewMode} onClick={() => !previewMode && onBookingIntent?.({ hotelId: block.hotelId, roomTypeId: block.roomTypeId })}>{block.label} <ArrowRight size={18} /></button>
  </section>

  if (!block.cta) return null
  return <section className="content-page-cta" key={`cta-${index}`}>
    <div>
      {block.eyebrow && <p className="section-kicker">{block.eyebrow}</p>}
      <h2>{block.title}</h2>
      {block.description && <p>{block.description}</p>}
    </div>
    {previewMode ? <button type="button" className="outline" disabled>{block.cta.label} <ArrowRight size={17} /></button> : <a href={block.cta.href} className="outline">{block.cta.label} <ArrowRight size={17} /></a>}
  </section>
}

function BlockHeading({ block, id }: { block: Exclude<ContentPageBlock, { type: 'HERO' | 'TEXT' | 'CTA' }>; id: string }) {
  return <div className="content-detail-heading">
    {block.eyebrow && <p className="section-kicker">{block.eyebrow}</p>}
    <h2 id={id}>{block.title}</h2>
    {block.description && <p>{block.description}</p>}
  </div>
}

function ContentPageGallery({ block, index, locale }: { block: Extract<ContentPageBlock, { type: 'IMAGE_GALLERY' }>; index: number; locale: 'ko' | 'en' }) {
  const [selected, setSelected] = useState(0)
  const [outgoing, setOutgoing] = useState<{ item: ContentPageGalleryItem; direction: GalleryTransitionDirection; key: number } | null>(null)
  const [motionKey, setMotionKey] = useState(0)
  const swipeStart = useRef<{ pointerId: number; x: number; y: number } | null>(null)
  const transitionKey = useRef(0)
  const item = block.items[selected] ?? block.items[0]
  if (!item) return null
  const showTransition = (next: number, direction: GalleryTransitionDirection) => {
    const current = block.items[selected]
    if (!current || next === selected) return
    const key = ++transitionKey.current
    setOutgoing({ item: current, direction, key })
    setMotionKey(key)
    setSelected(next)
  }
  const selectByOffset = (offset: -1 | 1) => {
    const next = galleryTransition(selected, offset, block.items.length)
    showTransition(next.index, next.direction)
  }
  const selectThumbnail = (next: number) => showTransition(next, next > selected ? 'forward' : 'backward')
  const startSwipe = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!event.isPrimary || event.button !== 0 || (event.target as HTMLElement).closest('button')) return
    swipeStart.current = { pointerId: event.pointerId, x: event.clientX, y: event.clientY }
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const finishSwipe = (event: ReactPointerEvent<HTMLDivElement>) => {
    const start = swipeStart.current
    if (!start || start.pointerId !== event.pointerId) return
    swipeStart.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    const offset = gallerySwipeOffset(start, { x: event.clientX, y: event.clientY })
    if (offset) selectByOffset(offset)
  }
  const cancelSwipe = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (swipeStart.current?.pointerId !== event.pointerId) return
    swipeStart.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
  }
  return <section className="content-section content-page-gallery" aria-labelledby={`gallery-${index}`}>
    <BlockHeading block={block} id={`gallery-${index}`} />
    <div className={`content-gallery-stage${outgoing ? ' is-transitioning' : ''}`} data-direction={outgoing?.direction} onPointerDown={startSwipe} onPointerUp={finishSwipe} onPointerCancel={cancelSwipe}>
      <div className="content-gallery-media">
        {outgoing && <img className="content-gallery-image content-gallery-image-exit" src={outgoing.item.imageSrc} alt="" aria-hidden="true" onAnimationEnd={() => setOutgoing(current => current?.key === outgoing.key ? null : current)} />}
        <img key={`${selected}-${motionKey}`} className="content-gallery-image content-gallery-image-enter" src={item.imageSrc} alt={item.imageAlt} />
      </div>
      <div className="content-gallery-controls"><button type="button" aria-label={locale === 'en' ? 'Previous image' : '이전 이미지'} onClick={() => selectByOffset(-1)}><ArrowLeft size={20} /></button><span key={`position-${motionKey}`} className="content-gallery-position" aria-live="polite">{selected + 1} / {block.items.length}</span><button type="button" aria-label={locale === 'en' ? 'Next image' : '다음 이미지'} onClick={() => selectByOffset(1)}><ArrowRight size={20} /></button></div>
      {item.caption && <p key={`caption-${motionKey}`} className="content-gallery-caption">{item.caption}</p>}
    </div>
    <div className="content-gallery-thumbnails" aria-label={locale === 'en' ? 'Select gallery image' : '갤러리 이미지 선택'}>{block.items.map((thumbnail, thumbnailIndex) => <button type="button" aria-label={locale === 'en' ? `View image ${thumbnailIndex + 1}` : `${thumbnailIndex + 1}번 이미지 보기`} aria-pressed={thumbnailIndex === selected} onClick={() => selectThumbnail(thumbnailIndex)} key={thumbnailIndex}><img src={thumbnail.imageSrc} alt="" /></button>)}</div>
  </section>
}

export function ContentPageAfterHero({ page, onBookingIntent, locale = 'ko', previewMode = false }: { page: ContentPageDocument; onBookingIntent?: (intent: BookingIntent) => void; locale?: 'ko' | 'en'; previewMode?: boolean }) {
  const firstBlockIsHero = page.blocks[0]?.type === 'HERO'
  return <>
    {!firstBlockIsHero && page.contentKind && <p className="content-kind-label content-section">{page.contentKind}</p>}
    {page.blocks.slice(firstBlockIsHero ? 1 : 0).map((block, index) => {
      if (block.type === 'HERO') return null
      const blockIndex = index + (firstBlockIsHero ? 1 : 0)
      return <ContentPageBlockView block={block} index={blockIndex} previewMode={previewMode} onBookingIntent={onBookingIntent} locale={locale} key={`${block.type.toLowerCase()}-${blockIndex}`} />
    })}
  </>
}

export function ContentPage({ page, onBookingIntent, locale = 'ko', previewMode = false }: { page: ContentPageDocument; onBookingIntent?: (intent: BookingIntent) => void; locale?: 'ko' | 'en'; previewMode?: boolean }) {
  return <>
    <ContentPageHero page={page} previewMode={previewMode} />
    <ContentPageAfterHero page={page} previewMode={previewMode} onBookingIntent={onBookingIntent} locale={locale} />
  </>
}
