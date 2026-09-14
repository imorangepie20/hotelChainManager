// @ts-nocheck -- Node's direct TypeScript runner requires the source extension.
import { describeReservationChangeLink, shouldPollReservationChangeRequest } from './reservation-change-link-state.ts'

function expectEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, received ${String(actual)}`)
}

const expired = describeReservationChangeLink('EXPIRED', true, 'READY')
expectEqual(expired.keepVisible, true, '만료된 링크는 응답 poll 뒤에도 보존한다')
expectEqual(expired.state, 'EXPIRED', '만료 상태를 우선 표시한다')
expectEqual(expired.canStartNewRequest, true, '만료 뒤 새 링크용 요청을 시작할 수 있다')
expectEqual(shouldPollReservationChangeRequest('EXPIRED'), false, '만료 요청은 poll을 멈춘다')
expectEqual(shouldPollReservationChangeRequest('AWAITING_PAYMENT'), true, '결제 대기 요청은 poll한다')

const copied = describeReservationChangeLink('AWAITING_PAYMENT', true, 'COPIED')
expectEqual(copied.keepVisible, true, '활성 링크는 표시한다')
expectEqual(copied.state, 'COPIED', '복사 성공 상태를 유지한다')
