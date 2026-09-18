import { changeText, type ChangeTextKey } from './reservation-change-copy'

function equal(actual: unknown, expected: unknown, message: string) { if (actual !== expected) throw new Error(`${message}: ${String(actual)} !== ${String(expected)}`) }
equal(changeText('ko', 'changeReservation'), '예약 변경', '예약 변경 제목을 유지한다')
equal(changeText('en', 'changeReservation'), 'Change reservation', '영문 예약 변경 제목을 유지한다')
equal(changeText('ko', 'priceChanged'), '요금이 변경되었습니다. 새 금액을 확인해 주세요.', '가격 변경 안내를 유지한다')
equal(changeText('en', 'priceChanged'), 'The price changed. Please review the new amount.', '영문 가격 변경 안내를 유지한다')

// 고객 변경 시작 거부 코드는 서버가 판단한 상태를 그대로 노출하지 않고 행동을 안내한다.
for (const key of ['reservationChanged', 'soldOut', 'changeActive', 'settlementDisabled', 'providerUnsupported', 'refundUnavailable'] as ChangeTextKey[]) {
  equal(/[가-힣]/.test(changeText('en', key)), false, `영문 변경 거부 안내에 한글을 섞지 않는다: ${key}`)
  equal(changeText('ko', key).length > 0, true, `한국어 변경 거부 안내가 있다: ${key}`)
}

console.log('reservation change copy contracts passed')
