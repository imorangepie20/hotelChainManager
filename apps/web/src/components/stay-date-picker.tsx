import { bookingText } from '../lib/booking-copy'
import { useEffect, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import { DayPicker, type DateRange } from 'react-day-picker'
import 'react-day-picker/style.css'
import { ko, enUS } from 'react-day-picker/locale'

type Props = { locale?: 'ko' | 'en'; checkIn: string; checkOut: string; onChange: (checkIn: string, checkOut: string) => void }
const parse = (value: string) => new Date(`${value}T00:00:00`)
const iso = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const format = (value: string, locale: 'ko' | 'en') => new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'ko-KR', { month: 'long', day: 'numeric', weekday: 'short' }).format(parse(value))
const nights = (from: Date, to: Date) => Math.round((to.getTime() - from.getTime()) / 86_400_000)

export function StayDatePicker({ checkIn, checkOut, onChange, locale = 'ko' }: Props) {
  const [open, setOpen] = useState(false); const [range, setRange] = useState<DateRange | undefined>({ from: parse(checkIn), to: parse(checkOut) })
  useEffect(() => setRange({ from: parse(checkIn), to: parse(checkOut) }), [checkIn, checkOut])
  const isComplete = Boolean(range?.from && range.to && range.from.getTime() !== range.to.getTime())
  function select(next: DateRange | undefined) { setRange(next) }
  function apply() { if (!range?.from || !range.to || range.from.getTime() === range.to.getTime()) return; onChange(iso(range.from), iso(range.to)); setOpen(false) }
  return <div className="stay-date-picker"><button type="button" className="date-trigger" aria-haspopup="dialog" aria-expanded={open} onClick={() => { setRange(undefined); setOpen(true) }}><CalendarDays size={16} /><span><small>{bookingText(locale, "체크인 / 체크아웃")}</small><strong>{format(checkIn, locale)} — {format(checkOut, locale)} <em>{nights(parse(checkIn), parse(checkOut))}{bookingText(locale, "박")}</em></strong></span></button>{open && <div className="calendar-popover" role="dialog" aria-label={bookingText(locale, "숙박 날짜 선택")}><div className="calendar-head"><div><p>{bookingText(locale, "체크인과 체크아웃 날짜를 선택하세요")}</p><strong>{bookingText(locale, "숙박 날짜 선택")}</strong></div></div><DayPicker mode="range" locale={locale === 'en' ? enUS : ko} selected={range} onSelect={select} disabled={{ before: new Date() }} numberOfMonths={1} showOutsideDays fixedWeeks weekStartsOn={0} /><div className="calendar-foot"><span>{range?.from && (!range.to || range.from.getTime() === range.to.getTime()) ? bookingText(locale, "체크아웃 날짜를 선택하세요.") : range?.from && range.to ? locale === 'en' ? `${nights(range.from, range.to)} nights selected.` : `${nights(range.from, range.to)}박을 선택했습니다.` : bookingText(locale, "선택 버튼을 누르면 날짜가 적용됩니다.")}</span><div><button type="button" onClick={() => setOpen(false)}>{bookingText(locale, "닫기")}</button><button type="button" className="calendar-apply" disabled={!isComplete} onClick={apply}>{bookingText(locale, "선택")}</button></div></div></div>}</div>
}





