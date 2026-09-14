import assert from 'node:assert/strict';
import { cancellationNotice } from './cancellation-notice.ts';

assert.match(cancellationNotice('CANCELLED', '100,000원'), /예약이 취소되었습니다/);
for (const status of ['CANCELLATION_PENDING', 'UNKNOWN', 'CONFIRMED']) {
  const notice = cancellationNotice(status, '100,000원');
  assert.doesNotMatch(notice, /취소되었습니다/);
  assert.match(notice, /확인 중/);
}
console.log('취소 완료·대기 상태 안내 4건 통과');
