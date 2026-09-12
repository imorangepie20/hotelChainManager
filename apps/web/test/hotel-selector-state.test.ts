import { nextHotelIndex } from '../src/lib/hotel-selector-state.ts'

function assertEqual(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) throw new Error(`${message}: expected ${expected}, received ${actual}`)
}

assertEqual(nextHotelIndex(0, 3, 'ArrowDown'), 1, '아래 방향키는 다음 지점으로 이동한다')
assertEqual(nextHotelIndex(2, 3, 'ArrowDown'), 0, '마지막 지점에서 아래 방향키는 첫 지점으로 순환한다')
assertEqual(nextHotelIndex(0, 3, 'ArrowUp'), 2, '첫 지점에서 위 방향키는 마지막 지점으로 순환한다')
assertEqual(nextHotelIndex(1, 3, 'Home'), 0, 'Home은 첫 지점을 가리킨다')
assertEqual(nextHotelIndex(1, 3, 'End'), 2, 'End는 마지막 지점을 가리킨다')
assertEqual(nextHotelIndex(0, 0, 'ArrowDown'), -1, '지점이 없으면 활성 항목이 없다')
