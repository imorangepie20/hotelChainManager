import { canContinueChangePayment, formatReservationDateTime, reservationStatusLabel } from './reservation-management-state.ts'

function equal(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, received ${String(actual)}`)
}

equal(reservationStatusLabel('PENDING_PAYMENT'), '결제 대기', '결제 대기 상태를 고객 언어로 표시한다')
equal(reservationStatusLabel('CONFIRMED'), '예약 확정', '확정 상태를 고객 언어로 표시한다')
equal(reservationStatusLabel('CANCELLING'), '취소 처리 중', '취소 처리 상태를 고객 언어로 표시한다')
equal(reservationStatusLabel('CANCELLED'), '취소 완료', '취소 완료 상태를 고객 언어로 표시한다')
equal(reservationStatusLabel('EXPIRED'), '만료', '만료 상태를 고객 언어로 표시한다')
equal(canContinueChangePayment('AWAITING_PAYMENT', '12345678-1234-1234-1234-123456789abc', '12345678-1234-1234-1234-123456789abc'), true, '현재 예약의 유효한 추가 결제만 계속한다')
equal(canContinueChangePayment('AWAITING_PAYMENT', 'aaaaaaaa-aaaa-aaaa-aaaa-123456789abc', 'bbbbbbbb-bbbb-bbbb-bbbb-123456789abc'), false, '같은 suffix라도 다른 예약의 변경 결제를 표시하지 않는다')
equal(canContinueChangePayment('READY_TO_APPLY', '12345678-1234-1234-1234-123456789abc', '12345678-1234-1234-1234-123456789abc'), false, '서버가 결제 대기를 끝낸 뒤 결제를 다시 열지 않는다')
equal(formatReservationDateTime('2026-09-21T09:00:00.000Z', 'en', 'Asia/Seoul').includes('06:00'), true, '저장된 호텔 시간대로 취소 마감 시각을 표시한다')

console.log('reservation management state contract passed')
