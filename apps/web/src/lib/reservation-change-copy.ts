const copy = {
  ko: {
    changeReservation: '예약 변경',
    priceChanged: '요금이 변경되었습니다. 새 금액을 확인해 주세요.',
    reservationChanged: '예약 조건이 변경되었습니다. 다시 조회해 주세요.',
    soldOut: '선택한 객실이 매진되었습니다. 다른 객실을 다시 선택해 주세요.',
    changeActive: '진행 중인 예약 변경을 먼저 완료해 주세요.',
    settlementDisabled: '예약 변경 정산이 비활성화되어 있습니다. 호텔에 문의해 주세요.',
    providerUnsupported: '현재 결제 수단으로는 이 예약을 변경할 수 없습니다. 호텔에 문의해 주세요.',
    refundUnavailable: '환불 가능한 결제 내역이 없습니다. 호텔에 문의해 주세요.',
  },
  en: {
    changeReservation: 'Change reservation',
    priceChanged: 'The price changed. Please review the new amount.',
    reservationChanged: 'The reservation details changed. Please check again.',
    soldOut: 'The selected room is sold out. Please choose another room.',
    changeActive: 'Complete the reservation change already in progress first.',
    settlementDisabled: 'Reservation change settlement is disabled. Please contact the hotel.',
    providerUnsupported: 'This reservation can be changed with the current payment method. Please contact the hotel.',
    refundUnavailable: 'No refundable payment is available. Please contact the hotel.',
  },
} as const

export type ChangeTextKey = keyof typeof copy.ko
export function changeText(locale: 'ko' | 'en', key: ChangeTextKey) { return copy[locale][key] }
