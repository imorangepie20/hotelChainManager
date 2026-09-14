import { changeText } from './reservation-change-copy'

function equal(actual: unknown, expected: unknown) { if (actual !== expected) throw new Error(`${actual} !== ${expected}`) }
equal(changeText('ko', 'changeReservation'), '예약 변경')
equal(changeText('en', 'changeReservation'), 'Change reservation')
equal(changeText('ko', 'priceChanged'), '요금이 변경되었습니다. 새 금액을 확인해 주세요.')
