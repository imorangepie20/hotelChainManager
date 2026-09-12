import { normalizeCustomerPathname } from './customer-route.ts'

export type WebsitePreviewSession = { token: string; path: string }
export const WEBSITE_PREVIEW_STORAGE_KEY = 'website-preview'
type PreviewStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
const PREVIEW_FRAGMENT = /^#preview=([A-Za-z0-9_-]{43})$/
const TOKEN = /^[A-Za-z0-9_-]{43}$/

export function clearWebsitePreview(storage: Pick<Storage, 'removeItem'>): void {
  try { storage.removeItem(WEBSITE_PREVIEW_STORAGE_KEY) } catch { /* 저장소 접근이 차단돼도 종료를 계속한다. */ }
}

export function storedWebsitePreviewForPath(pathname: string, storage: Pick<Storage, 'getItem' | 'removeItem'>): WebsitePreviewSession | null {
  try {
    const raw = storage.getItem(WEBSITE_PREVIEW_STORAGE_KEY)
    if (!raw) return null
    const value: unknown = JSON.parse(raw)
    if (value && typeof value === 'object' && 'token' in value && 'path' in value
      && typeof value.token === 'string' && TOKEN.test(value.token)
      && typeof value.path === 'string' && normalizeCustomerPathname(value.path) === value.path
      && value.path === normalizeCustomerPathname(pathname)) return { token: value.token, path: value.path }
  } catch { /* 저장소 접근 또는 JSON 오류는 미리보기 복원을 거절한다. */ }
  clearWebsitePreview(storage)
  return null
}

export function captureWebsitePreview(location: Pick<Location, 'pathname' | 'search' | 'hash'>, storage: PreviewStorage, replaceUrl: (url: string) => void): WebsitePreviewSession | null {
  const match = PREVIEW_FRAGMENT.exec(location.hash)
  if (!match) return null
  // 저장소 접근 전에 주소에서 토큰을 제거한다.
  replaceUrl(location.pathname + location.search)
  const path = normalizeCustomerPathname(location.pathname)
  if (!path) { clearWebsitePreview(storage); return null }
  const session = { token: match[1], path }
  try { storage.setItem(WEBSITE_PREVIEW_STORAGE_KEY, JSON.stringify(session)) } catch { /* 현재 탭 메모리에서는 검토를 계속한다. */ }
  return session
}
