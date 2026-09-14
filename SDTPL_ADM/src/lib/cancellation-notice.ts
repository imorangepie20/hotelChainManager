export function cancellationNotice(status: string, refund: string): string {
  return status === 'CANCELLED'
    ? `예약이 취소되었습니다. 환불 예정 금액은 ${refund}입니다.`
    : '취소 환불을 확인 중입니다. 모든 환불이 확인되면 예약 취소가 완료됩니다.';
}
