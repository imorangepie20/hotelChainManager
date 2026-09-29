import { useState, type FocusEvent, type ReactNode } from 'react'

import type { DestinationHeroSlide } from '../lib/destination-content'
import { DestinationHeroCarousel } from './destination-hero-carousel'

type DestinationHeroProps = {
  slides: DestinationHeroSlide[]
  locale?: 'ko' | 'en'
  labelledBy?: string
  children: ReactNode
}

export function DestinationHero({ slides, locale = 'ko', labelledBy, children }: DestinationHeroProps) {
  const [hoverPaused, setHoverPaused] = useState(false)
  const [focusPaused, setFocusPaused] = useState(false)

  const handleBlur = (event: FocusEvent<HTMLElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocusPaused(false)
  }

  return <section
    className="hero"
    aria-labelledby={labelledBy}
    onMouseEnter={() => setHoverPaused(true)}
    onMouseLeave={() => setHoverPaused(false)}
    onFocusCapture={() => setFocusPaused(true)}
    onBlurCapture={handleBlur}
  >
    <DestinationHeroCarousel
      slides={slides}
      locale={locale}
      interactionPaused={hoverPaused || focusPaused}
      onResumeAutomaticRotation={() => setFocusPaused(false)}
    />
    {children}
  </section>
}
