import { api, ApiFailure } from './api.ts'

let calls = 0
globalThis.fetch = async (url, init) => {
  calls++
  if (String(url) !== '/api/website/pages/preview?path=%2Fbrand%2Fstory&locale=ko') throw new Error('토큰은 URL에 포함하지 않는다')
  if (init?.cache !== 'no-store' || init.credentials !== 'omit') throw new Error('캐시·예약 자격증명을 사용하지 않는다')
  if ((init.headers as Record<string, string>)['X-Website-Preview'] !== 'P'.repeat(43)) throw new Error('preview header만 사용한다')
  return new Response(JSON.stringify({ id: 'draft' }), { headers: { 'X-Website-Preview-Expires-At': '2026-09-13T01:10:00Z' } })
}
const result = await api.websitePreviewPage('/brand/story', 'ko', 'P'.repeat(43))
if (result.page.id !== 'draft' || result.expiresAt !== '2026-09-13T01:10:00Z' || calls !== 1) throw new Error('초안과 만료를 반환한다')
globalThis.fetch = async () => new Response('{}')
try { await api.websitePreviewPage('/brand/story', 'ko', 'P'.repeat(43)); throw new Error('만료 header 누락을 거절해야 한다') }
catch (error) { if (!(error instanceof ApiFailure) || error.code !== 'WEBSITE_PREVIEW_UNAVAILABLE') throw error }
globalThis.fetch = async () => new Response(JSON.stringify({ code: 'WEBSITE_PREVIEW_UNAVAILABLE', message: '만료' }), { status: 410 })
try { await api.websitePreviewPage('/brand/story', 'ko', 'P'.repeat(43)); throw new Error('410을 보존해야 한다') }
catch (error) { if (!(error instanceof ApiFailure) || error.status !== 410) throw error }
Object.defineProperty(globalThis, 'window', { configurable: true, value: { location: { protocol: 'http:', hostname: 'preview.example.test' } } })
let insecureCalls = 0
globalThis.fetch = async () => { insecureCalls++; return new Response('{}') }
try { await api.websitePreviewPage('/brand/story', 'ko', 'P'.repeat(43)); throw new Error('운영 HTTP로 토큰을 보내면 안 된다') }
catch (error) { if (!(error instanceof ApiFailure) || error.code !== 'WEBSITE_PREVIEW_HTTPS_REQUIRED') throw error }
if (insecureCalls) throw new Error('비보안 요청 전에 차단한다')
console.log('website-preview-api: request, failure and transport contracts passed')
