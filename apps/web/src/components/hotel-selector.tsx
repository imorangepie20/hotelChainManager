import { bookingText } from '../lib/booking-copy'
import { useEffect, useId, useRef, useState } from 'react'
import { Check, ChevronDown, MapPin } from 'lucide-react'

import type { Hotel } from '../lib/api'
import { nextHotelIndex } from '../lib/hotel-selector-state'

type HotelSelectorProps = {
  locale?: 'ko' | 'en'
  hotels: Hotel[]
  value: string
  onChange: (hotelId: string) => void
}

export function HotelSelector({ hotels, value, onChange, locale = 'ko' }: HotelSelectorProps) {
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(() => hotels.findIndex(hotel => hotel.id === value))
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([])
  const listboxId = useId()
  const selectedHotel = hotels.find(hotel => hotel.id === value)

  useEffect(() => {
    if (!open) return
    const selectedIndex = hotels.findIndex(hotel => hotel.id === value)
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0)
  }, [open, hotels, value])

  useEffect(() => {
    if (!open || activeIndex < 0) return
    optionRefs.current[activeIndex]?.focus()
  }, [activeIndex, open])

  useEffect(() => {
    const closeOnOutsidePointer = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutsidePointer)
    return () => document.removeEventListener('mousedown', closeOnOutsidePointer)
  }, [])

  function close(returnFocus = false) {
    setOpen(false)
    if (returnFocus) requestAnimationFrame(() => triggerRef.current?.focus())
  }

  function choose(hotelId: string) {
    close()
    onChange(hotelId)
  }

  function move(key: string) {
    setActiveIndex(current => nextHotelIndex(current < 0 ? 0 : current, hotels.length, key))
  }

  function handleTriggerKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      move(event.key)
    }
  }

  function handleOptionKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, index: number) {
    if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
      event.preventDefault()
      move(event.key)
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      choose(hotels[index].id)
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      close(true)
    }
  }

  return <div className="hotel-selector" ref={rootRef}>
    <span className="hotel-selector-label"><MapPin size={16} />{locale === 'en' ? 'Hotel' : '지점'}</span>
    <button ref={triggerRef} type="button" className="hotel-selector-trigger" aria-haspopup="listbox" aria-controls={listboxId} aria-expanded={open} onClick={() => setOpen(current => !current)} onKeyDown={handleTriggerKeyDown}>
      <span><small>{selectedHotel?.region ?? bookingText(locale, "지점 선택")}</small><strong>{selectedHotel?.name ?? (locale === 'en' ? 'Choose a hotel' : '호텔을 선택해 주세요')}</strong></span>
      <ChevronDown size={18} aria-hidden="true" />
    </button>
    {open && <div id={listboxId} role="listbox" aria-label={bookingText(locale, "지점 선택")} className="hotel-selector-menu">
      <p>{locale === 'en' ? 'Where would you like to stay?' : '어디에서 머무를까요?'}</p>
      {hotels.map((hotel, index) => <button key={hotel.id} ref={element => { optionRefs.current[index] = element }} type="button" role="option" aria-selected={hotel.id === value} className={hotel.id === value ? 'selected' : ''} onClick={() => choose(hotel.id)} onKeyDown={event => handleOptionKeyDown(event, index)}>
        <span><small>{hotel.region}</small><strong>{hotel.name}</strong></span>
        {hotel.id === value && <Check size={17} aria-label="선택됨" />}
      </button>)}
    </div>}
  </div>
}
