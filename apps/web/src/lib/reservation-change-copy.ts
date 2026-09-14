const copy = {
  ko: { changeReservation: '예약 변경', priceChanged: '요금이 변경되었습니다. 새 금액을 확인해 주세요.' },
  en: { changeReservation: 'Change reservation', priceChanged: 'The price changed. Please review the new amount.' },
} as const

export type ChangeTextKey = keyof typeof copy.ko
export function changeText(locale: 'ko' | 'en', key: ChangeTextKey) { return copy[locale][key] }
