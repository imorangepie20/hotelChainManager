export function nextHotelIndex(currentIndex: number, hotelCount: number, key: string) {
  if (hotelCount === 0) return -1
  if (key === 'Home') return 0
  if (key === 'End') return hotelCount - 1
  if (key === 'ArrowDown') return (currentIndex + 1 + hotelCount) % hotelCount
  if (key === 'ArrowUp') return (currentIndex - 1 + hotelCount) % hotelCount
  return currentIndex
}
