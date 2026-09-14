import { changeAction } from './reservation-change-state'

function equal(actual: unknown, expected: unknown) { if (actual !== expected) throw new Error(`${actual} !== ${expected}`) }
equal(changeAction('AWAITING_PAYMENT'), 'PAY')
equal(changeAction('REFUND_PENDING'), 'POLL')
equal(changeAction('READY_TO_APPLY'), 'POLL')
equal(changeAction('COMPLETED'), 'RETURN')
