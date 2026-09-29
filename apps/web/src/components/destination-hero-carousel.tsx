import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type FocusEvent } from 'react'
import { LazyMotion, m, useReducedMotion } from 'motion/react'

import type { DestinationHeroSlide } from '../lib/destination-content'
import { ResponsiveCmsImage } from './responsive-cms-image'

type DestinationHeroCarouselProps = {
  slides: DestinationHeroSlide[]
  locale?: 'ko' | 'en'
  interactionPaused?: boolean
  onResumeAutomaticRotation?: () => void
}

type HeroTransition = {
  id: number
  from: number
  to: number
}

const ROTATION_INTERVAL_MS = 5_000
const DESKTOP_GRID = { columns: 6, rows: 4 }
const MOBILE_GRID = { columns: 4, rows: 3 }
const loadMotionFeatures = () => import('../lib/motion-features').then(module => module.default)

function responsiveSrcSet(slide: DestinationHeroSlide) {
  return slide.variants?.map(variant => `${variant.deliveryUrl} ${variant.targetWidth}w`).join(', ')
}

function useCompactHeroGrid() {
  const [compact, setCompact] = useState(false)

  useEffect(() => {
    const query = window.matchMedia('(max-width: 640px)')
    const update = () => setCompact(query.matches)
    update()
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])

  return compact
}

export function DestinationHeroCarousel({ slides, locale = 'ko', interactionPaused = false, onResumeAutomaticRotation }: DestinationHeroCarouselProps) {
  const reducedMotion = useReducedMotion()
  const compactGrid = useCompactHeroGrid()
  const [activeIndex, setActiveIndex] = useState(0)
  const [transition, setTransition] = useState<HeroTransition | null>(null)
  const [manualPaused, setManualPaused] = useState(false)
  const [hoverPaused, setHoverPaused] = useState(false)
  const [focusPaused, setFocusPaused] = useState(false)
  const [documentVisible, setDocumentVisible] = useState(true)
  const [announceChanges, setAnnounceChanges] = useState(false)
  const transitionId = useRef(0)
  const slideIdentity = slides.map(slide => slide.assetId ?? slide.image).join('|')

  useEffect(() => {
    setActiveIndex(0)
    setTransition(null)
  }, [slideIdentity])

  useEffect(() => {
    const update = () => setDocumentVisible(document.visibilityState === 'visible')
    update()
    document.addEventListener('visibilitychange', update)
    return () => document.removeEventListener('visibilitychange', update)
  }, [])

  const beginTransition = useCallback((nextIndex: number, announce = true) => {
    if (slides.length < 2 || nextIndex === activeIndex || transition) return
    setAnnounceChanges(announce)
    if (reducedMotion) {
      setActiveIndex(nextIndex)
      return
    }
    transitionId.current += 1
    setTransition({ id: transitionId.current, from: activeIndex, to: nextIndex })
  }, [activeIndex, reducedMotion, slides.length, transition])

  const paused = manualPaused || Boolean(reducedMotion) || hoverPaused || focusPaused || interactionPaused || !documentVisible || transition !== null
  useEffect(() => {
    if (slides.length < 2 || paused) return
    const timer = window.setTimeout(() => beginTransition((activeIndex + 1) % slides.length, false), ROTATION_INTERVAL_MS)
    return () => window.clearTimeout(timer)
  }, [activeIndex, beginTransition, paused, slides.length])

  const finishTransition = useCallback((id: number, nextIndex: number) => {
    if (!transition || transition.id !== id) return
    setActiveIndex(nextIndex)
    setTransition(null)
  }, [transition])

  const grid = compactGrid ? MOBILE_GRID : DESKTOP_GRID
  const tiles = useMemo(() => Array.from({ length: grid.columns * grid.rows }, (_, index) => ({
    index,
    column: index % grid.columns,
    row: Math.floor(index / grid.columns),
  })), [grid.columns, grid.rows])

  const visualIndex = transition?.to ?? activeIndex
  const visualSlide = slides[visualIndex] ?? slides[0]
  const nextSlide = slides.length > 1 ? slides[(activeIndex + 1) % slides.length] : null
  const previousLabel = locale === 'en' ? 'Previous image' : '이전 이미지'
  const nextLabel = locale === 'en' ? 'Next image' : '다음 이미지'
  const regionLabel = locale === 'en' ? 'Resort main images' : '리조트 메인 이미지'
  const rotationLabel = manualPaused
    ? (locale === 'en' ? 'Resume automatic rotation' : '자동 전환 재생')
    : (locale === 'en' ? 'Pause automatic rotation' : '자동 전환 일시정지')

  if (!visualSlide) return null

  const handleBlur = (event: FocusEvent<HTMLElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocusPaused(false)
  }

  const toggleManualPause = () => {
    if (manualPaused) {
      setFocusPaused(false)
      onResumeAutomaticRotation?.()
    }
    setManualPaused(!manualPaused)
  }

  return (
    <LazyMotion features={loadMotionFeatures} strict>
    <div
      role="region"
      className="hero-carousel"
      aria-label={regionLabel}
      onMouseEnter={() => setHoverPaused(true)}
      onMouseLeave={() => setHoverPaused(false)}
      onFocusCapture={() => setFocusPaused(true)}
      onBlurCapture={handleBlur}
    >
      {nextSlide && <link
        rel="preload"
        as="image"
        href={nextSlide.image}
        imageSrcSet={responsiveSrcSet(nextSlide)}
        imageSizes="100vw"
        fetchPriority="low"
      />}

      <div className="hero-carousel-stage">
        {reducedMotion ? (
          <m.div
            key={visualSlide.assetId ?? visualSlide.image}
            className="hero-carousel-image hero-carousel-fade"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
          >
            <ResponsiveCmsImage
              src={visualSlide.image}
              imageVariants={visualSlide.variants}
              sizes="100vw"
              alt={visualSlide.alt}
              loading={activeIndex === 0 ? 'eager' : undefined}
              fetchPriority={activeIndex === 0 ? 'high' : 'auto'}
            />
          </m.div>
        ) : (
          <div className={`hero-carousel-image${transition ? ' is-transitioning' : ''}`}>
            <ResponsiveCmsImage
              src={visualSlide.image}
              imageVariants={visualSlide.variants}
              sizes="100vw"
              alt={visualSlide.alt}
              aria-hidden={Boolean(transition)}
              loading={activeIndex === 0 ? 'eager' : undefined}
              fetchPriority={activeIndex === 0 ? 'high' : 'auto'}
            />
          </div>
        )}

        {transition && <div
          className="hero-carousel-grid"
          style={{ '--hero-columns': grid.columns, '--hero-rows': grid.rows } as CSSProperties}
          aria-hidden="true"
        >
          {tiles.map(tile => {
            const outgoing = slides[transition.from]
            const incoming = slides[transition.to]
            const stagger = tile.column * 0.055 + tile.row * 0.012
            const tileStyle = {
              '--hero-column': tile.column,
              '--hero-row': tile.row,
              '--hero-columns': grid.columns,
              '--hero-rows': grid.rows,
            } as CSSProperties
            return <span className="hero-carousel-tile" style={tileStyle} key={`${transition.id}-${tile.index}`}>
              <m.span
                className="hero-carousel-tile-layer is-outgoing"
                initial={{ opacity: 1, scaleX: 1 }}
                animate={{ opacity: 0, scaleX: 0 }}
                transition={{ duration: 0.28, delay: stagger, ease: [0.4, 0, 0.2, 1] }}
              >
                <img
                  src={outgoing.image}
                  srcSet={responsiveSrcSet(outgoing)}
                  sizes="100vw"
                  alt=""
                  draggable={false}
                />
              </m.span>
              <m.span
                className="hero-carousel-tile-layer is-incoming"
                initial={{ opacity: 0, scaleX: 0 }}
                animate={{ opacity: 1, scaleX: 1 }}
                transition={{ duration: 0.28, delay: 0.22 + stagger, ease: [0.4, 0, 0.2, 1] }}
                onAnimationComplete={tile.index === tiles.length - 1
                  ? () => finishTransition(transition.id, transition.to)
                  : undefined}
              >
                <img
                  src={incoming.image}
                  srcSet={responsiveSrcSet(incoming)}
                  sizes="100vw"
                  alt=""
                  draggable={false}
                />
              </m.span>
            </span>
          })}
        </div>}
      </div>

      {slides.length > 1 && <div className="hero-carousel-controls">
        {!reducedMotion && <button
          type="button"
          className="hero-carousel-toggle"
          aria-label={rotationLabel}
          aria-pressed={manualPaused}
          onClick={toggleManualPause}
        >{manualPaused ? '▶' : 'Ⅱ'}</button>}
        <button type="button" className="hero-carousel-arrow" aria-label={previousLabel} onClick={() => beginTransition((activeIndex - 1 + slides.length) % slides.length)}>‹</button>
        <div className="hero-carousel-dots" aria-label={locale === 'en' ? 'Choose main image' : '메인 이미지 선택'}>
          {slides.map((slide, index) => <button
            type="button"
            className="hero-carousel-dot"
            aria-label={locale === 'en' ? `View main image ${index + 1}` : `메인 이미지 ${index + 1} 보기`}
            aria-current={index === activeIndex ? 'true' : undefined}
            onClick={() => beginTransition(index)}
            key={slide.assetId ?? slide.image}
          ><span>{index + 1}</span></button>)}
        </div>
        <button type="button" className="hero-carousel-arrow" aria-label={nextLabel} onClick={() => beginTransition((activeIndex + 1) % slides.length)}>›</button>
      </div>}

      <p className="sr-only" aria-live={announceChanges ? 'polite' : 'off'}>
        {locale === 'en'
          ? `Main image ${activeIndex + 1}/${slides.length}: ${slides[activeIndex]?.alt}`
          : `메인 이미지 ${activeIndex + 1}/${slides.length}: ${slides[activeIndex]?.alt}`}
      </p>
    </div>
    </LazyMotion>
  )
}
