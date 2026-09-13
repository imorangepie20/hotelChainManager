export type FragmentLocation = Pick<Location, 'hash' | 'pathname'>

const tokenPattern = /^[A-Za-z0-9_-]{43}$/

export function captureReservationChangePaymentToken(
  location: FragmentLocation,
  replacePath: (path: string) => void,
): string | null {
  const fragment = location.hash.startsWith('#') ? location.hash.slice(1) : location.hash
  replacePath(location.pathname)
  return tokenPattern.test(fragment) ? fragment : null
}
