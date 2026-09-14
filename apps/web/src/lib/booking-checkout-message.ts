export function bookingCheckoutErrorMessage(code?: string) {
  if (code === 'PRICE_CHANGED') return '요금이 변경되었습니다. 최신 객실과 금액을 다시 확인해 주세요.'
  if (code === 'SOLD_OUT') return '선택한 객실이 매진되었습니다. 다른 객실을 다시 선택해 주세요.'
  if (code === 'HOLD_EXPIRED') return '객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.'
  return '현재 상태를 확인하지 못했습니다. 입력 내용은 유지되어 있습니다. 잠시 후 다시 시도해 주세요.'
}
