import { customerPaymentFailureMessage, customerPaymentFailureStatus, changePaymentStatusForDisplay, canRecoverChangePayment, describeReservationChangePayment, shouldPollReservationChangePayment, type ReservationChangePaymentState } from './reservation-change-payment-state.ts'

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
expectEqual(customerPaymentFailureMessage('ko'), '결제 링크가 만료되었습니다. 예약 상세에서 변경을 다시 시작해 주세요.', '만료 안내는 다음 행동을 한국어로 제시한다')
expectEqual(customerPaymentFailureMessage('en'), 'This payment link has expired. Start the change again from your reservation details.', '만료 안내는 다음 행동을 영어로 제시한다')

void ({} as ReservationChangePaymentState)

for (const stalePaymentStatus of ['UNKNOWN', 'APPROVING', 'PROCESSING']) {
  expectEqual(changePaymentStatusForDisplay('AWAITING_PAYMENT', stalePaymentStatus), 'RECONCILIATION_REQUIRED', '변경 대기 중 불확실 결제는 완료로 표시하지 않는다')
  for (const authoritativeStatus of ['READY_TO_APPLY', 'APPLYING', 'COMPLETED', 'CANCELLED', 'EXPIRED']) {
    expectEqual(changePaymentStatusForDisplay(authoritativeStatus, stalePaymentStatus), authoritativeStatus, '폴링으로 갱신한 변경 상태가 이전 Toss 대기 상태보다 우선한다')
  }
}
expectEqual(canRecoverChangePayment(true, null, false, undefined), true, '초기 확인 실패로 provider/payment가 없어도 Toss 복귀 복구를 허용한다')
expectEqual(canRecoverChangePayment(false, 'toss-test', true, undefined), true, '이전 Toss 조회 상태가 있으면 상세 로딩 실패 후에도 복구한다')
expectEqual(canRecoverChangePayment(false, 'fake', false, 'AWAITING_PAYMENT'), false, 'fake 결제에 Toss 복구를 표시하지 않는다')
expectEqual(canRecoverChangePayment(true, 'toss-test', true, 'COMPLETED'), false, '확인된 완료 화면은 결과 동선을 사용한다')

expectEqual(canRecoverChangePayment(false, 'toss-test', false, undefined), true, '새로고침 후 최초 status 실패에도 알려진 Toss provider로 복구한다')
expectEqual(canRecoverChangePayment(false, 'toss-live', false, undefined), true, '운영 Toss provider도 서버 상태 복구를 지원한다')
