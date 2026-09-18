export type ChangeAction = 'PAY' | 'POLL' | 'RETURN'

export function changeAction(status: string): ChangeAction {
  if (status === 'AWAITING_PAYMENT') return 'PAY'
  if (['COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED'].includes(status)) return 'RETURN'
  return 'POLL'
}

// 결제 화면의 세션은 추가 결제 시도에만 발급된다. 0원·환불 변경은 예약 상세에서
// 서버 정산·적용 상태를 폴링해야 결제 링크가 없는 화면에 갇히지 않는다.
export function changeSettlementDestination(status: string): 'PAYMENT' | 'DETAILS' {
  return status === 'AWAITING_PAYMENT' ? 'PAYMENT' : 'DETAILS'
}
