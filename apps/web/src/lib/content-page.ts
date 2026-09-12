export type ContentPageSeo = { title: string; description: string }
export type ContentKind = 'ROOM' | 'DINING' | 'FACILITY' | 'EXPERIENCE' | 'PROMOTION' | 'GUIDE' | 'BRAND'
export type ContentPageConnections = { roomTypeIds: string[]; targetHotelIds: string[]; relatedPages: ContentPageRelation[] }
export type ContentPageRelation = { targetPageId: string; relationType: 'RELATED' | 'MANUAL_CARD'; displayOrder: number }
type WithBlockId = { blockId?: string; eyebrow?: string; description?: string; cta?: ContentPageCta }

export type ContentPageBlock =
  | (WithBlockId & { type: 'HERO'; eyebrow?: string; title: string; description?: string; imageAssetId: string; imageSrc: string; imageAlt: string; cta?: ContentPageCta })
  | (WithBlockId & { type: 'TEXT'; eyebrow?: string; title: string; paragraphs: string[] })
  | (WithBlockId & { type: 'CTA'; eyebrow?: string; title: string; description?: string; cta: ContentPageCta })
  | (WithBlockId & { type: 'IMAGE_GALLERY'; eyebrow?: string; title: string; description?: string; items: ContentPageGalleryItem[] })
  | (WithBlockId & { type: 'FEATURE_GRID'; eyebrow?: string; title: string; description?: string; items: ContentPageFeature[] })
  | (WithBlockId & { type: 'SPEC_TABLE'; eyebrow?: string; title: string; description?: string; rows: ContentPageSpecification[] })
  | (WithBlockId & { type: 'ACCORDION'; eyebrow?: string; title: string; description?: string; items: ContentPageAccordionItem[] })
  | (WithBlockId & { type: 'NOTICE_LIST'; eyebrow?: string; title: string; description?: string; items: ContentPageNotice[] })
  | (WithBlockId & { type: 'RICH_TEXT'; eyebrow: string; title: string; paragraphs: string[] })
  | (WithBlockId & { type: 'OPERATING_HOURS'; title: string; description?: string; entries: ContentPageOperatingHour[]; exceptions?: string; location?: string; phone?: string })
  | (WithBlockId & { type: 'LOCATION'; title: string; address: string; directions?: string; mapHref?: string })
  | (WithBlockId & { type: 'PROMOTION_SUMMARY'; title: string; salesPeriod: string; stayPeriod: string; benefits: string[]; displayPrice?: string; tags?: string[] })
  | (WithBlockId & { type: 'RELATED_COLLECTION'; title: string; kind: ContentKind; targetHotelId?: string; maxItems: number })
  | (WithBlockId & { type: 'BOOKING_CTA'; eyebrow?: string; title: string; description: string; label: string; hotelId: string; roomTypeId?: string })

export type ContentPageDocument = { seo: ContentPageSeo; blocks: ContentPageBlock[]; contentKind?: ContentKind; hotelId?: string | null; connections?: ContentPageConnections }
export type ContentPageCta = { label: string; href: string }
export type ContentPageGalleryItem = { imageAssetId: string; imageSrc: string; imageAlt: string; caption?: string }
export type ContentPageFeature = { title: string; description: string }
export type ContentPageSpecification = { label: string; value: string }
export type ContentPageAccordionItem = { title: string; content: string }
export type ContentPageNotice = { text: string; severity: 'DEFAULT' | 'IMPORTANT' }
export type ContentPageOperatingHour = { dayLabel: string; opensAt?: string; closesAt?: string; closed: boolean }

const localHref = /^(?:\/|\/[a-z0-9]+(?:-[a-z0-9]+)*(?:\/[a-z0-9]+(?:-[a-z0-9]+)*)*)(?:#[a-z0-9-]+)?$/
const localImage = /^\/images\/[A-Za-z0-9][A-Za-z0-9._/-]*$/
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const uploadedMediaDelivery = /^\/api\/website\/media\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/content$/i

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const text = (value: unknown, maximumLength = Number.MAX_SAFE_INTEGER): string | null =>
  typeof value === 'string' && value.trim().length > 0 && value.length <= maximumLength ? value.trim() : null

function optionalText(value: unknown, maximumLength = Number.MAX_SAFE_INTEGER): string | undefined {
  return text(value, maximumLength) ?? undefined
}

function hasOnlyFields(value: Record<string, unknown>, fields: readonly string[]): boolean {
  return Object.keys(value).every(field => fields.includes(field))
}

function commonBlock(value: Record<string, unknown>): { eyebrow?: string; title: string; description?: string } | null {
  const title = text(value.title, 160)
  if (!title) return null
  const eyebrow = optionalText(value.eyebrow, 100)
  const description = optionalText(value.description, 1000)
  return { ...(eyebrow ? { eyebrow } : {}), title, ...(description ? { description } : {}) }
}

function parseCta(value: unknown): ContentPageCta | undefined {
  if (!isRecord(value)) return undefined
  const label = text(value.label)
  const href = text(value.href)
  if (!label || !href || !localHref.test(href)) return undefined
  return { label, href }
}

function isLocalImage(value: string): boolean {
  return localImage.test(value) && value.slice('/images/'.length).split('/').every(segment => segment !== '' && segment !== '.' && segment !== '..')
}

export function safeMediaDeliveryPath(assetIdValue: unknown, deliveryPathValue: unknown): string | null {
  const assetId = text(assetIdValue)
  const deliveryPath = text(deliveryPathValue)
  if (!deliveryPath) return null
  if (isLocalImage(deliveryPath)) return !assetId || uuid.test(assetId) ? deliveryPath : null

  const uploadedMatch = deliveryPath.match(uploadedMediaDelivery)
  if (!uploadedMatch || (assetId && (!uuid.test(assetId) || uploadedMatch[1]?.toLowerCase() !== assetId.toLowerCase()))) return null
  return deliveryPath
}

const allowedByKind: Record<ContentKind, readonly ContentPageBlock['type'][]> = {
  ROOM: ['HERO', 'IMAGE_GALLERY', 'SPEC_TABLE', 'BOOKING_CTA', 'FEATURE_GRID', 'ACCORDION', 'NOTICE_LIST', 'RELATED_COLLECTION', 'RICH_TEXT'],
  DINING: ['HERO', 'IMAGE_GALLERY', 'OPERATING_HOURS', 'RICH_TEXT', 'LOCATION', 'NOTICE_LIST', 'ACCORDION'],
  FACILITY: ['HERO', 'IMAGE_GALLERY', 'SPEC_TABLE', 'OPERATING_HOURS', 'LOCATION', 'ACCORDION', 'NOTICE_LIST', 'RICH_TEXT'],
  EXPERIENCE: ['HERO', 'RICH_TEXT', 'IMAGE_GALLERY', 'SPEC_TABLE', 'NOTICE_LIST', 'BOOKING_CTA', 'RELATED_COLLECTION'],
  PROMOTION: ['HERO', 'PROMOTION_SUMMARY', 'BOOKING_CTA', 'IMAGE_GALLERY', 'FEATURE_GRID', 'NOTICE_LIST', 'RELATED_COLLECTION'],
  GUIDE: ['HERO', 'RICH_TEXT', 'SPEC_TABLE', 'LOCATION', 'ACCORDION', 'NOTICE_LIST'],
  BRAND: ['HERO', 'RICH_TEXT', 'TEXT', 'CTA', 'IMAGE_GALLERY', 'FEATURE_GRID', 'SPEC_TABLE', 'ACCORDION', 'NOTICE_LIST', 'RELATED_COLLECTION'],
}

const time = /^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/
const kind = (value: unknown): ContentKind | null => typeof value === 'string' && value in allowedByKind ? value as ContentKind : null
const blockFields = (typed: boolean, fields: string[]) => typed ? ['blockId', ...fields] : fields

function parseBlock(value: unknown, typed = false): ContentPageBlock | null {
  if (!isRecord(value)) return null
  const blockId = typed ? text(value.blockId, 36) : undefined
  if (typed && (!blockId || !uuid.test(blockId))) return null
  const id = blockId ? { blockId } : {}

  if (value.type === 'HERO') {
    if (!hasOnlyFields(value, blockFields(typed, ['type', 'imageAssetId', 'imageSrc', 'imageAlt', 'eyebrow', 'title', 'description', 'cta']))) return null
    const common = commonBlock(value); const imageAssetId = text(value.imageAssetId, 36); const imageSrc = safeMediaDeliveryPath(imageAssetId, value.imageSrc); const imageAlt = text(value.imageAlt, 200); const cta = parseCta(value.cta)
    if (!common || !imageAssetId || !imageSrc || !imageAlt || (value.cta !== undefined && !cta)) return null
    return { type: 'HERO', ...id, ...common, imageAssetId, imageSrc, imageAlt, ...(cta ? { cta } : {}) }
  }
  if (value.type === 'TEXT' || value.type === 'RICH_TEXT') {
    const rich = value.type === 'RICH_TEXT'
    if (rich && !typed) return null
    if (!hasOnlyFields(value, blockFields(typed, ['type', 'eyebrow', 'title', 'paragraphs']))) return null
    const title = text(value.title, 160); const eyebrow = optionalText(value.eyebrow, 100); const paragraphs = textList(value.paragraphs, 1, 6, 1000)
    if (!title || !paragraphs || value.description !== undefined || (rich && !eyebrow)) return null
    return rich ? { type: 'RICH_TEXT', ...id, eyebrow: eyebrow!, title, paragraphs } : { type: 'TEXT', ...id, ...(eyebrow ? { eyebrow } : {}), title, paragraphs }
  }
  if (value.type === 'CTA') {
    if (!hasOnlyFields(value, blockFields(typed, ['type', 'eyebrow', 'title', 'description', 'cta']))) return null
    const common = commonBlock(value); const cta = parseCta(value.cta)
    return common && cta ? { type: 'CTA', ...id, ...common, cta } : null
  }
  const common = commonBlock(value)
  if (value.type === 'IMAGE_GALLERY') {
    if (!common || !hasOnlyFields(value, blockFields(typed, ['type', 'eyebrow', 'title', 'description', 'items'])) || !Array.isArray(value.items) || value.items.length < 2 || value.items.length > 12) return null
    const items = value.items.map(item => {
      if (!isRecord(item) || !hasOnlyFields(item, ['imageAssetId', 'imageSrc', 'imageAlt', 'caption'])) return null
      const imageAssetId = text(item.imageAssetId, 36); const imageSrc = safeMediaDeliveryPath(imageAssetId, item.imageSrc); const imageAlt = text(item.imageAlt, 200); const caption = optionalText(item.caption, 300)
      return imageAssetId && imageSrc && imageAlt ? { imageAssetId, imageSrc, imageAlt, ...(caption ? { caption } : {}) } : null
    })
    return items.every(Boolean) ? { type: 'IMAGE_GALLERY', ...id, ...common, items: items as ContentPageGalleryItem[] } : null
  }
  if (value.type === 'FEATURE_GRID') return common ? parseTextItems(value, common, id, typed, 'FEATURE_GRID', 'items', 2, 6, ['title', 'description'], item => ({ title: text(item.title, 160), description: text(item.description, 500) })) : null
  if (value.type === 'SPEC_TABLE') return common ? parseTextItems(value, common, id, typed, 'SPEC_TABLE', 'rows', 1, 12, ['label', 'value'], item => ({ label: text(item.label, 100), value: text(item.value, 300) })) : null
  if (value.type === 'ACCORDION') return common ? parseTextItems(value, common, id, typed, 'ACCORDION', 'items', 1, 12, ['title', 'content'], item => ({ title: text(item.title, 160), content: text(item.content, 2000) })) : null
  if (value.type === 'NOTICE_LIST') {
    if (!common || !hasOnlyFields(value, blockFields(typed, ['type', 'eyebrow', 'title', 'description', 'items'])) || !Array.isArray(value.items) || value.items.length < 1 || value.items.length > 20) return null
    const items = value.items.map(item => isRecord(item) && hasOnlyFields(item, ['text', 'severity']) && (item.severity === 'DEFAULT' || item.severity === 'IMPORTANT') ? { text: text(item.text, 1000), severity: item.severity } : null)
    return items.every(item => item?.text) ? { type: 'NOTICE_LIST', ...id, ...common, items: items as ContentPageNotice[] } : null
  }
  if (!typed) return null
  if (value.type === 'OPERATING_HOURS') return parseOperatingHours(value, id)
  if (value.type === 'LOCATION') return parseLocation(value, id)
  if (value.type === 'PROMOTION_SUMMARY') return parsePromotionSummary(value, id)
  if (value.type === 'RELATED_COLLECTION') return parseRelatedCollection(value, id)
  if (value.type === 'BOOKING_CTA') return parseBookingCta(value, id)
  return null
}

function textList(value: unknown, minimum: number, maximum: number, maximumLength: number): string[] | null {
  if (!Array.isArray(value) || value.length < minimum || value.length > maximum) return null
  const values = value.map(item => text(item, maximumLength))
  return values.every(Boolean) ? values as string[] : null
}

function parseTextItems<T extends Record<string, string | null>>(value: Record<string, unknown>, common: { eyebrow?: string; title: string; description?: string }, id: WithBlockId, typed: boolean, type: 'FEATURE_GRID' | 'SPEC_TABLE' | 'ACCORDION', key: 'items' | 'rows', minimum: number, maximum: number, fields: string[], map: (item: Record<string, unknown>) => T): ContentPageBlock | null {
  if (!hasOnlyFields(value, blockFields(typed, ['type', 'eyebrow', 'title', 'description', key])) || !Array.isArray(value[key]) || value[key].length < minimum || value[key].length > maximum) return null
  const items = value[key].map(item => isRecord(item) && hasOnlyFields(item, fields) ? map(item) : null)
  if (items.some(item => !item || Object.values(item).some(field => !field))) return null
  if (type === 'FEATURE_GRID') return { type, ...id, ...common, items: items as unknown as ContentPageFeature[] }
  if (type === 'SPEC_TABLE') return { type, ...id, ...common, rows: items as unknown as ContentPageSpecification[] }
  return { type, ...id, ...common, items: items as unknown as ContentPageAccordionItem[] }
}

function parseOperatingHours(value: Record<string, unknown>, id: WithBlockId): ContentPageBlock | null {
  if (!hasOnlyFields(value, ['blockId', 'type', 'title', 'description', 'entries', 'exceptions', 'location', 'phone'])) return null
  const title = text(value.title, 160); const entries = value.entries
  if (!title || !Array.isArray(entries) || entries.length < 1 || entries.length > 7) return null
  const parsed = entries.map(entry => {
    if (!isRecord(entry) || !hasOnlyFields(entry, ['dayLabel', 'opensAt', 'closesAt', 'closed'])) return null
    const dayLabel = text(entry.dayLabel, 50)
    if (!dayLabel || typeof entry.closed !== 'boolean') return null
    if (entry.closed) return entry.opensAt === undefined && entry.closesAt === undefined ? { dayLabel, closed: true } : null
    const opensAt = text(entry.opensAt, 5); const closesAt = text(entry.closesAt, 5)
    return opensAt && closesAt && time.test(opensAt) && time.test(closesAt) ? { dayLabel, opensAt, closesAt, closed: false } : null
  })
  if (!parsed.every(Boolean)) return null
  const description = optionalText(value.description, 1000); const exceptions = optionalText(value.exceptions, 1000); const location = optionalText(value.location, 200); const phone = optionalText(value.phone, 40)
  return { type: 'OPERATING_HOURS', ...id, title, ...(description ? { description } : {}), entries: parsed as ContentPageOperatingHour[], ...(exceptions ? { exceptions } : {}), ...(location ? { location } : {}), ...(phone ? { phone } : {}) }
}

function parseLocation(value: Record<string, unknown>, id: WithBlockId): ContentPageBlock | null {
  if (!hasOnlyFields(value, ['blockId', 'type', 'title', 'address', 'directions', 'mapHref'])) return null
  const title = text(value.title, 160); const address = text(value.address, 300); const directions = optionalText(value.directions, 1000); const mapHref = optionalText(value.mapHref, 255)
  if (!title || !address || (value.mapHref !== undefined && (!mapHref || !localHref.test(mapHref)))) return null
  return { type: 'LOCATION', ...id, title, address, ...(directions ? { directions } : {}), ...(mapHref ? { mapHref } : {}) }
}

function parsePromotionSummary(value: Record<string, unknown>, id: WithBlockId): ContentPageBlock | null {
  if (!hasOnlyFields(value, ['blockId', 'type', 'title', 'salesPeriod', 'stayPeriod', 'benefits', 'displayPrice', 'tags'])) return null
  const title = text(value.title, 160); const salesPeriod = text(value.salesPeriod, 100); const stayPeriod = text(value.stayPeriod, 100); const benefits = textList(value.benefits, 1, 6, 200); const displayPrice = optionalText(value.displayPrice, 100); const tags = value.tags === undefined ? undefined : textList(value.tags, 1, 6, 50)
  if (!title || !salesPeriod || !stayPeriod || !benefits || (value.tags !== undefined && !tags)) return null
  return { type: 'PROMOTION_SUMMARY', ...id, title, salesPeriod, stayPeriod, benefits, ...(displayPrice ? { displayPrice } : {}), ...(tags ? { tags } : {}) }
}

function parseRelatedCollection(value: Record<string, unknown>, id: WithBlockId): ContentPageBlock | null {
  if (!hasOnlyFields(value, ['blockId', 'type', 'title', 'kind', 'targetHotelId', 'maxItems'])) return null
  const title = text(value.title, 160); const relatedKind = kind(value.kind); const targetHotelId = optionalText(value.targetHotelId, 36); const maxItems = value.maxItems
  if (!title || !relatedKind || (targetHotelId && !uuid.test(targetHotelId)) || typeof maxItems !== 'number' || !Number.isInteger(maxItems) || maxItems < 1 || maxItems > 12) return null
  return { type: 'RELATED_COLLECTION', ...id, title, kind: relatedKind, ...(targetHotelId ? { targetHotelId } : {}), maxItems }
}

function parseBookingCta(value: Record<string, unknown>, id: WithBlockId): ContentPageBlock | null {
  if (!hasOnlyFields(value, ['blockId', 'type', 'eyebrow', 'title', 'description', 'label', 'hotelId', 'roomTypeId'])) return null
  const title = text(value.title, 160); const description = text(value.description, 1000); const label = text(value.label, 100); const hotelId = text(value.hotelId, 36); const roomTypeId = optionalText(value.roomTypeId, 36); const eyebrow = optionalText(value.eyebrow, 100)
  if (!title || !description || !label || !hotelId || !uuid.test(hotelId) || (roomTypeId && !uuid.test(roomTypeId))) return null
  return { type: 'BOOKING_CTA', ...id, ...(eyebrow ? { eyebrow } : {}), title, description, label, hotelId, ...(roomTypeId ? { roomTypeId } : {}) }
}

function parseConnections(value: unknown): ContentPageConnections | null {
  if (!isRecord(value) || !hasOnlyFields(value, ['roomTypeIds', 'targetHotelIds', 'relatedPages'])) return null
  const ids = (items: unknown) => Array.isArray(items) && items.every(item => typeof item === 'string' && uuid.test(item)) && new Set(items).size === items.length ? items : null
  const roomTypeIds = ids(value.roomTypeIds); const targetHotelIds = ids(value.targetHotelIds)
  if (!roomTypeIds || !targetHotelIds || !Array.isArray(value.relatedPages)) return null
  const relatedPages = value.relatedPages.map(item => {
    if (!isRecord(item) || !hasOnlyFields(item, ['targetPageId', 'relationType', 'displayOrder'])) return null
    const displayOrder = item.displayOrder
    return typeof item.targetPageId === 'string' && uuid.test(item.targetPageId) && (item.relationType === 'RELATED' || item.relationType === 'MANUAL_CARD') && typeof displayOrder === 'number' && Number.isInteger(displayOrder) && displayOrder >= 0
      ? { targetPageId: item.targetPageId, relationType: item.relationType, displayOrder } as ContentPageRelation : null
  })
  return relatedPages.every(Boolean) ? { roomTypeIds, targetHotelIds, relatedPages: relatedPages as ContentPageRelation[] } : null
}

function typedDocument(value: Record<string, unknown>): ContentPageDocument | null {
  if (!hasOnlyFields(value, ['contentKind', 'hotelId', 'connections', 'seo', 'blocks'])) return null
  const contentKind = kind(value.contentKind); const hotelId = value.hotelId === null ? null : text(value.hotelId, 36); const connections = parseConnections(value.connections)
  if (!contentKind || (value.hotelId !== null && (!hotelId || !uuid.test(hotelId))) || !connections || !isRecord(value.seo) || !Array.isArray(value.blocks)) return null
  const title = text(value.seo.title, 60); const description = text(value.seo.description, 160)
  if (!hasOnlyFields(value.seo, ['title', 'description']) || !title || !description || value.blocks.length < 1 || value.blocks.length > 20) return null
  const blocks = value.blocks.map(block => parseBlock(block, true))
  if (blocks.some(block => block === null)) return null
  const validBlocks = blocks as ContentPageBlock[]
  if (new Set(validBlocks.map(block => block.blockId)).size !== validBlocks.length || validBlocks.some(block => !allowedByKind[contentKind].includes(block.type))) return null
  const types = new Set(validBlocks.map(block => block.type))
  if ((types.has('HERO') && validBlocks[0]?.type !== 'HERO') || validBlocks.filter(block => block.type === 'HERO').length > 1 || !requiredBlocks(contentKind, types)) return null
  const bookingCtas = validBlocks.filter((block): block is Extract<ContentPageBlock, { type: 'BOOKING_CTA' }> => block.type === 'BOOKING_CTA')
  if (bookingCtas.some(block => !validBookingCta(block, contentKind, hotelId, connections))) return null
  return { contentKind, hotelId, connections, seo: { title, description }, blocks: validBlocks }
}

function requiredBlocks(contentKind: ContentKind, types: Set<ContentPageBlock['type']>): boolean {
  const required = (...names: ContentPageBlock['type'][]) => names.every(name => types.has(name))
  if (contentKind === 'ROOM') return required('HERO', 'IMAGE_GALLERY', 'SPEC_TABLE', 'BOOKING_CTA')
  if (contentKind === 'DINING') return required('HERO', 'IMAGE_GALLERY', 'OPERATING_HOURS')
  if (contentKind === 'FACILITY') return required('HERO', 'IMAGE_GALLERY') && (types.has('SPEC_TABLE') || types.has('OPERATING_HOURS'))
  if (contentKind === 'EXPERIENCE') return required('HERO', 'RICH_TEXT')
  if (contentKind === 'PROMOTION') return required('HERO', 'PROMOTION_SUMMARY', 'BOOKING_CTA')
  return types.has('HERO') || types.has('RICH_TEXT')
}

function validBookingCta(block: Extract<ContentPageBlock, { type: 'BOOKING_CTA' }>, contentKind: ContentKind, hotelId: string | null | undefined, connections: ContentPageConnections): boolean {
  const hotelMatches = hotelId ? block.hotelId === hotelId : connections.targetHotelIds.includes(block.hotelId)
  if (!hotelMatches) return false
  if (block.roomTypeId && !connections.roomTypeIds.includes(block.roomTypeId)) return false
  return contentKind === 'ROOM' ? Boolean(block.roomTypeId) : true
}

function legacyDocument(value: Record<string, unknown>): ContentPageDocument | null {
  if (!isRecord(value.seo) || !Array.isArray(value.blocks)) return null
  const title = text(value.seo.title); const description = text(value.seo.description)
  if (!title || !description || value.blocks.length < 1 || value.blocks.length > 20) return null
  const blocks = value.blocks.map(block => parseBlock(block))
  if (blocks.some(block => block === null)) return null
  const validBlocks = blocks as ContentPageBlock[]
  return validBlocks[0]?.type === 'HERO' && validBlocks.filter(block => block.type === 'HERO').length === 1 ? { seo: { title, description }, blocks: validBlocks } : null
}

export function parseContentPage(value: unknown): ContentPageDocument | null {
  if (!isRecord(value)) return null
  if (isRecord(value.content)) {
    return typedDocument({ contentKind: value.contentKind, hotelId: value.hotelId, connections: value.connections, ...value.content })
  }
  return 'contentKind' in value ? typedDocument(value) : legacyDocument(value)
}
