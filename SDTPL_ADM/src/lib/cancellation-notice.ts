export function cancellationNotice(status: string, refund: string): string {
  if (status === 'CANCELLATION_FAILED') return '일부 환불이 실패했습니다. 예약 취소를 다시 요청하면 기존 환불 계획으로 재시도합니다.';
  return status === 'CANCELLED'
    ? `예약이 취소되었습니다. 환불 예정 금액은 ${refund}입니다.`
    : '취소 환불을 확인 중입니다. 모든 환불이 확인되면 예약 취소가 완료됩니다.';
}
