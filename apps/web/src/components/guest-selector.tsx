import { useState } from 'react'
import { Minus, Plus, Users } from 'lucide-react'

type Props = { adults: number; children: number; rooms: number; onChange: (next: { adults: number; children: number; rooms: number }) => void }
export function GuestSelector({ adults, children, rooms, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const adjust = (key: 'adults' | 'children' | 'rooms', delta: number, minimum: number) => onChange({ adults, children, rooms, [key]: Math.max(minimum, ({ adults, children, rooms }[key] + delta)) })
  const rows: Array<{ key: 'adults' | 'children' | 'rooms'; label: string; detail: string; minimum: number }> = [{ key: 'adults', label: '성인', detail: '만 13세 이상', minimum: 1 }, { key: 'children', label: '아동', detail: '만 12세 이하', minimum: 0 }, { key: 'rooms', label: '객실', detail: '객실 수', minimum: 1 }]
  return <div className="guest-selector"><button type="button" className="guest-trigger" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(value => !value)}><Users size={16} /><span><small>투숙 인원</small><strong>성인 {adults} · 아동 {children} · 객실 {rooms}</strong></span></button>{open && <div className="guest-popover" role="dialog" aria-label="투숙 인원 선택">{rows.map(row => <div className="guest-row" key={row.key}><div><strong>{row.label}</strong><small>{row.detail}</small></div><div><button type="button" aria-label={`${row.label} 줄이기`} disabled={({ adults, children, rooms }[row.key] <= row.minimum)} onClick={() => adjust(row.key, -1, row.minimum)}><Minus size={15} /></button><output>{({ adults, children, rooms }[row.key])}</output><button type="button" aria-label={`${row.label} 늘리기`} onClick={() => adjust(row.key, 1, row.minimum)}><Plus size={15} /></button></div></div>)}<button type="button" className="guest-confirm" onClick={() => setOpen(false)}>선택</button></div>}</div>
}
