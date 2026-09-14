import { bookingCheckoutErrorMessage } from './booking-checkout-message.ts'

function equal(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, received ${String(actual)}`)
}

equal(bookingCheckoutErrorMessage('PRICE_CHANGED'), '요금이 변경되었습니다. 최신 객실과 금액을 다시 확인해 주세요.', '가격 변경은 최신 금액 확인 안내를 보여야 한다')
equal(bookingCheckoutErrorMessage('SOLD_OUT'), '선택한 객실이 매진되었습니다. 다른 객실을 다시 선택해 주세요.', '판매 완료는 다른 객실 선택 안내를 보여야 한다')
equal(bookingCheckoutErrorMessage('HOLD_EXPIRED'), '객실 확보 시간이 만료되었습니다. 객실을 다시 검색해 주세요.', '확보 만료는 재검색 안내를 보여야 한다')
equal(bookingCheckoutErrorMessage('UNEXPECTED'), '현재 상태를 확인하지 못했습니다. 입력 내용은 유지되어 있습니다. 잠시 후 다시 시도해 주세요.', '알 수 없는 오류는 입력 보존 안내를 보여야 한다')

console.log('booking checkout error contracts passed')
