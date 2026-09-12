type PointerPosition = { x: number; y: number }

export type GalleryTransitionDirection = 'forward' | 'backward'

export function galleryTransition(currentIndex: number, offset: -1 | 1, itemCount: number): { index: number; direction: GalleryTransitionDirection } {
  return {
    index: (currentIndex + offset + itemCount) % itemCount,
    direction: offset > 0 ? 'forward' : 'backward',
  }
}

export function gallerySwipeOffset(start: PointerPosition, end: PointerPosition): -1 | 0 | 1 {
  const horizontalDistance = end.x - start.x
  const verticalDistance = end.y - start.y
  if (Math.abs(horizontalDistance) < 48 || Math.abs(horizontalDistance) <= Math.abs(verticalDistance)) return 0
  return horizontalDistance < 0 ? 1 : -1
}
