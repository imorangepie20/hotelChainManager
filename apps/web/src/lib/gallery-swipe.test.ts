import { gallerySwipeOffset, galleryTransition } from './gallery-swipe.ts'

function expectEqual(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) throw new Error(`${message}\nexpected: ${expected}\nreceived: ${actual}`)
}

const cases = [
  { name: '왼쪽 스와이프는 다음 이미지를 선택한다', start: { x: 240, y: 120 }, end: { x: 160, y: 124 }, expected: 1 },
  { name: '오른쪽 스와이프는 이전 이미지를 선택한다', start: { x: 120, y: 120 }, end: { x: 200, y: 116 }, expected: -1 },
  { name: '짧은 이동은 이미지를 바꾸지 않는다', start: { x: 200, y: 120 }, end: { x: 168, y: 121 }, expected: 0 },
  { name: '세로 중심 이동은 이미지를 바꾸지 않는다', start: { x: 200, y: 100 }, end: { x: 140, y: 210 }, expected: 0 },
]

for (const testCase of cases) {
  expectEqual(gallerySwipeOffset(testCase.start, testCase.end), testCase.expected, testCase.name)
}

const transitions: Array<{ name: string; current: number; offset: -1 | 1; count: number; expected: { index: number; direction: 'forward' | 'backward' } }> = [
  { name: '다음 이미지는 앞으로 전환한다', current: 0, offset: 1, count: 3, expected: { index: 1, direction: 'forward' } },
  { name: '마지막 다음 이미지는 첫 이미지로 순환한다', current: 2, offset: 1, count: 3, expected: { index: 0, direction: 'forward' } },
  { name: '첫 이전 이미지는 마지막 이미지로 순환한다', current: 0, offset: -1, count: 3, expected: { index: 2, direction: 'backward' } },
]

for (const testCase of transitions) {
  const actual = galleryTransition(testCase.current, testCase.offset, testCase.count)
  expectEqual(actual.index, testCase.expected.index, `${testCase.name}: 이미지 인덱스`)
  expectEqual(actual.direction, testCase.expected.direction, `${testCase.name}: 모션 방향`)
}
