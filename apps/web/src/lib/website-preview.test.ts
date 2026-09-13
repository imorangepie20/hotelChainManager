import { captureWebsitePreview, storedWebsitePreviewForPath, clearWebsitePreview, WEBSITE_PREVIEW_STORAGE_KEY } from './website-preview.ts'

const TOKEN = 'P'.repeat(43)
const data = new Map<string, string>()
const storage = { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value) }, removeItem: (key: string) => { data.delete(key) } }
function equal(actual: unknown, expected: unknown, label: string) {
  assertions++
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(label)
}
let assertions = 0
let replaced = ''
equal(captureWebsitePreview({ pathname: '/brand/story', search: '?from=cms', hash: `#preview=${TOKEN}` }, storage, url => { replaced = url }), { token: TOKEN, path: '/brand/story' }, '정확한 fragment를 캡처한다')
equal(replaced, '/brand/story?from=cms', '토큰을 주소에서 제거한다')
equal(storedWebsitePreviewForPath('/brand/story', storage), { token: TOKEN, path: '/brand/story' }, '새로고침에서 복원한다')
equal(storedWebsitePreviewForPath('/brand/other', storage), null, '다른 경로는 토큰을 폐기한다')
equal(data.size, 0, '다른 경로에 토큰이 남지 않는다')
for (const hash of ['#booking', '#preview=bad', `#preview=${TOKEN}&extra=1`, `#preview=${TOKEN}=`, `#preview=${TOKEN}#extra`]) {
  replaced = ''
  equal(captureWebsitePreview({ pathname: '/', search: '', hash }, storage, url => { replaced = url }), null, '형식이 다른 fragment는 수용하지 않는다')
  equal(data.size, 0, '형식이 다른 token은 저장하지 않는다')
}
for (const value of ['bad-json', '{}', '{"token":"bad","path":"/"}', JSON.stringify({ token: TOKEN, path: '//external.test' })]) {
  data.set(WEBSITE_PREVIEW_STORAGE_KEY, value)
  equal(storedWebsitePreviewForPath('/', storage), null, '잘못된 저장값은 복원하지 않는다')
  equal(data.size, 0, '잘못된 저장값을 제거한다')
}
captureWebsitePreview({ pathname: '/en/brand/story/', search: '', hash: `#preview=${TOKEN}` }, storage, () => {})
equal(storedWebsitePreviewForPath('/en/brand/story', storage), { token: TOKEN, path: '/en/brand/story' }, '정규화된 경로에만 결합한다')
clearWebsitePreview(storage)
equal(data.size, 0, '미리보기 종료 시 제거한다')
console.log(`website-preview: ${assertions} assertions passed`)
