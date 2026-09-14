import { customerPaymentFailureStatus, describeReservationChangePayment, shouldPollReservationChangePayment, type ReservationChangePaymentState } from './reservation-change-payment-state.ts'

function expectEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, received ${String(actual)}`)
}

const change = {
  previousCheckIn: '2026-10-10', previousCheckOut: '2026-10-12', previousRoomTypeName: '스탠다드 시티', previousRatePlanName: '룸 온리', previousTotalKrw: 340000,
  checkIn: '2026-10-20', checkOut: '2026-10-23', roomTypeName: '디럭스 오션', ratePlanName: '조식 포함', totalKrw: 440000, differenceKrw: 100000,
}

const awaiting = describeReservationChangePayment({ ...change, status: 'AWAITING_PAYMENT' })
expectEqual(awaiting.kind, 'AWAITING_PAYMENT', '서버의 결제 대기 상태를 유지한다')
expectEqual(awaiting.canCheckout, true, '결제 대기 상태에서만 결제를 허용한다')
expectEqual(awaiting.direction, 'CHARGE', '서버 차액의 정산 방향을 의미값으로 유지한다')
expectEqual(shouldPollReservationChangePayment(awaiting.kind), true, '결제 대기 중에는 provider 결과와 만료를 다시 확인한다')

const applying = describeReservationChangePayment({ ...change, status: 'APPLYING' })
expectEqual(applying.kind, 'APPLYING', '서버 적용 중 상태를 유지한다')
expectEqual(applying.canCheckout, false, '적용 중에는 중복 결제를 차단한다')
expectEqual(shouldPollReservationChangePayment(applying.kind), true, '적용 중에는 완료 상태를 다시 확인한다')

const expired = describeReservationChangePayment({ ...change, status: 'EXPIRED' })
expectEqual(expired.kind, 'EXPIRED', '만료 상태를 화면에 남긴다')
expectEqual(expired.canCheckout, false, '만료 링크는 결제를 차단한다')
expectEqual(shouldPollReservationChangePayment(expired.kind), false, '만료 뒤 timer를 멈춘다')

const unknown = describeReservationChangePayment({ ...change, status: 'RECONCILIATION_REQUIRED' })
expectEqual(unknown.kind, 'RECONCILIATION_REQUIRED', '불명확 결과를 성공으로 바꾸지 않는다')
expectEqual(unknown.canCheckout, false, '조정 필요 상태는 결제를 차단한다')

const completed = describeReservationChangePayment({ ...change, status: 'COMPLETED' })
expectEqual(completed.kind, 'COMPLETED', '완료 상태를 완료로 표시한다')
expectEqual(completed.canCheckout, false, '완료 후 재결제를 차단한다')

const cancelled = describeReservationChangePayment({ ...change, status: 'CANCELLED' })
expectEqual(cancelled.kind, 'CANCELLED', 'provider 명시 실패 뒤 취소 상태를 실패로 표시한다')
expectEqual(cancelled.canCheckout, false, '취소 상태에서 재결제를 차단한다')
expectEqual(customerPaymentFailureStatus(401), 'EXPIRED', '만료된 고객 세션 401을 만료 상태로 바꾼다')
expectEqual(customerPaymentFailureStatus(404), 'EXPIRED', '만료된 고객 세션 404를 만료 상태로 바꾼다')
expectEqual(customerPaymentFailureStatus(500), null, '서버 오류는 만료로 위장하지 않는다')

void ({} as ReservationChangePaymentState)
