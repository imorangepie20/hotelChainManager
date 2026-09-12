import { safeMediaDeliveryPath, type ContentKind } from './content-page.ts'

export type ContentCollectionItem = {
  id: string
  contentKind: ContentKind
  path: string
  title: string
  summary: string
  image: string
  hotelSlug?: string
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const localPath = /^(?:\/[a-z0-9]+(?:-[a-z0-9]+)*)+$/
const slug = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const kinds = new Set<ContentKind>(['ROOM', 'DINING', 'FACILITY', 'EXPERIENCE', 'PROMOTION', 'GUIDE', 'BRAND'])

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const text = (value: unknown, maximumLength: number): string | null =>
  typeof value === 'string' && value.trim().length > 0 && value.length <= maximumLength ? value.trim() : null

export function parseContentCollection(value: unknown): ContentCollectionItem[] | null {
  if (!Array.isArray(value)) return null
  const cards = value.map(item => parseCard(item))
  return cards.every(Boolean) ? cards as ContentCollectionItem[] : null
}

function parseCard(value: unknown): ContentCollectionItem | null {
  if (!isRecord(value) || !['id', 'contentKind', 'path', 'title', 'summary', 'image', 'hotelSlug'].every(key => key in value) || Object.keys(value).some(key => !['id', 'contentKind', 'path', 'title', 'summary', 'image', 'hotelSlug'].includes(key))) return null
  const id = text(value.id, 36); const contentKind = value.contentKind; const path = text(value.path, 255); const title = text(value.title, 160); const summary = text(value.summary, 1000); const image = text(value.image, 255)
  const hotelSlug = value.hotelSlug === null ? undefined : text(value.hotelSlug, 80)
  if (!id || !uuid.test(id) || typeof contentKind !== 'string' || !kinds.has(contentKind as ContentKind) || !path || !localPath.test(path) || !title || !summary || !image || !safeMediaDeliveryPath(undefined, image) || (value.hotelSlug !== null && (!hotelSlug || !slug.test(hotelSlug)))) return null
  return { id, contentKind: contentKind as ContentKind, path, title, summary, image, ...(hotelSlug ? { hotelSlug } : {}) }
}
