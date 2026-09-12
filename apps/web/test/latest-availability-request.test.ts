import { LatestAvailabilityRequest } from '../src/lib/latest-availability-request.js'

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message)
}

const firstCriteria = 'sokcho|2026-09-18|2026-09-20|2|0|1|false'
const nextCriteria = 'jeju|2026-09-25|2026-09-27|2|1|1|true'
const requests = new LatestAvailabilityRequest(firstCriteria)
const first = requests.start(firstCriteria)

assert(requests.criteriaChanged(nextCriteria), 'Changing booking conditions must invalidate an older request.')
assert(!requests.isCurrent(first), 'An older availability response must not update the UI.')

const second = requests.start(nextCriteria)
assert(!requests.criteriaChanged(nextCriteria), 'Applying matching AI conditions must keep its new request current.')
assert(requests.isCurrent(second), 'The latest availability response must remain eligible to update the UI.')
assert(requests.complete(second), 'The latest request must be allowed to clear loading state.')