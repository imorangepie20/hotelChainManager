import { useState } from 'react'
import { ApiFailure, api, type Hotel, type GuestRequestInput } from '../lib/api'

type Props = { hotels: Hotel[]; locale: 'ko' | 'en' }

const words = {
  ko: {
    title: '호텔에 요청하기',
    description: '객실·편의 요청이나 환불·일반 문의를 남길 수 있습니다. 예약 변경과 취소는 내 예약에서 진행해 주세요.',
    hotel: '지점',
    hotelPlaceholder: '지점을 선택해 주세요',
    type: '요청 유형',
    types: { ROOM_REQUEST: '객실 요청', AMENITY_REQUEST: '편의 요청', REFUND_INQUIRY: '환불 문의', GENERAL_INQUIRY: '일반 문의', OTHER: '기타' },
    subject: '제목',
    subjectPlaceholder: '제목을 입력해 주세요',
    body: '내용',
    bodyPlaceholder: '문의 내용을 입력해 주세요',
    name: '이름',
    namePlaceholder: '이름을 입력해 주세요',
    email: '이메일',
    emailPlaceholder: 'name@example.com',
    phone: '전화번호(선택)',
    phonePlaceholder: '010-0000-0000',
    submit: '요청 보내기',
    submitting: '보내는 중…',
    success: '요청을 접수했습니다. 호텔에서 입력하신 이메일로 답변합니다.',
    failure: '요청을 보내지 못했습니다. 입력값을 확인하고 다시 시도해 주세요.',
    back: '홈으로',
  },
  en: {
    title: 'Contact the hotel',
    description: 'Leave a room or amenity request, or a refund and general inquiry. Reservation changes and cancellations are handled in My reservations.',
    hotel: 'Hotel',
    hotelPlaceholder: 'Select a hotel',
    type: 'Request type',
    types: { ROOM_REQUEST: 'Room request', AMENITY_REQUEST: 'Amenity request', REFUND_INQUIRY: 'Refund inquiry', GENERAL_INQUIRY: 'General inquiry', OTHER: 'Other' },
    subject: 'Subject',
    subjectPlaceholder: 'Enter a subject',
    body: 'Message',
    bodyPlaceholder: 'Enter your message',
    name: 'Name',
    namePlaceholder: 'Enter your name',
    email: 'Email',
    emailPlaceholder: 'name@example.com',
    phone: 'Phone (optional)',
    phonePlaceholder: '010-0000-0000',
    submit: 'Send request',
    submitting: 'Sending…',
    success: 'Your request was received. The hotel will reply by email.',
    failure: 'We could not send the request. Please check your input and try again.',
    back: 'Home',
  },
} as const

const REQUEST_TYPES: GuestRequestInput['requestType'][] = ['ROOM_REQUEST', 'AMENITY_REQUEST', 'REFUND_INQUIRY', 'GENERAL_INQUIRY', 'OTHER']

export function GuestRequestPage({ hotels, locale }: Props) {
  const text = words[locale]
  const [hotelId, setHotelId] = useState('')
  const [requestType, setRequestType] = useState<GuestRequestInput['requestType']>('GENERAL_INQUIRY')
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [guestName, setGuestName] = useState('')
  const [guestEmail, setGuestEmail] = useState('')
  const [guestPhone, setGuestPhone] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [receiptId, setReceiptId] = useState('')
  const [error, setError] = useState('')

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!hotelId || submitting) return
    setSubmitting(true)
    setError('')
    try {
      const receipt = await api.submitGuestRequest(hotelId, crypto.randomUUID(), {
        requestType,
        subject: subject.trim(),
        body: body.trim(),
        guestName: guestName.trim(),
        guestEmail: guestEmail.trim(),
        guestPhone: guestPhone.trim() || null,
      })
      setReceiptId(receipt.requestId)
    } catch (cause) {
      setError(cause instanceof ApiFailure ? cause.message : text.failure)
    } finally {
      setSubmitting(false)
    }
  }

  if (receiptId) {
    return (
      <main className="content-section" aria-labelledby="guest-request-title">
        <p className="section-kicker">CONTACT</p>
        <h1 id="guest-request-title">{text.title}</h1>
        <p role="status">{text.success}</p>
        <p className="text-sm" style={{ color: 'var(--muted-foreground, #6b7280)' }}>
          {receiptId.slice(0, 8)}
        </p>
        <a className="outline" href={locale === 'en' ? '/en' : '/'}>{text.back}</a>
      </main>
    )
  }

  return (
    <main className="content-section" aria-labelledby="guest-request-title">
      <p className="section-kicker">CONTACT</p>
      <h1 id="guest-request-title">{text.title}</h1>
      <p>{text.description}</p>
      <form onSubmit={submit} className="guest-request-form" aria-label={text.title}>
        <label className="grid gap-1">
          <span>{text.hotel}</span>
          <select value={hotelId} onChange={event => setHotelId(event.target.value)} required>
            <option value="">{text.hotelPlaceholder}</option>
            {hotels.map(hotel => <option key={hotel.id} value={hotel.id}>{hotel.name}</option>)}
          </select>
        </label>
        <label className="grid gap-1">
          <span>{text.type}</span>
          <select value={requestType} onChange={event => setRequestType(event.target.value as GuestRequestInput['requestType'])}>
            {REQUEST_TYPES.map(type => <option key={type} value={type}>{text.types[type]}</option>)}
          </select>
        </label>
        <label className="grid gap-1">
          <span>{text.subject}</span>
          <input value={subject} onChange={event => setSubject(event.target.value)} required maxLength={100} placeholder={text.subjectPlaceholder} />
        </label>
        <label className="grid gap-1">
          <span>{text.body}</span>
          <textarea value={body} onChange={event => setBody(event.target.value)} required maxLength={2000} placeholder={text.bodyPlaceholder} rows={6} />
        </label>
        <div className="grid gap-1 sm:grid-cols-2">
          <label className="grid gap-1">
            <span>{text.name}</span>
            <input value={guestName} onChange={event => setGuestName(event.target.value)} required maxLength={100} placeholder={text.namePlaceholder} />
          </label>
          <label className="grid gap-1">
            <span>{text.email}</span>
            <input type="email" value={guestEmail} onChange={event => setGuestEmail(event.target.value)} required maxLength={254} placeholder={text.emailPlaceholder} />
          </label>
        </div>
        <label className="grid gap-1">
          <span>{text.phone}</span>
          <input value={guestPhone} onChange={event => setGuestPhone(event.target.value)} maxLength={30} placeholder={text.phonePlaceholder} />
        </label>
        {error && <p role="alert" style={{ color: 'var(--destructive, #b91c1c)' }}>{error}</p>}
        <button className="primary" type="submit" disabled={submitting || !hotelId}>
          {submitting ? text.submitting : text.submit}
        </button>
      </form>
    </main>
  )
}
